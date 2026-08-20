package com.joinville.ubermetaauto

object DecisionEngine {

    fun decide(
        offer: RideOffer,
        targets: RideTargets
    ): RideDecision = decide(
        offer = offer,
        targetHour = targets.hourly,
        targetKm = targets.perKm
    )

    fun decide(
        offer: RideOffer,
        targetHour: Double,
        targetKm: Double
    ): RideDecision {
        if (targetHour <= 0.0 || targetKm <= 0.0) {
            return RideDecision.PASS
        }

        if (
            offer.reaisPerHour >= targetHour &&
            offer.reaisPerKm >= targetKm
        ) {
            return RideDecision.TAKE
        }

        if (
            offer.reaisPerHour >= targetHour * 0.85 &&
            offer.reaisPerKm >= targetKm * 0.85
        ) {
            return RideDecision.MAYBE
        }

        return RideDecision.PASS
    }
}
