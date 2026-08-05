package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase
import android.database.sqlite.SQLiteOpenHelper

/**
 * 안정 episode 하나에서 추출한 대표값.
 *
 * 매초 값을 저장하지 않고, 진입 조건을 통과해 실제 안정 episode가 끝났을 때
 * episode 전체의 중앙값/품질 대표값을 후보 1개로 저장한다.
 */
data class StableCandidateRecord(
    val id: Long = 0L,
    val sleepSessionId: String,
    val episodeId: String,
    val startedAt: Long,
    val endedAt: Long,
    val durationSec: Int,

    val rrMedian: Double?,
    val rrvMedian: Double?,
    val hrMedian: Double?,
    val hrvRmssdMedian: Double?,
    val hrvLfMedian: Double?,
    val hrvHfMedian: Double?,
    val temperatureMedian: Double?,
    val temperatureSlopeMedian: Double?,

    val rrQuality: Double?,
    val rrvQuality: Double?,
    val hrQuality: Double?,
    val hrvQuality: Double?,
    val temperatureQuality: Double?,

    val movementStabilityScore: Double?,
    val respiratoryStabilityScore: Double?,
    val cardiacStabilityScore: Double?,
    val temperatureStabilityScore: Double?,
    val overallStabilityScore: Double,

    val usedDomainCount: Int,
    val analysisSegmentId: Long,
    val reconnectCount: Int,
    val continuityBreakCount: Int,
    val packetLossCount: Int,
    val algorithmVersion: Int,
    val createdAt: Long
)

/**
 * 안정 후보와 개인 기준선을 같은 DB 파일에서 관리하기 위한 스키마 정의.
 */
internal object PotchStabilitySchema {
    const val DATABASE_NAME = "potch_stability.db"
    const val DATABASE_VERSION = 1

    const val STABLE_CANDIDATE_TABLE = "stable_candidate"
    const val PERSONAL_BASELINE_TABLE = "personal_baseline"

    const val CREATE_STABLE_CANDIDATE_TABLE = """
        CREATE TABLE IF NOT EXISTS stable_candidate (
            id INTEGER PRIMARY KEY AUTOINCREMENT,
            sleep_session_id TEXT NOT NULL,
            episode_id TEXT NOT NULL UNIQUE,
            started_at INTEGER NOT NULL,
            ended_at INTEGER NOT NULL,
            duration_sec INTEGER NOT NULL,

            rr_median REAL,
            rrv_median REAL,
            hr_median REAL,
            hrv_rmssd_median REAL,
            hrv_lf_median REAL,
            hrv_hf_median REAL,
            temperature_median REAL,
            temperature_slope_median REAL,

            rr_quality REAL,
            rrv_quality REAL,
            hr_quality REAL,
            hrv_quality REAL,
            temperature_quality REAL,

            movement_stability_score REAL,
            respiratory_stability_score REAL,
            cardiac_stability_score REAL,
            temperature_stability_score REAL,
            overall_stability_score REAL NOT NULL,

            used_domain_count INTEGER NOT NULL,
            analysis_segment_id INTEGER NOT NULL,
            reconnect_count INTEGER NOT NULL,
            continuity_break_count INTEGER NOT NULL,
            packet_loss_count INTEGER NOT NULL,
            algorithm_version INTEGER NOT NULL,
            created_at INTEGER NOT NULL
        )
    """

    const val CREATE_PERSONAL_BASELINE_TABLE = """
        CREATE TABLE IF NOT EXISTS personal_baseline (
            metric_type TEXT NOT NULL,
            algorithm_version INTEGER NOT NULL,
            center REAL,
            spread REAL,
            candidate_count INTEGER NOT NULL,
            lifecycle_state TEXT NOT NULL,
            confidence REAL NOT NULL,
            last_candidate_id INTEGER,
            updated_at INTEGER NOT NULL,
            distribution_version INTEGER NOT NULL,
            PRIMARY KEY(metric_type, algorithm_version)
        )
    """
}

