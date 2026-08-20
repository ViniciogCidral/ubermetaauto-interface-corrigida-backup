package com.joinville.ubermetaauto

/** Estado de um ciclo de trabalho. */
data class WorkCycle(
    val number: Int,
    val startedAtMillis: Long,
    val durationMillis: Long = DEFAULT_DURATION_MILLIS,
    val hourlyTarget: Double,
    val revenue: Double = 0.0,
    val rideCount: Int = 0,
    val countedRideIds: Set<String> = emptySet(),
    val pauses: List<WorkPause> = emptyList(),
    val workIntervals: List<WorkInterval> = emptyList(),
    val finishedAtMillis: Long? = null
) {

    val targetAmount: Double
        get() = hourlyTarget * durationMillis / MILLIS_PER_HOUR

    fun isPaused(): Boolean = pauses.lastOrNull()?.endedAtMillis == null

    fun pausedMillis(nowMillis: Long): Long =
        pauses.sumOf { pause -> pause.durationMillis(nowMillis) }

    fun elapsedMillis(nowMillis: Long): Long =
        ((nowMillis - startedAtMillis).coerceAtLeast(0L) - pausedMillis(nowMillis))
            .coerceIn(0L, durationMillis)

    fun remainingMillis(nowMillis: Long): Long =
        (durationMillis - elapsedMillis(nowMillis)).coerceAtLeast(0L)

    fun expectedFinishedAtMillis(nowMillis: Long): Long =
        startedAtMillis + durationMillis + pausedMillis(nowMillis)

    fun workedMillis(nowMillis: Long): Long =
        workIntervals.sumOf { interval ->
            val intervalEnd = interval.endedAtMillis ?: nowMillis
            val grossDuration = interval.durationMillis(nowMillis)
            val pausedDuringRide = pauses.sumOf { pause ->
                overlapMillis(
                    startA = interval.startedAtMillis,
                    endA = intervalEnd,
                    startB = pause.startedAtMillis,
                    endB = pause.endedAtMillis ?: nowMillis
                )
            }

            (grossDuration - pausedDuringRide).coerceAtLeast(0L)
        }.coerceAtMost(elapsedMillis(nowMillis))

    fun idleMillis(nowMillis: Long): Long =
        (elapsedMillis(nowMillis) - workedMillis(nowMillis)).coerceAtLeast(0L)

    fun missingAmount(): Double =
        (targetAmount - revenue).coerceAtLeast(0.0)

    fun isFinished(nowMillis: Long): Boolean =
        finishedAtMillis != null || remainingMillis(nowMillis) == 0L

    companion object {
        const val DEFAULT_DURATION_MILLIS = 60L * 60L * 1000L
        private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
    }

    private fun overlapMillis(
        startA: Long,
        endA: Long,
        startB: Long,
        endB: Long
    ): Long = (minOf(endA, endB) - maxOf(startA, startB)).coerceAtLeast(0L)
}
