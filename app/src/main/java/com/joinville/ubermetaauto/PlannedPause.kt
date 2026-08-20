package com.joinville.ubermetaauto

/** Pausa prevista dentro do planejamento de um turno. */
data class PlannedPause(
    val startsAtMillis: Long,
    val endsAtMillis: Long
) {
    init {
        require(endsAtMillis > startsAtMillis) {
            "O fim da pausa planejada deve ser posterior ao início."
        }
    }

    /** Identificador estável para distinguir esta pausa das pausas manuais. */
    val key: String
        get() = "$startsAtMillis:$endsAtMillis"
}
