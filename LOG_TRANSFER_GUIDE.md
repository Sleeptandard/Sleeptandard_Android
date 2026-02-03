# 로그 파일 전송 기능 가이드

## 📋 개요

워치(Wear OS)에서 수집된 수면 로그 파일을 모바일로 **자동 전송**하고, 모바일에서 이메일/카카오톡 등으로 공유하는 기능입니다.

---

## 🔄 전송 플로우

```
[Wear OS] 
  ↓ 로그 기록 (sensor_log_*.csv, inference_log_*.csv)
  ↓ 
[Wear OS] 알람 종료 → 자동으로 가장 최근 2개 파일 전송 ⭐️ (NEW!)
  ↓ ChannelClient (대용량 파일 전송)
  ↓
[Mobile] PhoneListenerService가 수신 & 저장
  ↓ 알림 표시
  ↓
[Mobile] 설정 > 수면데이터 보내기 화면
  ↓ 파일 목록 확인
  ↓
[Mobile] 공유 버튼 → Intent Chooser
  ↓
[카카오톡/이메일/구글드라이브 등으로 전송]
```

---

## 📱 사용 방법

### 1️⃣ Wear OS (워치)

#### ⭐️ 자동 전송 (추천)

**알람을 끄면 자동으로 전송됩니다!**

1. 알람 설정 및 취침
2. 아침에 알람이 울림
3. 알람 종료 → **자동으로 로그 파일 전송** 🚀
4. 모바일에서 알림 확인

**장점:**
- 사용자 개입 불필요
- 워치 화면 볼 필요 없음
- 전송 실패 시에도 서비스는 정상 종료

#### 수동 전송 (재전송용)

자동 전송이 실패했거나 이전 로그를 다시 보내고 싶을 때:

1. 워치 메인 화면에서 **파란색 업로드 버튼**(↑) 클릭
2. "로그 재전송 중..." 토스트 메시지 확인
3. 전송 완료 시: "✅ 2개 파일 재전송 완료" 메시지
4. 전송 실패 시: "❌ 재전송 실패: [에러 메시지]"

#### 로그 상태 확인하기

1. 워치 메인 화면에서 **회색 정보 버튼**(ⓘ) 클릭
2. 저장된 로그 파일 개수와 크기 확인
   - 예: "📊 6개 파일 (12.3 MB)"
   - 파일 없음: "로그 파일 없음 (알람 종료 시 자동 전송됨)"

#### 주의사항

- 전송 완료된 파일은 **자동 삭제**됩니다 (용량 관리)
- 자동 전송은 알람 종료 시 실행됨 (비동기, 백그라운드)
- 연결된 모바일이 없으면 전송 실패 (서비스는 정상 종료)
- 자동 전송 실패 시 로그에만 기록 (사용자 알림 없음)
- 수동 전송 버튼은 재전송용으로 사용

---

### 2️⃣ Mobile (모바일)

#### 파일 수신 확인

1. 워치에서 전송하면 **알림**이 표시됩니다
   - "로그 파일 수신 완료"
   - 파일명 및 크기 표시
2. 토스트 메시지: "로그 파일 수신 완료 ✅"

#### 파일 공유하기

1. 앱 실행 → **설정** 탭
2. **"수면데이터 보내기"** 메뉴 클릭
3. 수신된 파일 목록 확인
   - 파일명, 수신 시간, 크기
   - 전체 파일 개수 및 총 크기
4. 하단 **공유하기 버튼** (↑ 아이콘) 클릭
5. 공유 방법 선택
   - 이메일
   - 카카오톡
   - 구글 드라이브
   - 기타 앱

---

## 🛠️ 기술 구조

### Wear OS 모듈

#### 1. LogFileTransferManager.kt
```kotlin
// 위치: wear/backend/manager/LogFileTransferManager.kt

// 주요 기능:
- sendLatestLogsToPhone(): 가장 최근 로그 2개 전송
- getLogFileStats(): 로그 파일 통계 조회
- transferFile(): ChannelClient로 파일 전송
- getLatestLogFiles(): 최신 파일 검색 (타입별 1개씩)
```

**특징:**
- ChannelClient 사용 (100MB 이상 파일 전송 가능)
- 전송 성공 시 파일 자동 삭제
- 에러 핸들링 및 로깅

#### 2. MainActivity.kt 수정
- 파란색 버튼: 로그 전송
- 회색 버튼: 로그 상태 확인

---

### Mobile 모듈

#### 1. PhoneListenerService.kt 확장
```kotlin
// 위치: app/service/PhoneListenerService.kt

// 추가된 메서드:
override fun onChannelOpened(channel: Channel)
- receiveLogFile(channel): 파일 수신 및 저장
- showFileReceivedNotification(): 알림 표시
```

**특징:**
- ChannelClient.getInputStream()으로 스트리밍 수신
- `received_[원본파일명].csv` 형식으로 저장
- 수신 완료 시 알림 자동 표시

#### 2. SendingDataScreen.kt 재구현
```kotlin
// 위치: app/Screen/SendingDataScreen.kt

// 주요 컴포넌트:
- 파일 목록 (LazyColumn)
- 통계 정보 (파일 개수, 전체 크기)
- 공유 버튼 (ACTION_SEND_MULTIPLE)
- shareLogFiles(): FileProvider + Intent Chooser
```

#### 3. FileProvider 설정

**AndroidManifest.xml:**
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

**file_paths.xml:**
```xml
<paths>
    <files-path name="log_files" path="." />
</paths>
```

---

## 📊 파일 크기 정보

### 로그 파일 크기 (25Hz ACC 기준)

