package com.joinville.ubermetaauto

import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

/** Converte os registros de ciclos finalizados em texto para a tela inicial. */
object CycleHistoryFormatter {

    private val timeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")

    fun format(cycles: List<CycleRecord>): String {
        if (cycles.isEmpty()) return "Nenhum ciclo encerrado ainda."

        return cycles.asReversed().joinToString("\n\n") { cycle ->
            val started = formatTime(cycle.startedAtMillis)
            val ended = formatTime(cycle.finishedAtMillis)

            String.format(
                Locale("pt", "BR"),
                "Ciclo %d | %s → %s\nR$ %.2f / meta R$ %.2f (%s)\nTrabalho: %s | Vazio: %s | Pausa: %s\nCorridas concluídas: %d",
                cycle.number,
                started,
                ended,
                cycle.revenue,
                cycle.targetAmount,
                if (cycle.targetReached) "meta atingida" else "meta não atingida",
                formatDuration(cycle.workedMillis),
                formatDuration(cycle.idleMillis),
                formatDuration(cycle.pausedMillis),
                cycle.rideCount
            )
        }
    }

    private fun formatTime(millis: Long): String =
        Instant.ofEpochMilli(millis)
            .atZone(ZoneId.systemDefault())
            .format(timeFormatter)

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1_000L
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }
}
