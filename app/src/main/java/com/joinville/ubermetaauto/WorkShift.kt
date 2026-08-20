package com.joinville.ubermetaauto

/** Turno planejado e o que foi efetivamente realizado. */
data class WorkShift(
    val id: String,
    val plannedStartMillis: Long? = null,
    val plannedEndMillis: Long? = null,
    val plannedPauses: List<PlannedPause> = emptyList(),
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null,
    val pauses: List<WorkPause> = emptyList(),
    val cycleNumbers: Set<Int> = emptySet(),
    val completedRideIds: Set<String> = emptySet(),
    val revenue: Double = 0.0
) {
    fun plannedDurationMillis(): Long? =
        if (plannedStartMillis != null && plannedEndMillis != null) {
            (plannedEndMillis - plannedStartMillis).coerceAtLeast(0L)
        } else {
            null
        }

    fun pausedMillis(nowMillis: Long): Long =
        pauses.sumOf { it.durationMillis(nowMillis) }

    fun workedMillis(nowMillis: Long): Long {
        val end = endedAtMillis ?: nowMillis
        return ((end - startedAtMillis).coerceAtLeast(0L) - pausedMillis(nowMillis))
            .coerceAtLeast(0L)
    }
}
