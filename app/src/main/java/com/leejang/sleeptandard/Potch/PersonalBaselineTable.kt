package com.leejang.sleeptandard.Potch

import android.content.ContentValues
import android.content.Context
import android.database.Cursor
import android.database.sqlite.SQLiteDatabase

/** 개인 기준선을 따로 관리하는 지표 종류. */
enum class BaselineMetricType(val candidateColumn: String) {
    RR("rr_median"),
    RRV("rrv_median"),
    HR("hr_median"),
    HRV_RMSSD("hrv_rmssd_median"),
    TEMPERATURE("temperature_median")
}

/** 후보 개수에 따른 개인 기준선 생명주기. */
enum class BaselineLifecycleState {
    EMPTY,
    COLLECTING,
    PROVISIONAL,
    MATURE
}

/**
 * 개인 기준선 테이블의 한 행.
 *
 * center/spread는 후보 20개 미만일 때 null이다. spread에는 dense region의 MAD를 저장한다.
 */
data class PersonalBaselineRecord(
    val metricType: BaselineMetricType,
    val center: Double?,
    val spread: Double?,
    val candidateCount: Int,
    val lifecycleState: BaselineLifecycleState,
    val confidence: Double,
    val lastCandidateId: Long?,
    val updatedAt: Long,
    val distributionVersion: Int,
    val algorithmVersion: Int
) {
    val isUsable: Boolean
        get() =
            lifecycleState == BaselineLifecycleState.PROVISIONAL ||
                    lifecycleState == BaselineLifecycleState.MATURE
}

/** 개인 기준선 테이블 접근 객체. */
class PersonalBaselineTable(context: Context) {
    private val helper = PotchStabilityDatabaseProvider.get(context)

    @Synchronized
    fun loadAll(algorithmVersion: Int): Map<BaselineMetricType, PersonalBaselineRecord> {
        val cursor = helper.readableDatabase.query(
            PotchStabilitySchema.PERSONAL_BASELINE_TABLE,
            null,
            "algorithm_version = ?",
            arrayOf(algorithmVersion.toString()),
            null,
            null,
            null
        )

        return cursor.useAndMap { toRecord() }
            .associateBy { it.metricType }
    }

    @Synchronized
    fun load(
        metricType: BaselineMetricType,
        algorithmVersion: Int
    ): PersonalBaselineRecord? {
        val cursor = helper.readableDatabase.query(
            PotchStabilitySchema.PERSONAL_BASELINE_TABLE,
            null,
            "metric_type = ? AND algorithm_version = ?",
            arrayOf(metricType.name, algorithmVersion.toString()),
            null,
            null,
            null,
            "1"
        )

        cursor.use {
            return if (it.moveToFirst()) it.toRecord() else null
        }
    }

    @Synchronized
    fun upsert(record: PersonalBaselineRecord) {
        helper.writableDatabase.insertWithOnConflict(
            PotchStabilitySchema.PERSONAL_BASELINE_TABLE,
            null,
            record.toContentValues(),
            SQLiteDatabase.CONFLICT_REPLACE
        )
    }

    @Synchronized
    fun upsertAll(records: Collection<PersonalBaselineRecord>) {
        if (records.isEmpty()) return

        val db = helper.writableDatabase
        db.beginTransaction()
        try {
            records.forEach { record ->
                db.insertWithOnConflict(
                    PotchStabilitySchema.PERSONAL_BASELINE_TABLE,
                    null,
                    record.toContentValues(),
                    SQLiteDatabase.CONFLICT_REPLACE
                )
            }
            db.setTransactionSuccessful()
        } finally {
            db.endTransaction()
        }
    }

    private fun PersonalBaselineRecord.toContentValues(): ContentValues {
        return ContentValues().apply {
            put("metric_type", metricType.name)
            put("algorithm_version", algorithmVersion)
            if (center == null || !center.isFinite()) putNull("center") else put("center", center)
            if (spread == null || !spread.isFinite()) putNull("spread") else put("spread", spread)
            put("candidate_count", candidateCount)
            put("lifecycle_state", lifecycleState.name)
            put("confidence", confidence.coerceIn(0.0, 1.0))
            if (lastCandidateId == null) putNull("last_candidate_id") else put("last_candidate_id", lastCandidateId)
            put("updated_at", updatedAt)
            put("distribution_version", distributionVersion)
        }
    }

    private fun Cursor.toRecord(): PersonalBaselineRecord {
        return PersonalBaselineRecord(
            metricType = BaselineMetricType.valueOf(getString(getColumnIndexOrThrow("metric_type"))),
            center = getNullableDouble("center"),
            spread = getNullableDouble("spread"),
            candidateCount = getInt(getColumnIndexOrThrow("candidate_count")),
            lifecycleState = BaselineLifecycleState.valueOf(
                getString(getColumnIndexOrThrow("lifecycle_state"))
            ),
            confidence = getDouble(getColumnIndexOrThrow("confidence")),
            lastCandidateId = getNullableLong("last_candidate_id"),
            updatedAt = getLong(getColumnIndexOrThrow("updated_at")),
            distributionVersion = getInt(getColumnIndexOrThrow("distribution_version")),
            algorithmVersion = getInt(getColumnIndexOrThrow("algorithm_version"))
        )
    }

    private fun Cursor.getNullableDouble(column: String): Double? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getDouble(index)
    }

    private fun Cursor.getNullableLong(column: String): Long? {
        val index = getColumnIndexOrThrow(column)
        return if (isNull(index)) null else getLong(index)
    }

    private inline fun <T> Cursor.useAndMap(mapper: Cursor.() -> T): List<T> {
        use { cursor ->
            val result = mutableListOf<T>()
            while (cursor.moveToNext()) result += cursor.mapper()
            return result
        }
    }
}
