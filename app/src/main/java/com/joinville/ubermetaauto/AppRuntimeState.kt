package com.joinville.ubermetaauto

import android.content.Context

/**
 * Estado operacional que sobrevive ao fechamento da interface principal.
 *
 * O token de captura não é salvo: por segurança, o Android exige que a pessoa
 * autorize uma nova captura após reiniciar o aparelho ou o serviço parar.
 */
object AppRuntimeState {

    private const val PREFS = "runtime_state"
    private const val KEY_CAPTURE_ACTIVE = "capture_active"
    private const val KEY_CAPTURE_NEEDS_AUTHORIZATION = "capture_needs_authorization"
    private const val KEY_OVERLAY_ENABLED = "overlay_enabled"

    fun markCaptureStarted(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_CAPTURE_ACTIVE, true)
            .putBoolean(KEY_CAPTURE_NEEDS_AUTHORIZATION, false)
            .apply()
    }

    fun markCaptureStopped(context: Context) {
        preferences(context).edit()
            .putBoolean(KEY_CAPTURE_ACTIVE, false)
            .putBoolean(KEY_CAPTURE_NEEDS_AUTHORIZATION, true)
            .apply()
    }

    fun setOverlayEnabled(context: Context, enabled: Boolean) {
        preferences(context).edit()
            .putBoolean(KEY_OVERLAY_ENABLED, enabled)
            .apply()
    }

    fun isCaptureActive(context: Context): Boolean =
        preferences(context).getBoolean(KEY_CAPTURE_ACTIVE, false)

    fun statusText(context: Context): String = when {
        isCaptureActive(context) ->
            "Status: captura ativa em segundo plano."

        preferences(context).getBoolean(KEY_CAPTURE_NEEDS_AUTHORIZATION, false) ->
            "Status: autorize novamente a captura de tela."

        else ->
            "Status: pronto."
    }

    private fun preferences(context: Context) =
        context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
}
