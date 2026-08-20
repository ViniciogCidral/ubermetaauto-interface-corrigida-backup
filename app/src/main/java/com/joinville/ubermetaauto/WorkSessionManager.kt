package com.joinville.ubermetaauto

import android.content.Context

/**
 * Estado operacional entre o botão INICIAR, o overlay e a captura.
 *
 * Pausar a sessão não encerra a captura imediatamente, mas impede novas tags
 * de oferta até a pessoa retomar. Eventos de conclusão da corrida ativa seguem
 * sendo tratados pela acessibilidade.
 */
object WorkSessionManager {

    private var preferences: android.content.SharedPreferences? = null
    private var started = false
    private var paused = false
    private var pauseReason = PauseReason.NONE
    private var plannedPauseKey: String? = null

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return

        val sessionPreferences = context.applicationContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
        preferences = sessionPreferences
        started = sessionPreferences.getBoolean(KEY_STARTED, false)
        paused = sessionPreferences.getBoolean(KEY_PAUSED, false)
        pauseReason = sessionPreferences.getString(KEY_PAUSE_REASON, null)
            ?.let { saved ->
                runCatching { PauseReason.valueOf(saved) }
                    .getOrDefault(PauseReason.NONE)
            }
            ?: PauseReason.NONE
        plannedPauseKey = sessionPreferences.getString(KEY_PLANNED_PAUSE_KEY, null)

        if (!paused) {
            pauseReason = PauseReason.NONE
            plannedPauseKey = null
        }
    }

    @Synchronized
    fun begin() {
        started = true
        paused = false
        pauseReason = PauseReason.NONE
        plannedPauseKey = null
        persist()
    }

    @Synchronized
    fun pause(nowMillis: Long = System.currentTimeMillis()) {
        pauseInternal(PauseReason.MANUAL, null, nowMillis)
    }

    /** Pausa disparada pelo horário salvo no planejamento do turno. */
    @Synchronized
    fun pauseForPlanned(
        pauseKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean = pauseInternal(PauseReason.PLANNED, pauseKey, nowMillis)

    /**
     * Só retoma se a sessão ainda estiver parada exatamente por esta pausa
     * planejada. Assim, uma pausa manual nunca é retomada pelo agendamento.
     */
    @Synchronized
    fun resumeFromPlanned(
        pauseKey: String,
        nowMillis: Long = System.currentTimeMillis()
    ): Boolean {
        if (
            !started ||
            !paused ||
            pauseReason != PauseReason.PLANNED ||
            plannedPauseKey != pauseKey
        ) {
            return false
        }

        resumeInternal(nowMillis)
        return true
    }

    @Synchronized
    fun resume(nowMillis: Long = System.currentTimeMillis()) {
        if (!started || !paused) return

        resumeInternal(nowMillis)
    }

    @Synchronized
    fun isPaused(): Boolean = paused

    @Synchronized
    fun isStarted(): Boolean = started

    @Synchronized
    fun plannedPauseKey(): String? =
        if (pauseReason == PauseReason.PLANNED) plannedPauseKey else null

    @Synchronized
    fun canShowOffers(): Boolean = started && !paused

    @Synchronized
    fun end() {
        started = false
        paused = false
        pauseReason = PauseReason.NONE
        plannedPauseKey = null
        persist()
    }

    private fun pauseInternal(
        reason: PauseReason,
        pauseKey: String?,
        nowMillis: Long
    ): Boolean {
        if (!started || paused) return false

        paused = true
        pauseReason = reason
        plannedPauseKey = pauseKey
        ShiftManager.pause(nowMillis)
        CycleManager.pause(nowMillis)
        persist()
        return true
    }

    private fun resumeInternal(nowMillis: Long) {
        paused = false
        pauseReason = PauseReason.NONE
        plannedPauseKey = null
        ShiftManager.resume(nowMillis)
        CycleManager.resume(nowMillis)
        persist()
    }

    private fun persist() {
        preferences?.edit()
            ?.putBoolean(KEY_STARTED, started)
            ?.putBoolean(KEY_PAUSED, paused)
            ?.putString(KEY_PAUSE_REASON, pauseReason.name)
            ?.putString(KEY_PLANNED_PAUSE_KEY, plannedPauseKey)
            ?.apply()
    }

    private const val PREFS = "work_session"
    private const val KEY_STARTED = "started"
    private const val KEY_PAUSED = "paused"
    private const val KEY_PAUSE_REASON = "pause_reason"
    private const val KEY_PLANNED_PAUSE_KEY = "planned_pause_key"

    /* Constantes de persistência pertencem diretamente a este singleton. */

    private enum class PauseReason {
        NONE,
        MANUAL,
        PLANNED
    }
}
