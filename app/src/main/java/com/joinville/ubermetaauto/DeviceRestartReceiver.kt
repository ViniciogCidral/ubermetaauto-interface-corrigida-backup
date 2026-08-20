package com.joinville.ubermetaauto

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent

/** Marca que a captura precisa de nova autorização depois de reiniciar. */
class DeviceRestartReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent?) {
        if (intent?.action == Intent.ACTION_BOOT_COMPLETED) {
            AppRuntimeState.markCaptureStopped(context)
            WorkSessionManager.initialize(context)
            WorkSessionManager.end()
        }
    }
}
