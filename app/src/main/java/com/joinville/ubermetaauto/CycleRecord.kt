package com.joinville.ubermetaauto

/** Foto imutável de um ciclo encerrado. */
data class CycleRecord(
    val number: Int,
    val startedAtMillis: Long,
    val finishedAtMillis: Long,
    val revenue: Double,
    val targetAmount: Double,
    val workedMillis: Long,
    val idleMillis: Long,
    val pausedMillis: Long,
    val rideCount: Int,
    val pauses: List<WorkPause>,
    val targetReached: Boolean
) {
    companion object {
        fun from(cycle: WorkCycle): CycleRecord {
            val finishedAt = requireNotNull(cycle.finishedAtMillis)
            return CycleRecord(
                number = cycle.number,
                startedAtMillis = cycle.startedAtMillis,
                finishedAtMillis = finishedAt,
                revenue = cycle.revenue,
                targetAmount = cycle.targetAmount,
                workedMillis = cycle.workedMillis(finishedAt),
                idleMillis = cycle.idleMillis(finishedAt),
                pausedMillis = cycle.pausedMillis(finishedAt),
                rideCount = cycle.rideCount,
                pauses = cycle.pauses,
                targetReached = cycle.revenue >= cycle.targetAmount
            )
        }
    }
}
