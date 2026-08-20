package com.joinville.ubermetaauto

/** Sugestão informativa baseada no desempenho já registrado. */
enum class WorkRecommendation {
    KEEP_WORKING,
    REVIEW_STRATEGY,
    CONSIDER_BREAK,
    INSUFFICIENT_DATA
}

data class WorkInsight(
    val recommendation: WorkRecommendation,
    val message: String,
    val currentReaisPerHour: Double,
    val targetReaisPerHour: Double,
    val idlePercentage: Double
)
