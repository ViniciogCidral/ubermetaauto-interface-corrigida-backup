package com.joinville.ubermetaauto

import java.util.Locale

/** Apresentação da projeção da meta informada pelo motorista. */
object ProjectionFormatter {

    fun format(goalRevenue: Double, remainingHours: Double): String {
        if (goalRevenue <= 0.0 || remainingHours < 0.0) {
            return "Informe uma meta de faturamento e as horas restantes."
        }

        val statistics = StatisticsCalculator.calculate(
            records = RideHistoryManager.all(),
            cycles = CycleManager.history()
        )
        val projection = ProjectionCalculator.calculate(
            currentRevenue = statistics.revenue,
            goalRevenue = goalRevenue,
            averageReaisPerHour = statistics.reaisPerHour,
            remainingHours = remainingHours
        )

        val estimated = projection.estimatedHoursToGoal?.let { hours ->
            String.format(Locale("pt", "BR"), "%.1f h", hours)
        } ?: "sem média suficiente"

        return String.format(
            Locale("pt", "BR"),
            "PROJEÇÃO\nAtual: R$ %.2f | Meta: R$ %.2f\nProjetado: R$ %.2f\nFalta: R$ %.2f | Necessário: R$ %.2f/h\nEstimativa para a meta: %s",
            statistics.revenue,
            goalRevenue,
            projection.projectedRevenue,
            projection.amountRemaining,
            projection.requiredReaisPerHour,
            estimated
        )
    }
}
