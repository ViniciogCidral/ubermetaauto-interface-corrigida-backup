package com.joinville.ubermetaauto

import java.util.Locale

/** Texto do painel operacional provisório da tela inicial. */
object WorkDashboardFormatter {

    fun cycle(nowMillis: Long = System.currentTimeMillis()): String {
        val cycle = CycleManager.refresh(nowMillis)
            ?: return "CICLO\nAguardando a primeira corrida aceita."

        return String.format(
            Locale("pt", "BR"),
            "CICLO %d\nR$ %.2f / meta R$ %.2f\nRestante: %s | Corridas: %d\nTrabalho: %s | Vazio: %s | Pausa: %s",
            cycle.number,
            cycle.revenue,
            cycle.targetAmount,
            formatDuration(cycle.remainingMillis(nowMillis)),
            cycle.rideCount,
            formatDuration(cycle.workedMillis(nowMillis)),
            formatDuration(cycle.idleMillis(nowMillis)),
            formatDuration(cycle.pausedMillis(nowMillis))
        )
    }

    fun shift(nowMillis: Long = System.currentTimeMillis()): String {
        val shift = ShiftManager.current()
            ?: return "TURNO\nNenhum turno em andamento."

        return String.format(
            Locale("pt", "BR"),
            "TURNO\nFaturamento: R$ %.2f | Corridas: %d\nTempo efetivo: %s | Pausa: %s",
            shift.revenue,
            shift.completedRideIds.size,
            formatDuration(shift.workedMillis(nowMillis)),
            formatDuration(shift.pausedMillis(nowMillis))
        )
    }

    private fun formatDuration(millis: Long): String {
        val seconds = millis / 1_000L
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }
}
