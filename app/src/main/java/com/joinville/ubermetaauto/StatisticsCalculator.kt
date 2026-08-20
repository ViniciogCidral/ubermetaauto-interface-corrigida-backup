package com.joinville.ubermetaauto

/** Cálculos puros para a tela de estatísticas e projeções futuras. */
object StatisticsCalculator {

    fun calculate(
        records: List<RideRecord>,
        cycles: List<CycleRecord>
    ): StatisticsSnapshot {
        val completed = records.filter { it.outcome == RideOutcome.COMPLETED }
        val revenue = completed.sumOf { it.offer.fare }
        val totalKm = completed.sumOf { it.totalKm }
        val totalRideMinutes = completed.sumOf { it.totalMinutes }
        val workedMillis = cycles.sumOf { it.workedMillis }
        val idleMillis = cycles.sumOf { it.idleMillis }
        val pausedMillis = cycles.sumOf { it.pausedMillis }
        val workedHours = workedMillis / MILLIS_PER_HOUR.toDouble()

        return StatisticsSnapshot(
            revenue = revenue,
            completedRides = completed.size,
            reaisPerHour = if (workedHours > 0.0) revenue / workedHours else 0.0,
            reaisPerKm = if (totalKm > 0.0) revenue / totalKm else 0.0,
            ridesPerHour = if (workedHours > 0.0) completed.size / workedHours else 0.0,
            averageRideMinutes = if (completed.isNotEmpty()) {
                totalRideMinutes / completed.size
            } else {
                0.0
            },
            averageIdleMinutes = if (cycles.isNotEmpty()) {
                idleMillis / MILLIS_PER_MINUTE.toDouble() / cycles.size
            } else {
                0.0
            },
            workedMillis = workedMillis,
            idleMillis = idleMillis,
            pausedMillis = pausedMillis,
            byType = completed.groupBy { it.type }.mapValues { (_, typedRecords) ->
                val typedRevenue = typedRecords.sumOf { it.offer.fare }
                val typedKm = typedRecords.sumOf { it.totalKm }
                TypeStatistics(
                    completedRides = typedRecords.size,
                    revenue = typedRevenue,
                    reaisPerKm = if (typedKm > 0.0) typedRevenue / typedKm else 0.0
                )
            }
        )
    }

    private const val MILLIS_PER_MINUTE = 60L * 1000L
    private const val MILLIS_PER_HOUR = 60L * 60L * 1000L
}
