package com.joinville.ubermetaauto

import java.time.LocalDate

/** Critérios opcionais para consultar o histórico de ofertas e corridas. */
data class RideHistoryFilter(
    val period: HistoryPeriod = HistoryPeriod.ALL,
    val customStartDate: LocalDate? = null,
    val customEndDate: LocalDate? = null,
    val type: RideType? = null,
    val decision: RideDecision? = null,
    val outcome: RideOutcome? = null
)

enum class HistoryPeriod {
    ALL,
    TODAY,
    YESTERDAY,
    WEEK,
    MONTH,
    THREE_MONTHS,
    SIX_MONTHS,
    CUSTOM
}