internal class PotchStabilityDbHelper(context: Context) : SQLiteOpenHelper(
    context.applicationContext,
    PotchStabilitySchema.DATABASE_NAME,
    null,
    PotchStabilitySchema.DATABASE_VERSION
) {
    override fun onCreate(db: SQLiteDatabase) {
        db.execSQL(PotchStabilitySchema.CREATE_STABLE_CANDIDATE_TABLE)
        db.execSQL(PotchStabilitySchema.CREATE_PERSONAL_BASELINE_TABLE)
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_stable_candidate_created " +
                    "ON ${PotchStabilitySchema.STABLE_CANDIDATE_TABLE}(created_at DESC)"
        )
        db.execSQL(
            "CREATE INDEX IF NOT EXISTS idx_stable_candidate_session " +
                    "ON ${PotchStabilitySchema.STABLE_CANDIDATE_TABLE}(sleep_session_id)"
        )
    }

    override fun onUpgrade(db: SQLiteDatabase, oldVersion: Int, newVersion: Int) {
        // 첫 구현 버전. 이후 스키마가 변경되면 데이터 보존 migration을 여기에 추가한다.
    }
}

internal object PotchStabilityDatabaseProvider {
    @Volatile
    private var instance: PotchStabilityDbHelper? = null

    fun get(context: Context): PotchStabilityDbHelper {
        return instance ?: synchronized(this) {
            instance ?: PotchStabilityDbHelper(context).also { instance = it }
        }
    }
}

/**
 * 안정 후보 테이블 접근 객체.
 *
 * - 한 수면 세션에서 선별된 최대 5개 episode 대표값을 저장한다.
 * - 최근 90일, 최대 300개 제한을 적용한다.
 * - 개인 기준선 재계산 시 지표별 유효 값을 읽어 온다.
 */
class StableCandidateTable(context: Context) {
    private val helper = PotchStabilityDatabaseProvider.get(context)

