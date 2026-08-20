package com.joinville.ubermetaauto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Texto compacto, temporário, para consulta do histórico na tela inicial. */
object RideHistoryFormatter {

    private val dateTimeFormatter =
        DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun format(records: List<RideRecord>): String {
        if (records.isEmpty()) return "Histórico vazio."

        return records.joinToString("\n\n") { record ->
            val detectedAt = Instant.ofEpochMilli(record.detectedAtMillis)
                .atZone(ZoneId.systemDefault())
                .format(dateTimeFormatter)

            String.format(
                Locale("pt", "BR"),
                "%s | R$ %.2f\n%.1f min | %.2f km | R$ %.2f/h | R$ %.2f/km\n%s | %s | %s",
                detectedAt,
                record.offer.fare,
                record.totalMinutes,
                record.totalKm,
                record.reaisPerHour,
                record.reaisPerKm,
                record.type,
                record.decision,
                record.outcome
            )
        }
    }
}
