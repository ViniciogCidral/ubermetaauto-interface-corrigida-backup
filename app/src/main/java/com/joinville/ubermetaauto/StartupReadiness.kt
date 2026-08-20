package com.joinville.ubermetaauto

import android.content.Context

/** Motivos possíveis para o aplicativo ainda não poder iniciar a captura. */
enum class StartupBlocker {
    SHIFT_NOT_PLANNED,
    OVERLAY_PERMISSION_MISSING,
    ACCESSIBILITY_PERMISSION_MISSING,
    NOTIFICATION_PERMISSION_MISSING
}

/** Resultado da checagem feita antes do botão principal INICIAR agir. */
data class StartupReadiness(
    val blockers: Set<StartupBlocker>
) {
    val canRequestCaptureAuthorization: Boolean
        get() = blockers.isEmpty()

    fun userMessage(): String = when {
        StartupBlocker.SHIFT_NOT_PLANNED in blockers ->
            "Planeje o turno antes de iniciar."

        StartupBlocker.OVERLAY_PERMISSION_MISSING in blockers ->
            "Autorize a sobreposição antes de iniciar."

        StartupBlocker.ACCESSIBILITY_PERMISSION_MISSING in blockers ->
            "Ative a acessibilidade antes de iniciar."

        StartupBlocker.NOTIFICATION_PERMISSION_MISSING in blockers ->
            "Autorize as notificações antes de iniciar."

        else ->
            "Pronto para solicitar a autorização de captura de tela."
    }
}

/**
 * Camada única para a futura ação INICIAR da interface.
 *
 * A captura em si não pode ser considerada autorizada antecipadamente: o
 * Android sempre mostra o diálogo de MediaProjection no momento da partida.
 */
object StartupCoordinator {

    fun readiness(context: Context): StartupReadiness {
        val permissions = PermissionStatusReader.read(context)
        val blockers = buildSet {
            if (ShiftManager.plan() == null) add(StartupBlocker.SHIFT_NOT_PLANNED)
            if (!permissions.overlayGranted) add(StartupBlocker.OVERLAY_PERMISSION_MISSING)
            if (!permissions.accessibilityEnabled) add(StartupBlocker.ACCESSIBILITY_PERMISSION_MISSING)
            if (!permissions.notificationsGranted) add(StartupBlocker.NOTIFICATION_PERMISSION_MISSING)
        }

        return StartupReadiness(blockers)
    }
}
