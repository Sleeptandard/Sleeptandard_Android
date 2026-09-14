# Potch Raw Data Resumable Upload Contract v1

주요 구현:
- 서버 개발자용 명세: [potch-raw-upload-api.md (line 1)]
  - 150바이트 레코드 및 내부 142바이트 BLE 패킷 구조
  - FastAPI 엔드포인트와 요청·응답 JSON
  - S3 Multipart Upload/Presigned URL 흐름
  - CRC32C, ETag, 재개·완료·오류 처리
  - PostgreSQL 업로드 테이블 제안
- 파일 규격 및 체크섬: [PotchRawFileContract.kt (line 16)]
- WorkManager 예약: [RawDataUploadManager.kt (line 24)]
  - UNMETERED 네트워크
  - 배터리 부족 상태 제외
  - 닫힌 .bin 파일만 업로드
- 멀티파트 업로더: [RawDataUploadWorker.kt (line 27)]
  - 기본 8MiB 청크
  - S3 ListParts 기준 재개
  - 파트별 CRC32C와 ETag 수집
  - 프로세스 재시작 후 업로드 상태 복구
  - 완료 후 .s3-uploaded 마커 생성
  - 원본 파일은 자동 삭제하지 않음
- 로그 종료 시 자동 예약 연결: [PotchDataLogger.kt (line 472)]
서버가 없어 남겨둔 TODO:
- SLEEP_SERVER_BASE_URL 설정
- FastAPI 인증 토큰 연결: [SleepServerAuthProvider.kt (line 6)]
- FastAPI sleep_sessions 생성 API
- Multipart 생성·상태 조회·Presign·Complete·Abort 엔드포인트
- 펌웨어의 device_timestamp 단위 확인

Status: Draft - FastAPI/S3 server not deployed yet

This document is the contract between the Android app and the FastAPI server. The Android
implementation lives in `backend/RawDataUploadManager.kt` and `backend/RawDataUploadWorker.kt`.

## 1. Architecture

```text
Android app -- JSON control API --> FastAPI -- metadata --> PostgreSQL
Android app -- presigned PUT ----> Amazon S3
```

- FastAPI owns AWS credentials. AWS access keys must never be returned to the app.
- The app uploads immutable raw files directly to S3 with Multipart Upload presigned URLs.
- PostgreSQL stores the app-facing upload ID, the private S3 upload ID, object key, file metadata,
  upload state, and ownership.
- `sleep_sessions.session_status` and `sleep_sessions.upload_status` are independent.

## 2. Uploaded object

| Field | Value |
| --- | --- |
| Format identifier | `potch-raw-v1` |
| Content-Type | `application/octet-stream` |
| File extension | `.bin` |
| Compression | none |
| File header/footer | none |
| Record size | exactly 150 bytes |
| Default multipart part size | 8 MiB (`8388608` bytes) |
| Suggested object key | `users/{user_id}/sleep-sessions/{session_id}/sensor.raw.v1.bin` |

The final file size must be greater than zero and divisible by 150. A file is immutable after the
logger closes it. `record_count = size_bytes / 150`.

Example filename:

```text
potch_packet_raw_data_20260911_183104_123_550e8400-e29b-41d4-a716-446655440000.bin
```

The final UUID in the filename is the client-generated `sleep_session_id`.

### 2.1 Outer record layout (150 bytes)

| Offset | Size | Type | Meaning |
| ---: | ---: | --- | --- |
| 0 | 8 | unsigned 64-bit little-endian | Android receive time, Unix epoch milliseconds |
| 8 | 142 | bytes | Unmodified Potch510 BLE notification packet |

Records are saved before packet header and CRC validation. The file can therefore contain corrupt
packets intentionally; the server parser must validate every embedded packet.

### 2.2 Embedded Potch510 packet layout (142 bytes, relative to outer offset 8)

| Offset | Size | Type | Meaning |
| ---: | ---: | --- | --- |
| 0 | 2 | bytes | Header, exactly `A5 5A` |
| 2 | 2 | uint16 little-endian | Packet sequence, wraps after 65535 |
| 4 | 4 | uint32 little-endian | Device timestamp |
| 8 | 2 | uint16 little-endian | Battery ADC raw value |
| 10 | 2 | uint16 little-endian | NTC ADC raw value |
| 12 | 96 | 8 IMU samples | Each sample is 6 x int16 LE: ACC X/Y/Z, GYRO X/Y/Z |
| 108 | 32 | 16 x uint16 little-endian | Green PPG samples |
| 140 | 2 | uint16 little-endian | CRC-16/CCITT-FALSE over bytes 0..139 |