| 기록 시간 | 압축 전 | 압축 후 (예상) |
|---------|--------|--------------|
| 1시간    | ~5.4 MB | ~0.5 MB |
| 5시간    | ~27 MB  | ~2.5 MB |
| 7시간    | ~38 MB  | ~3.5 MB |

### 전송 시간 (참고)

- ChannelClient: 블루투스/Wi-Fi 사용
- 예상 속도: 약 1-3 MB/s
- 38MB 파일: 약 15-40초

---

## 🔍 문제 해결

### 워치에서 전송 실패

**문제:** "연결된 디바이스가 없습니다"

**해결:**
1. 모바일과 워치 블루투스 연결 확인
2. Wear OS 앱이 모바일에 설치되어 있는지 확인
3. 두 기기 모두 앱이 실행 중인지 확인

---

**문제:** "전송할 로그 파일이 없습니다"

**해결:**
1. 알람을 최소 1회 사용했는지 확인
2. 로그 상태 버튼(ⓘ)으로 파일 개수 확인
3. DataRepository가 정상 동작하는지 로그 확인

---

### 모바일에서 파일 수신 안 됨

**문제:** 워치에서 전송했는데 모바일에 안 보임

**해결:**
1. PhoneListenerService가 실행 중인지 확인
   ```bash
   adb shell dumpsys activity services | grep PhoneListenerService
   ```
2. AndroidManifest.xml에 CHANNEL_EVENT 필터 추가 확인
3. Logcat에서 에러 확인:
   ```bash
   adb logcat | grep PhoneListenerService
   ```

---

### 공유 실패

**문제:** "공유 실패: Permission denied"

**해결:**
1. FileProvider 설정 확인
2. file_paths.xml이 올바른 경로인지 확인
3. 앱 재설치 후 테스트

---

## 🧪 테스트 방법

### 1. 로그 파일 생성 테스트

```kotlin
// Wear OS에서 알람 사용
1. 알람 설정
2. SmartAlarmService 실행
3. 최소 1분 이상 실행
4. 알람 종료
5. context.filesDir 확인 → sensor_log_*.csv, inference_log_*.csv 생성 확인
```

### 2. 자동 전송 테스트 (권장)

```kotlin
// Wear OS
1. 알람 설정 및 실행
2. SmartAlarmService 실행 확인
3. 알람 종료 (ACTION_STOP_AND_SEND_RESULT)
4. Logcat 확인:
   - "🚀 Auto-transferring log files to phone..."
   - "✅ Auto-transfer completed: 2 files"

// Mobile
5. 알림 수신 확인 (자동)
6. context.filesDir 확인 → received_*.csv 생성 확인
```

### 3. 수동 전송 테스트 (재전송)

```kotlin
// Wear OS
1. MainActivity 실행
2. 파란색 버튼(↑) 클릭
3. 토스트 메시지 확인: "✅ 2개 파일 재전송 완료"
4. Logcat: "Successfully transferred: sensor_log_xxx.csv"

// Mobile
5. 알림 수신 확인
6. context.filesDir 확인 → received_*.csv 생성 확인
```

### 3. 공유 테스트

```kotlin
// Mobile
1. 설정 → 수면데이터 보내기
2. 파일 목록 표시 확인
3. 공유 버튼 클릭
4. 이메일 선택 → 첨부파일 확인
```

---

## 📝 주요 로그 확인

### Wear OS 로그

```bash
# 자동 전송 확인 (알람 종료 시)
adb logcat | grep -E "SmartAlarmService|LogFileTransferManager"

# 출력 예시 (자동 전송):
I/SmartAlarmService: ✅ Result sent to phone.
I/SmartAlarmService: 🚀 Auto-transferring log files to phone...
I/LogFileTransferManager: Found 2 log files to transfer
I/LogFileTransferManager:   - sensor_log_normal_20260203_143022.csv (12345KB)
I/LogFileTransferManager:   - inference_log_normal_20260203_143022.csv (67KB)
I/LogFileTransferManager: Opening channel: /sleep_log_transfer/sensor_log_normal_20260203_143022.csv
I/LogFileTransferManager: ✅ Successfully transferred: sensor_log_normal_20260203_143022.csv
I/LogFileTransferManager: 🗑️ Deleted transferred file: sensor_log_normal_20260203_143022.csv
I/SmartAlarmService: ✅ Auto-transfer completed: 2 files

# 자동 전송 실패 시 (비치명적):
I/SmartAlarmService: ⚠️ Auto-transfer failed: 연결된 디바이스가 없습니다
```

### Mobile 로그

```bash
# 수신 확인
adb logcat | grep PhoneListenerService

# 출력 예시:
I/PhoneListenerService: 📥 Channel opened: /sleep_log_transfer/sensor_log_normal_20260203_143022.csv
I/PhoneListenerService: Receiving file: sensor_log_normal_20260203_143022.csv
I/PhoneListenerService: ✅ File saved: received_sensor_log_normal_20260203_143022.csv (12345KB)
```

---

## 🎯 핵심 포인트

### ✅ 장점

1. **대용량 전송**: ChannelClient로 100MB+ 파일 가능
2. **자동 정리**: 전송 완료 시 워치 파일 자동 삭제
3. **직관적 UI**: 파일 목록, 크기, 수신 시간 표시
4. **범용 공유**: Intent Chooser로 모든 앱 지원

### ⚠️ 주의사항

1. **블루투스 연결 필수**: 두 기기 간 연결 확인
2. **배터리 소모**: 대용량 파일 전송 시 배터리 영향
3. **전송 시간**: 38MB 파일은 약 15-40초 소요
4. **파일 삭제**: 전송 완료 시 워치에서 자동 삭제 (복구 불가)

---

## 📞 문의

구현 관련 문의사항은 개발 문서를 참고하거나 이슈 등록 바랍니다.
