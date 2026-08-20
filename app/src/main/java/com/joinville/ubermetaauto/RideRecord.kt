package com.joinville.ubermetaauto

import java.util.UUID

/**
 * Registro do ciclo de vida de uma corrida.
 *
 * A oferta é registrada no instante em que é detectada. Os horários de início
 * e término serão preenchidos pelas etapas futuras que reconhecerem esses
 * eventos, sem perder os dados originais da oferta.
 */
data class RideRecord(
    val id: String = UUID.randomUUID().toString(),
    val detectedAtMillis: Long,
    val offer: RideOffer,
    val type: RideType = RideType.UNKNOWN,
    val decision: RideDecision = RideDecision.UNKNOWN,
    val outcome: RideOutcome = RideOutcome.PENDING,
    val startedAtMillis: Long? = null,
    val completedAtMillis: Long? = null
) {

    val totalMinutes: Double
        get() = offer.totalMinutes

    val totalKm: Double
        get() = offer.totalKm

    val reaisPerHour: Double
        get() = offer.reaisPerHour

    val reaisPerKm: Double
        get() = offer.reaisPerKm
}