CRC parameters: polynomial `0x1021`, initial value `0xFFFF`, no reflection, final XOR `0x0000`.

Current nominal sample rates after grouping 8 BLE packets are Green PPG 128 Hz and IMU 64 Hz.

TODO(Firmware/Server): Confirm the device timestamp unit and wraparound behavior with the firmware
owner before interpreting offset 4 as elapsed time.

## 3. Authentication and ownership

Every FastAPI request must eventually use:

```http
Authorization: Bearer <server-issued-access-token>
```

The server derives `user_id` from the token and must not accept an arbitrary user ID from the app.
It must verify that the authenticated user owns both the sleep session and upload.

TODO(App/Auth): `SleepServerAuthProvider.bearerToken()` currently returns null because the new
server login/token contract does not exist.

TODO(Server/Auth): Define login, access-token refresh, token lifetime, and the HTTP 401 response.

## 4. Required FastAPI API

All JSON uses UTF-8 and `Content-Type: application/json`.

### 4.1 Initiate or recover an upload

```http
POST /v1/sleep-sessions/{session_id}/raw-uploads
Idempotency-Key: {session_id}:raw:{sha256}
```

Request:

```json
{
  "formatVersion": "potch-raw-v1",
  "fileName": "potch_packet_raw_data_20260911_183104_123_550e8400-e29b-41d4-a716-446655440000.bin",
  "contentType": "application/octet-stream",
  "sizeBytes": 31457250,
  "recordSizeBytes": 150,
  "recordCount": 209715,
  "sha256": "lowercase-hex-sha256-of-the-entire-file"
}
```

Response `201 Created` for a new upload or `200 OK` for the same idempotency key:

```json
{
  "uploadId": "app-facing-uuid",
  "objectKey": "users/USER_ID/sleep-sessions/SESSION_ID/sensor.raw.v1.bin",
  "partSizeBytes": 8388608,
  "status": "UPLOADING"
}
```

Server behavior:

1. Validate format, size, record size/count, session ownership, and file name/session UUID.
2. Return the existing upload for the same `(session_id, format_version, sha256)`.
3. Call S3 `CreateMultipartUpload` with `ChecksumAlgorithm=CRC32C` and composite checksum mode.
4. Store the private S3 `UploadId`; return a separate UUID as the public `uploadId`.
5. Set `sleep_sessions.upload_status = 'UPLOADING'`.

TODO(Server/Session): The app does not yet create `sleep_sessions` on FastAPI. Define that API
before enabling upload, or make this endpoint create the session atomically from agreed metadata.

### 4.2 Query resumable state

```http
GET /v1/sleep-sessions/{session_id}/raw-uploads/{upload_id}
```

FastAPI calls S3 `ListParts` using the private S3 upload ID stored for this upload.

Response `200 OK` (normative form):

```json
{
  "status": "UPLOADING",
  "objectKey": "users/USER_ID/sleep-sessions/SESSION_ID/sensor.raw.v1.bin",
  "uploadedParts": [
    {
      "partNumber": 1,
      "etag": "\"s3-etag-value\"",
      "checksumCrc32c": "base64-crc32c",
      "sizeBytes": 8388608
    }
  ]
}
```

The authoritative resume state is S3 `ListParts`, not the device's local state. When the database
already records completion, return `status: "COMPLETE"` and the final object key.

### 4.3 Presign one missing part

```http
POST /v1/sleep-sessions/{session_id}/raw-uploads/{upload_id}/parts/presign
```

Request:

```json
{
  "partNumber": 2,
  "sizeBytes": 8388608,
  "checksumCrc32c": "base64-crc32c"
}
```

Response `200 OK`:

```json
{
  "partNumber": 2,
  "url": "https://BUCKET.s3.REGION.amazonaws.com/...?partNumber=2&uploadId=...",
  "expiresAt": "2026-09-11T20:00:00Z",
  "headers": {
    "x-amz-checksum-crc32c": "base64-crc32c"
  }
}
```

Server requirements:

- Validate `partNumber` is in `1..10000` and `sizeBytes` matches the expected range.
- All non-final parts must be at least 5 MiB; the final part can be smaller.
- Generate an S3 `UploadPart` presigned URL for the stored bucket/key/S3 upload ID/part number.
- Sign the exact `x-amz-checksum-crc32c` value supplied by the app.
- Use a short URL lifetime, recommended 15 minutes. A new URL can be issued for the same part.
- Never allow the app to choose the bucket or object key.

### 4.4 Upload a part directly to S3

The app performs:

```http
PUT <presigned URL>
Content-Type: application/octet-stream
x-amz-checksum-crc32c: <same value used for presigning>
Content-Length: <part size>

<binary file slice>
```

