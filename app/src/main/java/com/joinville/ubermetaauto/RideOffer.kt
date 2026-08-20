package com.joinville.ubermetaauto

/**
 * Dados extraídos de uma oferta exibida pelo Uber.
 *
 * Esta classe não decide se a corrida deve ser aceita; ela mantém somente os
 * dados reconhecidos e os indicadores calculados a partir deles.
 */
data class RideOffer(
    val fare: Double,
    val pickupMinutes: Double,
    val pickupKm: Double,
    val tripMinutes: Double,
    val tripKm: Double,
    /** Modalidade reconhecida na própria oferta. */
    val rideType: RideType = RideType.UNKNOWN,
    /** Nota exibida para o cliente, quando o OCR conseguir reconhecê-la. */
    val customerRating: Double? = null
) {

    init {
        require(customerRating == null || customerRating in 0.0..5.0) {
            "A nota do cliente deve estar entre 0 e 5."
        }
    }

    val totalMinutes: Double
        get() = pickupMinutes + tripMinutes

    val totalKm: Double
        get() = pickupKm + tripKm

    val reaisPerHour: Double
        get() =
            if (totalMinutes > 0.0) {
                fare * 60.0 / totalMinutes
            } else {
                0.0
            }

    val reaisPerKm: Double
        get() =
            if (totalKm > 0.0) {
                fare / totalKm
            } else {
                0.0
            }

    /** Mantém a mesma oferta quando apenas a nota chega em leitura posterior. */
    fun hasSameTripDataAs(other: RideOffer): Boolean =
        fare == other.fare &&
            pickupMinutes == other.pickupMinutes &&
            pickupKm == other.pickupKm &&
            tripMinutes == other.tripMinutes &&
            tripKm == other.tripKm
}
