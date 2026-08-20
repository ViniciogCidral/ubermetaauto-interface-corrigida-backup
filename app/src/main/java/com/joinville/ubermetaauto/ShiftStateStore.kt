package com.joinville.ubermetaauto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Persistência local do turno ativo e dos turnos concluídos. */
class ShiftStateStore(context: Context) {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    fun load(): ShiftState = runCatching {
        val root = JSONObject(preferences.getString(KEY_STATE, "{}") ?: "{}")
        ShiftState(
            current = root.optJSONObject("current")?.toShift(),
            completed = root.optJSONArray("completed").toShifts(),
            plan = root.optJSONObject("plan")?.toPlan()
        )
    }.getOrDefault(ShiftState())

    fun save(
        current: WorkShift?,
        completed: List<WorkShift>,
        plan: ShiftPlan?
    ) {
        preferences.edit().putString(
            KEY_STATE,
            JSONObject().apply {
                put("current", current?.toJson())
                put("plan", plan?.toJson())
                put("completed", JSONArray().apply {
                    completed.forEach { put(it.toJson()) }
                })
            }.toString()
        ).apply()
    }

    private fun WorkShift.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("plannedStartMillis", plannedStartMillis)
        put("plannedEndMillis", plannedEndMillis)
        put("plannedPauses", plannedPauses.toJson())
        put("startedAtMillis", startedAtMillis)
        put("endedAtMillis", endedAtMillis)
        put("pauses", JSONArray().apply {
            pauses.forEach { pause ->
                put(JSONObject().apply {
                    put("startedAtMillis", pause.startedAtMillis)
                    put("endedAtMillis", pause.endedAtMillis)
                })
            }
        })
        put("cycleNumbers", JSONArray(cycleNumbers.toList()))
        put("completedRideIds", JSONArray(completedRideIds.toList()))
        put("revenue", revenue)
    }

    private fun JSONObject.toShift(): WorkShift = WorkShift(
        id = getString("id"),
        plannedStartMillis = optNullableLong("plannedStartMillis"),
        plannedEndMillis = optNullableLong("plannedEndMillis"),
        plannedPauses = optJSONArray("plannedPauses").toPlannedPauses(),
        startedAtMillis = getLong("startedAtMillis"),
        endedAtMillis = optNullableLong("endedAtMillis"),
        pauses = optJSONArray("pauses").toPauses(),
        cycleNumbers = optJSONArray("cycleNumbers").toIntSet(),
        completedRideIds = optJSONArray("completedRideIds").toStringSet(),
        revenue = getDouble("revenue")
    )

    private fun ShiftPlan.toJson(): JSONObject = JSONObject().apply {
        put("startsAtMillis", startsAtMillis)
        put("endsAtMillis", endsAtMillis)
        put("plannedPauses", plannedPauses.toJson())
    }

    private fun JSONObject.toPlan(): ShiftPlan = ShiftPlan(
        startsAtMillis = getLong("startsAtMillis"),
        endsAtMillis = getLong("endsAtMillis"),
        plannedPauses = optJSONArray("plannedPauses").toPlannedPauses()
    )

    private fun JSONArray?.toPauses(): List<WorkPause> = buildList {
        val array = this@toPauses ?: return@buildList
        for (index in 0 until array.length()) {
            val value = array.getJSONObject(index)
            add(WorkPause(value.getLong("startedAtMillis"), value.optNullableLong("endedAtMillis")))
        }
    }

    private fun JSONArray?.toShifts(): List<WorkShift> = buildList {
        val array = this@toShifts ?: return@buildList
        for (index in 0 until array.length()) {
            add(array.getJSONObject(index).toShift())
        }
    }

    private fun List<PlannedPause>.toJson(): JSONArray = JSONArray().apply {
        this@toJson.forEach { pause ->
            put(JSONObject().apply {
                put("startsAtMillis", pause.startsAtMillis)
                put("endsAtMillis", pause.endsAtMillis)
            })
        }
    }

    private fun JSONArray?.toPlannedPauses(): List<PlannedPause> = buildList {
        val array = this@toPlannedPauses ?: return@buildList
        for (index in 0 until array.length()) {
            val value = array.getJSONObject(index)
            add(
                PlannedPause(
                    value.getLong("startsAtMillis"),
                    value.getLong("endsAtMillis")
                )
            )
        }
    }

    private fun JSONArray?.toIntSet(): Set<Int> = buildSet {
        val array = this@toIntSet ?: return@buildSet
        for (index in 0 until array.length()) add(array.getInt(index))
    }

    private fun JSONArray?.toStringSet(): Set<String> = buildSet {
        val array = this@toStringSet ?: return@buildSet
        for (index in 0 until array.length()) add(array.getString(index))
    }

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private companion object {
        const val PREFS = "shift_state"
        const val KEY_STATE = "state"
    }
}

data class ShiftState(
    val current: WorkShift? = null,
    val completed: List<WorkShift> = emptyList(),
    val plan: ShiftPlan? = null
)