On HTTP 2xx, the app stores the response `ETag`. On process death it can discard local ETags,
because the next status request rebuilds the authoritative part list from S3.

### 4.5 Complete the multipart upload

```http
POST /v1/sleep-sessions/{session_id}/raw-uploads/{upload_id}/complete
```

Request:

```json
{
  "parts": [
    {
      "partNumber": 1,
      "etag": "\"s3-etag-value\"",
      "checksumCrc32c": "base64-crc32c"
    }
  ],
  "sizeBytes": 31457250,
  "sha256": "lowercase-hex-sha256-of-the-entire-file"
}
```

Server behavior:

1. Require consecutive part numbers starting at 1, sorted before calling S3.
2. Compare the submitted list with S3 `ListParts`.
3. Call `CompleteMultipartUpload` with `PartNumber`, `ETag`, and `ChecksumCRC32C` for every part.
4. Call `HeadObject` and verify object size and stored checksum metadata.
5. In one database transaction, set `sensor_file_key` and `upload_status = 'COMPLETE'`.
6. Return the same successful result if this endpoint is retried after a lost response.

Response `200 OK`:

```json
{
  "status": "COMPLETE",
  "objectKey": "users/USER_ID/sleep-sessions/SESSION_ID/sensor.raw.v1.bin",
  "sizeBytes": 31457250
}
```

The app creates a local `.s3-uploaded` marker only after this response. It does not delete the raw
file automatically.

### 4.6 Abort

```http
DELETE /v1/sleep-sessions/{session_id}/raw-uploads/{upload_id}
```

FastAPI calls `AbortMultipartUpload`, changes the upload state to `ABORTED`, and returns `204`.
Configure the S3 bucket's `AbortIncompleteMultipartUpload` lifecycle rule for seven days as a final
safety net.

TODO(App): A user-facing cancel/delete flow does not exist yet, so the Android worker does not call
this endpoint.

## 5. Error contract

Error body:

```json
{
  "code": "UPLOAD_NOT_FOUND",
  "message": "Human-readable diagnostic message",
  "retryable": false
}
```

| HTTP | Meaning | App behavior |
| ---: | --- | --- |
| 400 | Invalid format, size, part, or checksum | Permanent failure |
| 401 | Missing/expired app login | Refresh/login; do not upload anonymously |
| 403 | Ownership failure | Permanent failure |
| 404 | Upload ID absent/expired | Initiate a new multipart upload |
| 409 | File/session conflict or already completing | Query status and reconcile |
| 422 | JSON validation failure | Permanent failure |
| 429 | Rate limited | Retry with exponential backoff |
| 500-599 | Temporary server/S3 failure | Retry with exponential backoff |

An expired S3 presigned URL returns 403 from S3. The app requests a new presigned URL without
changing the public upload ID or re-uploading completed parts.

## 6. Suggested PostgreSQL upload table

`sleep_sessions.upload_status` alone cannot retain enough information to resume S3 multipart
uploads. Add a dedicated table such as:

```text
sleep_session_uploads
- id UUID PK                         # public app-facing uploadId
- session_id UUID FK
- kind VARCHAR                      # RAW_SENSOR
- format_version VARCHAR            # potch-raw-v1
- object_key TEXT
- s3_upload_id TEXT                 # private, never returned to app
- file_name TEXT
- size_bytes BIGINT
- record_count BIGINT
- sha256 CHAR(64)
- part_size_bytes INTEGER
- status VARCHAR                    # UPLOADING, COMPLETE, ABORTED, FAILED
- created_at TIMESTAMPTZ
- updated_at TIMESTAMPTZ

UNIQUE(session_id, kind, format_version, sha256)
```

## 7. Android activation checklist

The code compiles while the server is absent, but upload scheduling remains disabled.

1. Deploy the FastAPI endpoints in this document.
2. Add `SLEEP_SERVER_BASE_URL=https://api.example.com` to the Android developer's
   `local.properties`.
3. Implement `SleepServerAuthProvider.bearerToken()`.
4. Implement or agree on FastAPI sleep-session creation.
5. Test process death after each uploaded part and confirm `GET .../raw-uploads/{upload_id}` resumes
   only missing parts.
6. Confirm production S3 lifecycle cleanup, encryption, access logging, and least-privilege IAM.

References:

- https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpuoverview.html
- https://docs.aws.amazon.com/AmazonS3/latest/userguide/qfacts.html
- https://docs.aws.amazon.com/AmazonS3/latest/userguide/checking-object-integrity-upload.html
- https://docs.aws.amazon.com/AmazonS3/latest/userguide/mpu-abort-incomplete-mpu-lifecycle-config.html
