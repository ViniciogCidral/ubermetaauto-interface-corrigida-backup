package com.joinville.ubermetaauto

import java.util.Locale

object StatisticsFormatter {

    fun current(): String {
        val statistics = StatisticsCalculator.calculate(
            records = RideHistoryManager.all(),
            cycles = CycleManager.history()
        )

        return String.format(
            Locale("pt", "BR"),
            "ESTATÍSTICAS\nFaturamento: R$ %.2f | Corridas: %d\nR$ %.2f/h | R$ %.2f/km | %.2f corridas/h\nMédia corrida: %.1f min | Média vazio: %.1f min",
            statistics.revenue,
            statistics.completedRides,
            statistics.reaisPerHour,
            statistics.reaisPerKm,
            statistics.ridesPerHour,
            statistics.averageRideMinutes,
            statistics.averageIdleMinutes
        )
    }
}
