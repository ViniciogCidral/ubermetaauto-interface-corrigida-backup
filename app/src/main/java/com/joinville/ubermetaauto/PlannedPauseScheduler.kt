package com.joinville.ubermetaauto

import android.content.Context
import android.content.Intent
import android.os.Handler
import android.os.Looper

/**
 * Agenda a próxima pausa ou o encerramento do turno atual.
 *
 * Ele não cria turnos nem inicia captura. Apenas atua enquanto uma sessão já
 * estiver iniciada. O estado da origem da pausa fica no WorkSessionManager,
 * portanto uma pausa manual não é retomada por este agendamento.
 */
object PlannedPauseScheduler {

    private const val ACTIVE_RIDE_RECHECK_MILLIS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private val transitionRunnable = Runnable { refresh() }
    private var applicationContext: Context? = null

    @Synchronized
    fun initialize(context: Context) {
        applicationContext = context.applicationContext
    }

    @Synchronized
    fun refresh(nowMillis: Long = System.currentTimeMillis()) {
        handler.removeCallbacks(transitionRunnable)

        /*
         * O turno guarda uma cópia do planejamento usado ao começar. Isso
         * impede que uma edição feita para um turno futuro altere um turno que
         * já está em andamento.
         */
        val shift = ShiftManager.current() ?: return
        if (!WorkSessionManager.isStarted()) return

        val plannedEnd = shift.plannedEndMillis
        if (plannedEnd != null && nowMillis >= plannedEnd) {
            if (RideLifecycleManager.activeRideId() != null) {
                handler.postDelayed(transitionRunnable, ACTIVE_RIDE_RECHECK_MILLIS)
            } else {
                finishPlannedShift(nowMillis)
            }
            return
        }

        val plannedPauses = shift.plannedPauses

        val activePause = plannedPauses.firstOrNull { pause ->
            nowMillis >= pause.startsAtMillis && nowMillis < pause.endsAtMillis
        }

        val statusChanged = if (
            activePause != null &&
            RideLifecycleManager.activeRideId() == null
        ) {
            WorkSessionManager.pauseForPlanned(activePause.key, nowMillis)
        } else {
            WorkSessionManager.plannedPauseKey()?.let { pauseKey ->
                val pausedPlan = plannedPauses.firstOrNull { it.key == pauseKey }
                if (pausedPlan == null || nowMillis >= pausedPlan.endsAtMillis) {
                    WorkSessionManager.resumeFromPlanned(pauseKey, nowMillis)
                } else {
                    false
                }
            } ?: false
        }

        if (statusChanged) {
            applicationContext?.let { context ->
                CycleStatusNotifier.update(context, nowMillis)
            }
        }

        val nextTransition = (plannedPauses
            .flatMap { pause -> listOf(pause.startsAtMillis, pause.endsAtMillis) }
            + listOfNotNull(plannedEnd))
            .filter { it > nowMillis }
            .minOrNull()
            ?: return

        handler.postDelayed(
            transitionRunnable,
            (nextTransition - nowMillis).coerceAtLeast(1L)
        )
    }

    @Synchronized
    fun cancel() {
        handler.removeCallbacks(transitionRunnable)
    }

    private fun finishPlannedShift(nowMillis: Long) {
        RideLifecycleManager.confirmExpired()?.let { recordId ->
            RideHistoryManager.markExpired(recordId)
        }

        CycleManager.stop(nowMillis)
        ShiftManager.stop(nowMillis)
        WorkSessionManager.end()
        applicationContext?.let(CycleStatusNotifier::clear)
        hideOverlay()
        cancel()
    }

    private fun hideOverlay() {
        val context = applicationContext ?: return
        context.startService(
            Intent(context, OverlayService::class.java).apply {
                action = OverlayService.ACTION_HIDE
            }
        )
    }
}
