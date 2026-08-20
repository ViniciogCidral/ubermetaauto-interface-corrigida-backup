package com.joinville.ubermetaauto

import android.content.Context
import android.os.Build
import android.provider.Settings

/** Leitura centralizada do estado das permissões necessárias ao app. */
data class PermissionStatus(
    val overlayGranted: Boolean,
    val accessibilityEnabled: Boolean,
    val notificationsGranted: Boolean,
    val captureActive: Boolean
) {
    fun displayText(): String = buildString {
        append("SISTEMA\n")
        append(mark(captureActive)).append(" Captura de tela\n")
        append(mark(overlayGranted)).append(" Sobreposição\n")
        append(mark(accessibilityEnabled)).append(" Acessibilidade\n")
        append(mark(notificationsGranted)).append(" Notificações\n\n")
        append(
            if (overlayGranted && accessibilityEnabled) {
                "Sistema pronto para análise."
            } else {
                "Conceda as permissões pendentes para analisar corridas."
            }
        )
    }

    private fun mark(granted: Boolean): String = if (granted) "✓" else "○"
}

object PermissionStatusReader {

    fun read(context: Context): PermissionStatus = PermissionStatus(
        overlayGranted = Settings.canDrawOverlays(context),
        accessibilityEnabled = isAccessibilityEnabled(context),
        notificationsGranted = notificationsGranted(context),
        captureActive = AppRuntimeState.isCaptureActive(context)
    )

    private fun notificationsGranted(context: Context): Boolean =
        Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU ||
            context.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED

    private fun isAccessibilityEnabled(context: Context): Boolean {
        val enabledServices = Settings.Secure.getString(
            context.contentResolver,
            Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
        ) ?: return false

        val fullName = UberAccessibilityService::class.java.name
        val shortName = ".${UberAccessibilityService::class.java.simpleName}"

        return enabledServices.split(':').any { component ->
            component.equals("${context.packageName}/$fullName", ignoreCase = true) ||
                component.equals("${context.packageName}/$shortName", ignoreCase = true)
        }
    }
}
