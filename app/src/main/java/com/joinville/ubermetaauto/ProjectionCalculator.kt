package com.joinville.ubermetaauto

/** Projeções sem efeitos colaterais, reutilizáveis para dia, semana ou mês. */
object ProjectionCalculator {

    fun calculate(
        currentRevenue: Double,
        goalRevenue: Double,
        averageReaisPerHour: Double,
        remainingHours: Double
    ): Projection {
        val safeHours = remainingHours.coerceAtLeast(0.0)
        val remaining = (goalRevenue - currentRevenue).coerceAtLeast(0.0)
        val projectedRevenue = currentRevenue + averageReaisPerHour.coerceAtLeast(0.0) * safeHours

        return Projection(
            projectedRevenue = projectedRevenue,
            amountRemaining = remaining,
            requiredReaisPerHour = if (safeHours > 0.0) remaining / safeHours else 0.0,
            estimatedHoursToGoal = if (remaining == 0.0) {
                0.0
            } else if (averageReaisPerHour > 0.0) {
                remaining / averageReaisPerHour
            } else {
                null
            }
        )
    }
}
