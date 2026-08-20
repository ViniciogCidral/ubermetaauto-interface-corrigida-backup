package com.joinville.ubermetaauto

/**
 * Planejamento salvo antes de um turno começar.
 *
 * O planejamento não inicia captura, ciclo nem turno por conta própria. Ele
 * apenas informa ao fluxo de início qual período a pessoa pretende trabalhar.
 */
data class ShiftPlan(
    val startsAtMillis: Long,
    val endsAtMillis: Long,
    val plannedPauses: List<PlannedPause> = emptyList()
) {
    init {
        require(endsAtMillis > startsAtMillis) {
            "O fim do turno deve ser posterior ao início."
        }
        require(plannedPauses.all { pause ->
            pause.startsAtMillis >= startsAtMillis &&
                pause.endsAtMillis <= endsAtMillis
        }) {
            "As pausas planejadas devem estar dentro do turno."
        }
        val orderedPauses = plannedPauses.sortedBy { it.startsAtMillis }
        require(orderedPauses.zipWithNext().all { (first, second) ->
            first.endsAtMillis <= second.startsAtMillis
        }) {
            "As pausas planejadas não podem se sobrepor."
        }
    }

    val plannedDurationMillis: Long
        get() = endsAtMillis - startsAtMillis

    /** Um ciclo padrão para cada hora planejada, arredondando para cima. */
    /** Tempo efetivo da jornada, descontadas as pausas planejadas. */
    val plannedWorkingDurationMillis: Long
        get() = (plannedDurationMillis - plannedPauseMillis).coerceAtLeast(0L)

    /** Um ciclo para cada hora efetivamente trabalhada, arredondando para cima. */
    val expectedCycleCount: Int
        get() = (
            (plannedWorkingDurationMillis + HOUR_MILLIS - 1L) /
                HOUR_MILLIS
            ).toInt()

    val plannedPauseMillis: Long
        get() = plannedPauses.sumOf { it.endsAtMillis - it.startsAtMillis }

    companion object {
        private const val HOUR_MILLIS = 60L * 60L * 1000L
    }
}
