package com.joinville.ubermetaauto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Armazena localmente o ciclo ativo e o histórico de ciclos. */
class CycleStateStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    fun load(): CycleState = runCatching {
        val root = JSONObject(preferences.getString(KEY_STATE, "{}") ?: "{}")
        CycleState(
            current = root.optJSONObject("current")?.toWorkCycle(),
            completed = root.optJSONArray("completed").toCycleRecords()
        )
    }.getOrDefault(CycleState())

    fun save(current: WorkCycle?, completed: List<CycleRecord>) {
        val root = JSONObject().apply {
            put("current", current?.toJson())
            put("completed", JSONArray().apply {
                completed.forEach { put(it.toJson()) }
            })
        }

        preferences.edit().putString(KEY_STATE, root.toString()).apply()
    }

    private fun WorkCycle.toJson(): JSONObject = JSONObject().apply {
        put("number", number)
        put("startedAtMillis", startedAtMillis)
        put("durationMillis", durationMillis)
        put("hourlyTarget", hourlyTarget)
        put("revenue", revenue)
        put("rideCount", rideCount)
        put("countedRideIds", JSONArray(countedRideIds.toList()))
        put("pauses", pauses.toPauseJson())
        put("workIntervals", workIntervals.toWorkIntervalJson())
        put("finishedAtMillis", finishedAtMillis)
    }

    private fun JSONObject.toWorkCycle(): WorkCycle = WorkCycle(
        number = getInt("number"),
        startedAtMillis = getLong("startedAtMillis"),
        durationMillis = getLong("durationMillis"),
        hourlyTarget = getDouble("hourlyTarget"),
        revenue = getDouble("revenue"),
        rideCount = getInt("rideCount"),
        countedRideIds = optJSONArray("countedRideIds").toStringSet(),
        pauses = optJSONArray("pauses").toPauses(),
        workIntervals = optJSONArray("workIntervals").toIntervals(),
        finishedAtMillis = optNullableLong("finishedAtMillis")
    )

    private fun CycleRecord.toJson(): JSONObject = JSONObject().apply {
        put("number", number)
        put("startedAtMillis", startedAtMillis)
        put("finishedAtMillis", finishedAtMillis)
        put("revenue", revenue)
        put("targetAmount", targetAmount)
        put("workedMillis", workedMillis)
        put("idleMillis", idleMillis)
        put("pausedMillis", pausedMillis)
        put("rideCount", rideCount)
        put("pauses", pauses.toPauseJson())
        put("targetReached", targetReached)
    }

    private fun JSONObject.toCycleRecord(): CycleRecord = CycleRecord(
        number = getInt("number"),
        startedAtMillis = getLong("startedAtMillis"),
        finishedAtMillis = getLong("finishedAtMillis"),
        revenue = getDouble("revenue"),
        targetAmount = getDouble("targetAmount"),
        workedMillis = getLong("workedMillis"),
        idleMillis = getLong("idleMillis"),
        pausedMillis = getLong("pausedMillis"),
        rideCount = getInt("rideCount"),
        pauses = optJSONArray("pauses").toPauses(),
        targetReached = getBoolean("targetReached")
    )

    private fun List<WorkPause>.toPauseJson(): JSONArray = JSONArray().apply {
        this@toPauseJson.forEach { pause ->
            put(JSONObject().apply {
                put("startedAtMillis", pause.startedAtMillis)
                put("endedAtMillis", pause.endedAtMillis)
            })
        }
    }

    private fun List<WorkInterval>.toWorkIntervalJson(): JSONArray = JSONArray().apply {
        this@toWorkIntervalJson.forEach { interval ->
            put(JSONObject().apply {
                put("rideRecordId", interval.rideRecordId)
                put("startedAtMillis", interval.startedAtMillis)
                put("endedAtMillis", interval.endedAtMillis)
            })
        }
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        val array = this@toStringSet ?: return@buildSet
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun JSONArray?.toPauses(): List<WorkPause> = buildList {
        val array = this@toPauses ?: return@buildList
        for (index in 0 until array.length()) {
            val value = array.getJSONObject(index)
            add(WorkPause(value.getLong("startedAtMillis"), value.optNullableLong("endedAtMillis")))
        }
    }

    private fun JSONArray?.toIntervals(): List<WorkInterval> = buildList {
        val array = this@toIntervals ?: return@buildList
        for (index in 0 until array.length()) {
            val value = array.getJSONObject(index)
            add(
                WorkInterval(
                    value.getString("rideRecordId"),
                    value.getLong("startedAtMillis"),
                    value.optNullableLong("endedAtMillis")
                )
            )
        }
    }

    private fun JSONArray?.toCycleRecords(): List<CycleRecord> = buildList {
        val array = this@toCycleRecords ?: return@buildList
        for (index in 0 until array.length()) add(array.getJSONObject(index).toCycleRecord())
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private companion object {
        const val PREFS = "cycle_state"
        const val KEY_STATE = "state"
    }
}

data class CycleState(
    val current: WorkCycle? = null,
    val completed: List<CycleRecord> = emptyList()
)
