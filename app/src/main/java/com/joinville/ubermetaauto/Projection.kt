package com.joinville.ubermetaauto

/** Resultado de uma projeção baseada no desempenho atual. */
data class Projection(
    val projectedRevenue: Double,
    val amountRemaining: Double,
    val requiredReaisPerHour: Double,
    val estimatedHoursToGoal: Double?
)
