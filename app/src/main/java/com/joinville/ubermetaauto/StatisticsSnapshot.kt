package com.joinville.ubermetaauto

/** Indicadores calculados exclusivamente a partir de corridas concluídas. */
data class StatisticsSnapshot(
    val revenue: Double,
    val completedRides: Int,
    val reaisPerHour: Double,
    val reaisPerKm: Double,
    val ridesPerHour: Double,
    val averageRideMinutes: Double,
    val averageIdleMinutes: Double,
    val workedMillis: Long,
    val idleMillis: Long,
    val pausedMillis: Long,
    val byType: Map<RideType, TypeStatistics>
)

data class TypeStatistics(
    val completedRides: Int,
    val revenue: Double,
    val reaisPerKm: Double
)
