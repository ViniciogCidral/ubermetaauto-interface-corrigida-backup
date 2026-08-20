package com.joinville.ubermetaauto

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Handler
import android.os.Looper
import androidx.core.app.NotificationCompat
import java.util.Locale

/** Notificação permanente com o resumo do ciclo em andamento. */
object CycleStatusNotifier {

    /* Mesmo canal/ID do serviço de captura: o usuário vê apenas um aviso. */
    private const val CHANNEL_ID = "capture"
    private const val NOTIFICATION_ID = 17
    private const val REFRESH_INTERVAL_MILLIS = 30_000L

    private val handler = Handler(Looper.getMainLooper())
    private var applicationContext: Context? = null
    private val refreshRunnable = object : Runnable {
        override fun run() {
            val context = applicationContext ?: return
            if (!WorkSessionManager.isStarted()) return

            update(context)
            handler.postDelayed(this, REFRESH_INTERVAL_MILLIS)
        }
    }

    /** Inicia a atualização periódica do resumo exibido pelo Android. */
    fun start(context: Context) {
        val appContext = context.applicationContext
        applicationContext = appContext
        handler.removeCallbacks(refreshRunnable)
        update(appContext)
        handler.postDelayed(refreshRunnable, REFRESH_INTERVAL_MILLIS)
    }

    fun update(context: Context, nowMillis: Long = System.currentTimeMillis()) {
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL_ID,
                "Status do ciclo",
                NotificationManager.IMPORTANCE_LOW
            )
        )

        val content = if (!WorkSessionManager.isStarted()) {
            "Turno encerrado"
        } else {
            val cycle = CycleManager.refresh(nowMillis)
            when {
                WorkSessionManager.isPaused() -> {
                    cycle?.let { "Turno pausado | C${it.number}" }
                        ?: "Turno pausado"
                }

                cycle == null -> "Aguardando a primeira corrida aceita"

                else -> String.format(
                    Locale("pt", "BR"),
                    "R$ %.2f | %s | C%d",
                    cycle.revenue,
                    formatRemaining(cycle.remainingMillis(nowMillis)),
                    cycle.number
                )
            }
        }

        manager.notify(
            NOTIFICATION_ID,
            NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(android.R.drawable.ic_menu_view)
                .setContentTitle("UberMetaAuto")
                .setContentText(content)
                .setOngoing(true)
                .setOnlyAlertOnce(true)
                .build()
        )
    }

    fun clear(context: Context) {
        handler.removeCallbacks(refreshRunnable)
        applicationContext = null
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE)
            as NotificationManager
        if (AppRuntimeState.isCaptureActive(context)) {
            manager.notify(
                NOTIFICATION_ID,
                NotificationCompat.Builder(context, CHANNEL_ID)
                    .setSmallIcon(android.R.drawable.ic_menu_view)
                    .setContentTitle("UberMetaAuto")
                    .setContentText("Captura ativa · inicie um turno para analisar ofertas")
                    .setOngoing(true)
                    .setOnlyAlertOnce(true)
                    .build()
            )
        } else {
            manager.cancel(NOTIFICATION_ID)
        }
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = millis / 1000L
        val minutes = totalSeconds / 60L
        val seconds = totalSeconds % 60L
        return String.format(Locale.US, "%02d:%02d", minutes, seconds)
    }
}
