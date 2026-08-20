package com.joinville.ubermetaauto

/** Metas ativas usadas pelo motor de decisão e pelos ciclos. */
data class RideTargets(
    val hourly: Double,
    val perKm: Double
) {
    val isValid: Boolean
        get() = hourly > 0.0 && perKm > 0.0
}