    @Synchronized
    fun insertAll(records: List<StableCandidateRecord>): List<Long> {
        if (records.isEmpty()) return emptyList()

        val db = helper.writableDatabase
        val insertedIds = mutableListOf<Long>()

        db.beginTransaction()
        try {
            records.forEach { record ->
                val id = db.insertWithOnConflict(
                    PotchStabilitySchema.STABLE_CANDIDATE_TABLE,
                    null,
                    record.toContentValues(),
                    SQLiteDatabase.CONFLICT_IGNORE
                )
                if (id >= 0L) insertedIds += id
            }

            // START_STICKY로 프로세스가 재생성되어 같은 session id가 이어져도
            // 수면 세션당 최대 5개 제한을 DB 수준에서 다시 보장한다.
            records.map { it.sleepSessionId }.distinct().forEach { sessionId ->
                trimSessionToLimit(db, sessionId, 5)
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }

        return insertedIds
    }

    private fun trimSessionToLimit(
        db: SQLiteDatabase,
        sleepSessionId: String,
        maxCount: Int
    ) {
        val cursor = db.query(
            PotchStabilitySchema.STABLE_CANDIDATE_TABLE,
            arrayOf("id"),
            "sleep_session_id = ?",
            arrayOf(sleepSessionId),
            null,
            null,
            "overall_stability_score DESC, used_domain_count DESC, duration_sec DESC, created_at ASC"
        )

        val overflowIds = cursor.use {
            val ids = mutableListOf<Long>()
            var index = 0
            while (it.moveToNext()) {
                if (index >= maxCount) ids += it.getLong(0)
                index += 1
            }
            ids
        }

        overflowIds.forEach { id ->
            db.delete(
                PotchStabilitySchema.STABLE_CANDIDATE_TABLE,
                "id = ?",
                arrayOf(id.toString())
            )
        }
    }

    @Synchronized
    fun prune(
        nowMillis: Long,
        maxAgeDays: Int = 90,
        maxRecordCount: Int = 300
    ) {
        val db = helper.writableDatabase
        val minimumCreatedAt = nowMillis - maxAgeDays * 24L * 60L * 60L * 1000L

        db.delete(
            PotchStabilitySchema.STABLE_CANDIDATE_TABLE,
            "created_at < ?",
            arrayOf(minimumCreatedAt.toString())
        )

        db.execSQL(
            """
            DELETE FROM ${PotchStabilitySchema.STABLE_CANDIDATE_TABLE}
            WHERE id IN (
                SELECT id
                FROM ${PotchStabilitySchema.STABLE_CANDIDATE_TABLE}
                ORDER BY created_at DESC, id DESC
                LIMIT -1 OFFSET $maxRecordCount
            )
            """.trimIndent()
        )
    }

    @Synchronized
    fun loadMetricValues(
        metricType: BaselineMetricType,
        algorithmVersion: Int,
        nowMillis: Long,
        maxAgeDays: Int = 90,
        limit: Int = 300
    ): List<StableMetricValue> {
        val column = metricType.candidateColumn
        val minimumCreatedAt = nowMillis - maxAgeDays * 24L * 60L * 60L * 1000L

        val cursor = helper.readableDatabase.query(
            PotchStabilitySchema.STABLE_CANDIDATE_TABLE,
            arrayOf("id", column, "created_at", "sleep_session_id"),
            "algorithm_version = ? AND created_at >= ? AND $column IS NOT NULL",
            arrayOf(algorithmVersion.toString(), minimumCreatedAt.toString()),
            null,
            null,
            "created_at DESC, id DESC",
            limit.toString()
        )

        return cursor.useAndMap {
            StableMetricValue(
                candidateId = getLong(getColumnIndexOrThrow("id")),
                value = getDouble(getColumnIndexOrThrow(column)),
                createdAt = getLong(getColumnIndexOrThrow("created_at")),
                sleepSessionId = getString(getColumnIndexOrThrow("sleep_session_id"))
            )
        }
    }

    @Synchronized
    fun countAll(): Int {
        helper.readableDatabase.rawQuery(
            "SELECT COUNT(*) FROM ${PotchStabilitySchema.STABLE_CANDIDATE_TABLE}",
            null
        ).use { cursor ->
            return if (cursor.moveToFirst()) cursor.getInt(0) else 0
        }
    }

    private fun StableCandidateRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("sleep_session_id", sleepSessionId)
            put("episode_id", episodeId)
            put("started_at", startedAt)
            put("ended_at", endedAt)
            put("duration_sec", durationSec)

            putNullable("rr_median", rrMedian)
            putNullable("rrv_median", rrvMedian)
            putNullable("hr_median", hrMedian)
            putNullable("hrv_rmssd_median", hrvRmssdMedian)
            putNullable("hrv_lf_median", hrvLfMedian)
            putNullable("hrv_hf_median", hrvHfMedian)
            putNullable("temperature_median", temperatureMedian)
            putNullable("temperature_slope_median", temperatureSlopeMedian)

            putNullable("rr_quality", rrQuality)
            putNullable("rrv_quality", rrvQuality)
            putNullable("hr_quality", hrQuality)
            putNullable("hrv_quality", hrvQuality)
            putNullable("temperature_quality", temperatureQuality)

            putNullable("movement_stability_score", movementStabilityScore)
            putNullable("respiratory_stability_score", respiratoryStabilityScore)
            putNullable("cardiac_stability_score", cardiacStabilityScore)
            putNullable("temperature_stability_score", temperatureStabilityScore)
            put("overall_stability_score", overallStabilityScore)

            put("used_domain_count", usedDomainCount)
            put("analysis_segment_id", analysisSegmentId)
            put("reconnect_count", reconnectCount)
            put("continuity_break_count", continuityBreakCount)
            put("packet_loss_count", packetLossCount)
            put("algorithm_version", algorithmVersion)
            put("created_at", createdAt)
        }
    }

    private fun ContentValues.putNullable(key: String, value: Double?) {
        if (value == null || !value.isFinite()) putNull(key) else put(key, value)
    }

    private inline fun <T> Cursor.useAndMap(mapper: Cursor.() -> T): List<T> {
        use { cursor ->
            val result = mutableListOf<T>()
            while (cursor.moveToNext()) result += cursor.mapper()
            return result
        }
    }
}

/** 지표별 분포 재계산에 사용하는 최소 값 객체. */
data class StableMetricValue(
    val candidateId: Long,
    val value: Double,
    val createdAt: Long,
    val sleepSessionId: String
)
