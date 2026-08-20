package com.joinville.ubermetaauto

object OcrParser {

    private val moneyRegex =
        Regex(
            """R\$\s*([0-9]+(?:[.,][0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )

    private val minKmRegex =
        Regex(
            """(\d+(?:[.,]\d+)?)\s*min(?:utos?)?\s*\(\s*([0-9]+(?:[.,][0-9]+)?)\s*km\s*\)""",
            RegexOption.IGNORE_CASE
        )

    fun parse(raw: String): RideOffer? {

        if (raw.isBlank()) return null

        val normalized =
            raw
                .replace('\n', ' ')
                .replace('\r', ' ')
                .replace(Regex("\\s+"), " ")
                .trim()

        val moneyMatch =
            moneyRegex.find(normalized)
                ?: return null

        val fare =
            parseDecimal(
                moneyMatch.groupValues[1]
            )

        if (fare <= 0.0) return null

        val pairs =
            minKmRegex
                .findAll(normalized)
                .toList()

        if (pairs.size < 2) return null

        val pickup = pairs[0]
        val trip = pairs[1]

        val pickupMinutes =
            parseDecimal(pickup.groupValues[1])

        val pickupKm =
            parseDecimal(pickup.groupValues[2])

        val tripMinutes =
            parseDecimal(trip.groupValues[1])

        val tripKm =
            parseDecimal(trip.groupValues[2])

        if (pickupMinutes <= 0.0) return null
        if (tripMinutes <= 0.0) return null
        if (pickupKm < 0.0) return null
        if (tripKm <= 0.0) return null

        if (pickupMinutes > 180.0) return null
        if (tripMinutes > 300.0) return null
        if (pickupKm > 100.0) return null
        if (tripKm > 500.0) return null

        return RideOffer(
            fare = fare,
            pickupMinutes = pickupMinutes,
            pickupKm = pickupKm,
            tripMinutes = tripMinutes,
            tripKm = tripKm
        )
    }

    private fun parseDecimal(
        value: String
    ): Double {

        val text = value.trim()

        if (text.isEmpty()) return 0.0
        return if (text.contains(",")) {
            text
                .replace(".", "")
                .replace(",", ".")
                .toDoubleOrNull()
                ?: 0.0
        } else {
            text.toDoubleOrNull() ?: 0.0
        }
    }
}
