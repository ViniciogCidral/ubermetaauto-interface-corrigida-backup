package com.joinville.ubermetaauto

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo

class UberAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "UberMetaAccessibility"
        private const val UBER_PACKAGE = "com.ubercab.driver"
        private const val ANALYZE_DELAY = 120L
        private const val MIN_ANALYZE_INTERVAL = 300L
        /* Eventos curtos do System UI nao significam que a Uber perdeu foco. */
        private const val LEAVE_CONFIRM_DELAY = 450L
    }

    private val handler =
        Handler(Looper.getMainLooper())

    private var lastAnalyze = 0L
    private var pending = false
    private var uberInForeground = false

    private val analyzeRunnable =
        Runnable {
            pending = false
            sendAnalyze()
        }

    private val confirmUberLeftRunnable = Runnable {
        if (!uberInForeground) return@Runnable
        val activePackage = rootInActiveWindow?.packageName?.toString()
        /* Janela nula e transitoria; nao escondemos o painel por ela. */
        if (!activePackage.isNullOrBlank() && activePackage != UBER_PACKAGE) {
            markUberLeftForeground()
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        RideHistoryManager.initialize(this)
        CycleManager.initialize(this)
        ShiftManager.initialize(this)
        RideLifecycleManager.initialize(this)
        WorkSessionManager.initialize(this)
        PlannedPauseScheduler.initialize(this)
        PlannedPauseScheduler.refresh()
    }

    override fun onAccessibilityEvent(
        event: AccessibilityEvent?
    ) {

        if (event == null) return

        val packageName =
            event.packageName?.toString()
                ?: return

        /*
         * Ignora tudo que não seja Uber.
         */
        if (packageName != UBER_PACKAGE) {
            if (
                uberInForeground &&
                event.eventType == AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            ) {
                /*
                 * Abertura de notificacao, permissao ou janela do proprio
                 * Android tambem emite WINDOW_STATE_CHANGED. Verificamos a
                 * janela ativa depois do evento antes de parar o OCR.
                 */
                handler.removeCallbacks(confirmUberLeftRunnable)
                handler.postDelayed(confirmUberLeftRunnable, LEAVE_CONFIRM_DELAY)
            }
            return
        }

        uberInForeground = true
        handler.removeCallbacks(confirmUberLeftRunnable)

        /*
         * DIAGNÓSTICO:
         *
         * Se esta mensagem aparecer no Logcat,
         * sabemos que a acessibilidade está recebendo
         * eventos da Uber.
         */
        Log.d(
            TAG,
            "Evento Uber recebido: ${event.eventType}"
        )

        handleRideLifecycleSignal(
            visibleText = visibleText(event),
            explicitRejection = event.eventType == AccessibilityEvent.TYPE_VIEW_CLICKED &&
                RideLifecycleSignalDetector.isExplicitRejectionAction(clickedText(event))
        )

        when (event.eventType) {

            AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED,
            AccessibilityEvent.TYPE_WINDOW_CONTENT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_TEXT_CHANGED,
            AccessibilityEvent.TYPE_VIEW_SCROLLED -> {

                scheduleAnalyze()
            }
        }
    }

    private fun scheduleAnalyze() {

        val now =
            SystemClock.uptimeMillis()

        val elapsed =
            now - lastAnalyze

        if (elapsed >= MIN_ANALYZE_INTERVAL) {

            lastAnalyze = now

            sendAnalyze()

            return
        }

        if (pending) {
            return
        }

        pending = true

        val delay =
            maxOf(
                ANALYZE_DELAY,
                MIN_ANALYZE_INTERVAL - elapsed
            )

        handler.postDelayed(
            analyzeRunnable,
            delay
        )
    }

    private fun sendAnalyze() {

        if (!uberInForeground) return

        lastAnalyze =
            SystemClock.uptimeMillis()

        Log.d(
            TAG,
            "Enviando ACTION_ANALYZE para ScreenCaptureService"
        )

        try {

            startService(
                Intent(
                    this,
                    ScreenCaptureService::class.java
                ).apply {

                    action =
                        ScreenCaptureService.ACTION_ANALYZE
                }
            )

        } catch (e: Throwable) {

            Log.e(
                TAG,
                "Erro ao iniciar ScreenCaptureService",
                e
            )
        }
    }

    /** Informa a captura que a tela atual deixou de ser a Uber Driver. */
    private fun markUberLeftForeground() {
        uberInForeground = false
        pending = false
        handler.removeCallbacks(analyzeRunnable)
        handler.removeCallbacks(confirmUberLeftRunnable)

        try {
            startService(
                Intent(
                    this,
                    ScreenCaptureService::class.java
                ).apply {
                    action = ScreenCaptureService.ACTION_UBER_LEFT
                }
            )
        } catch (e: Throwable) {
            Log.e(TAG, "Erro ao informar saida da Uber", e)
        }
    }

    private fun handleRideLifecycleSignal(
        visibleText: String,
        explicitRejection: Boolean
    ) {
        val signal = if (explicitRejection) {
            RideLifecycleSignalDetector.Signal.REJECTED
        } else {
            RideLifecycleSignalDetector.detect(visibleText)
        }

        when (signal) {
            RideLifecycleSignalDetector.Signal.ACCEPTED -> {
                /*
                 * Um evento antigo pode chegar depois de pausar/encerrar o
                 * turno. Ele nunca deve criar ciclo fora de uma sessão ativa.
                 */
                if (
                    !WorkSessionManager.canShowOffers() ||
                    ShiftManager.current() == null
                ) {
                    return
                }

                val recordId = RideLifecycleManager.confirmAccepted() ?: return
                val now = System.currentTimeMillis()
                RideHistoryManager.markStarted(recordId, now)

                val cycle = CycleManager.start(
                    hourlyTarget = Settings.targets(this).hourly,
                    startedAtMillis = now
                )

                ShiftManager.addCycle(cycle)
                CycleManager.registerRideStarted(recordId, now)
                CycleStatusNotifier.update(this, now)
            }

            RideLifecycleSignalDetector.Signal.REJECTED -> {
                RideLifecycleManager.confirmRejected()?.let { recordId ->
                    RideHistoryManager.markRejected(recordId)
                }
            }

            RideLifecycleSignalDetector.Signal.COMPLETED -> {
                val recordId = RideLifecycleManager.confirmCompleted() ?: return
                val now = System.currentTimeMillis()
                val record = RideHistoryManager.markCompleted(recordId, now)
                    ?: return

                CycleManager.registerRideCompleted(record, now)
                ShiftManager.addCompletedRide(record)
                CycleStatusNotifier.update(this, now)
                PlannedPauseScheduler.refresh(now)
            }

            RideLifecycleSignalDetector.Signal.NONE -> Unit
        }
    }

    private fun visibleText(event: AccessibilityEvent): String = buildString {
        event.text.forEach { append(it).append(' ') }
        append(event.contentDescription ?: "").append(' ')
        append(nodeText(rootInActiveWindow))
    }

    private fun clickedText(event: AccessibilityEvent): String = buildString {
        event.text.forEach { append(it).append(' ') }
        append(event.contentDescription ?: "")
    }

    private fun nodeText(root: AccessibilityNodeInfo?): String {
        if (root == null) return ""

        return try {
            buildString {
                appendNodeText(root, this, 0)
            }
        } finally {
            root.recycleForLegacyDevices()
        }
    }

    private fun appendNodeText(
        node: AccessibilityNodeInfo,
        output: StringBuilder,
        depth: Int
    ) {
        if (depth > 12 || output.length > 8_000) return

        node.text?.let { output.append(it).append(' ') }
        node.contentDescription?.let { output.append(it).append(' ') }

        for (index in 0 until node.childCount) {
            val child = node.getChild(index) ?: continue
            try {
                appendNodeText(child, output, depth + 1)
            } finally {
                child.recycleForLegacyDevices()
            }
        }
    }

    override fun onInterrupt() {

        Log.d(
            TAG,
            "AccessibilityService interrompido"
        )
    }

    @Suppress("DEPRECATION")
    private fun AccessibilityNodeInfo.recycleForLegacyDevices() {
        recycle()
    }

    override fun onDestroy() {

        handler.removeCallbacks(
            analyzeRunnable
        )
        handler.removeCallbacks(confirmUberLeftRunnable)

        pending = false
        uberInForeground = false

        Log.d(
            TAG,
            "AccessibilityService destruído"
        )

        super.onDestroy()
    }
}
