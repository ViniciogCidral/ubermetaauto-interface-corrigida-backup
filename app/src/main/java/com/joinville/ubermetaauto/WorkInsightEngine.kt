package com.joinville.ubermetaauto

/**
 * Analisa somente dados históricos do próprio aplicativo.
 *
 * Não usa localização, não acessa dados externos e não executa ações no Uber.
 * O resultado é sempre uma sugestão para o motorista decidir.
 */
object WorkInsightEngine {

    fun analyze(targets: RideTargets): WorkInsight {
        val statistics = StatisticsCalculator.calculate(
            records = RideHistoryManager.all(),
            cycles = CycleManager.history()
        )
        val totalActiveMillis = statistics.workedMillis + statistics.idleMillis
        val idlePercentage = if (totalActiveMillis > 0L) {
            statistics.idleMillis * 100.0 / totalActiveMillis
        } else {
            0.0
        }

        if (statistics.completedRides == 0 || statistics.workedMillis == 0L) {
            return insight(
                WorkRecommendation.INSUFFICIENT_DATA,
                "Ainda não há corridas concluídas suficientes para uma recomendação.",
                statistics.reaisPerHour,
                targets.hourly,
                idlePercentage
            )
        }

        if (idlePercentage >= 55.0) {
            return insight(
                WorkRecommendation.CONSIDER_BREAK,
                "O tempo vazio está alto. Considere pausar ou reavaliar o horário de trabalho.",
                statistics.reaisPerHour,
                targets.hourly,
                idlePercentage
            )
        }

        if (statistics.reaisPerHour < targets.hourly * 0.85) {
            return insight(
                WorkRecommendation.REVIEW_STRATEGY,
                "O resultado atual está abaixo de 85% da meta por hora. Avalie as próximas ofertas com mais cautela.",
                statistics.reaisPerHour,
                targets.hourly,
                idlePercentage
            )
        }

        return insight(
            WorkRecommendation.KEEP_WORKING,
            "O desempenho atual está dentro da faixa esperada para a meta configurada.",
            statistics.reaisPerHour,
            targets.hourly,
            idlePercentage
        )
    }

    private fun insight(
        recommendation: WorkRecommendation,
        message: String,
        currentReaisPerHour: Double,
        targetReaisPerHour: Double,
        idlePercentage: Double
    ) = WorkInsight(
        recommendation = recommendation,
        message = message,
        currentReaisPerHour = currentReaisPerHour,
        targetReaisPerHour = targetReaisPerHour,
        idlePercentage = idlePercentage
    )
}
