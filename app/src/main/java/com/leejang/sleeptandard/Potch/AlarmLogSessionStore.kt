package com.leejang.sleeptandard.Potch

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

/** Synchronous small metadata commits keep receiver/service races from reopening finished files. */
class AlarmLogSessionStore(context: Context) {
    private val preferences = context.applicationContext.getSharedPreferences("alarm_log_sessions", Context.MODE_PRIVATE)

    fun load(): List<AlarmLogSession> = synchronized(lock) { read() }

    fun schedule(alarmId: Int, target: Long) = update {
        AlarmLogSessionPolicy.schedule(it, alarmId, target, System.currentTimeMillis(), UUID.randomUUID().toString())
    }

    fun cancel(target: Long) = update {
        AlarmLogSessionPolicy.cancel(it, target, System.currentTimeMillis())
    }

    fun ring(alarmId: Int, target: Long): String? = synchronized(lock) {
        val sessions = update { AlarmLogSessionPolicy.ring(it, alarmId, target) }
        sessions.lastOrNull { it.alarmId == alarmId && it.phase == AlarmLogPhase.RINGING &&
            (target == 0L || it.targetTimeMillis == target) }?.id
    }

    fun dismiss(id: String): AlarmLogSession? = synchronized(lock) {
        update { AlarmLogSessionPolicy.dismiss(it, id, System.currentTimeMillis()) }.find { it.id == id }
    }

    fun removeFinished(ids: Set<String>) = update { sessions -> sessions.filterNot { it.id in ids } }

    private fun update(transform: (List<AlarmLogSession>) -> List<AlarmLogSession>): List<AlarmLogSession> =
        synchronized(lock) {
            val next = transform(read())
            val array = JSONArray()
            next.forEach {
                array.put(JSONObject().put("id", it.id).put("alarmId", it.alarmId)
                    .put("target", it.targetTimeMillis).put("started", it.startedAtMillis)
                    .put("phase", it.phase.name).put("rawStop", it.rawStopAtMillis)
                    .put("stabilityStop", it.stabilityStopAtMillis))
            }
            check(preferences.edit().putString("sessions", array.toString()).commit())
            next
        }

    private fun read(): List<AlarmLogSession> {
        val array = JSONArray(preferences.getString("sessions", "[]") ?: "[]")
        return (0 until array.length()).map { index ->
            val row = array.getJSONObject(index)
            AlarmLogSession(row.getString("id"), row.getInt("alarmId"), row.getLong("target"),
                row.getLong("started"), AlarmLogPhase.valueOf(row.getString("phase")),
                row.optLong("rawStop"), row.optLong("stabilityStop"))
        }
    }

    companion object { private val lock = Any() }
}
