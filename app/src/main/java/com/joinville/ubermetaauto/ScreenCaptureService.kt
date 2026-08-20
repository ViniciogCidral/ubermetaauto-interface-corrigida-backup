package com.joinville.ubermetaauto

import android.app.Activity
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.content.Intent
import android.graphics.Bitmap
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Handler
import android.os.IBinder
import android.os.Looper
import android.os.Build
import androidx.core.app.NotificationCompat
import java.util.Locale
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {

    companion object {

        const val EXTRA_RESULT_CODE =
            "result_code"

        const val EXTRA_DATA =
            "result_data"

        const val ACTION_ANALYZE =
            "analyze"

        const val ACTION_UBER_ENTERED =
            "uber_entered"

        const val ACTION_UBER_LEFT =
            "uber_left"

        private const val CHANNEL =
            "capture"

        private const val NOTIFICATION_ID =
            17

        /*
         * Intervalo entre análises.
         *
         * Mantemos o mesmo intervalo que já estava
         * funcionando anteriormente.
         */
        private const val RETRY_INTERVAL =
            350L

        /*
         * A interface da Uber pode mudar por alguns frames durante a chegada
         * de uma oferta. Uma janela maior evita que um OCR pontualmente vazio
         * esconda uma decisao que o motorista ainda esta lendo.
         */
        private const val OFFER_MISSING_GRACE_MILLIS =
            12_000L
    }

    private var projection: MediaProjection? =
        null

    private var display: VirtualDisplay? =
        null

    private var reader: ImageReader? =
        null

    private val mainHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val retryHandler =
        Handler(
            Looper.getMainLooper()
        )

    private val analyzing =
        AtomicBoolean(false)

    /* Acessibilidade informa se a Uber Driver está no primeiro plano. */
    private var uberIsOpen =
        false

    /*
     * Última oferta efetivamente mostrada.
     */
    private var lastOfferKey: String? =
        null

    private var lastOfferSeenAtMillis: Long? =
        null

    /*
     * Analisa continuamente somente enquanto a Uber Driver estiver em
     * primeiro plano e houver uma sessão de trabalho ativa.
     */
    private val retryRunnable =
        object : Runnable {

            override fun run() {
                if (!uberIsOpen) return

                analyzeNextFrame()

                if (uberIsOpen) {
                    retryHandler.postDelayed(
                        this,
                        RETRY_INTERVAL
                    )
                }
            }
        }

    private val projectionCallback =
        object : MediaProjection.Callback() {

            override fun onStop() {

                stopRetryLoop()

                releaseCapture()

                uberIsOpen =
                    false

                analyzing.set(false)

                lastOfferKey =
                    null

                AppRuntimeState.markCaptureStopped(this@ScreenCaptureService)
                CycleStatusNotifier.clear(this@ScreenCaptureService)
            }
        }

    override fun onCreate() {

        super.onCreate()

        RideHistoryManager.initialize(this)
        CycleManager.initialize(this)
        ShiftManager.initialize(this)
        RideLifecycleManager.initialize(this)
        WorkSessionManager.initialize(this)
        PlannedPauseScheduler.initialize(this)
        PlannedPauseScheduler.refresh()
        if (WorkSessionManager.isStarted()) {
            CycleStatusNotifier.start(this)
        }

        createChannel()

        startForeground(
            NOTIFICATION_ID,
            NotificationCompat.Builder(
                this,
                CHANNEL
            )
                .setContentTitle(
                    "UberMetaAuto"
                )
                .setContentText(
                    "Captura de tela ativa"
                )
                .setSmallIcon(
                    android.R.drawable.ic_menu_view
                )
                .setOngoing(true)
                .build()
        )
    }

    override fun onStartCommand(
        intent: Intent?,
        flags: Int,
        startId: Int
    ): Int {

        /*
         * Primeiro garante que a captura esteja criada.
         */
        if (
            projection == null &&
            intent?.hasExtra(EXTRA_DATA) == true
        ) {

            val resultCode =
                intent.getIntExtra(
                    EXTRA_RESULT_CODE,
                    Activity.RESULT_CANCELED
                )

            val data = intent.captureAuthorizationData()

            if (
                data != null &&
                resultCode == Activity.RESULT_OK
            ) {

                startProjection(
                    resultCode,
                    data
                )
            }
        }

        /* A captura só pode continuar analisando a tela atual da Uber. */
        if (projection != null && uberIsOpen) {
            startRetryLoop()
        }

        when (intent?.action) {

            ACTION_ANALYZE -> {

                uberIsOpen =
                    true

                ensureOverlayVisible()
                analyzeNextFrame()

                startRetryLoop()
            }

            ACTION_UBER_ENTERED -> {

                uberIsOpen =
                    true

                lastOfferKey =
                    null

                lastOfferSeenAtMillis =
                    null

                ensureOverlayVisible()
                analyzeNextFrame()

                startRetryLoop()
            }

            ACTION_UBER_LEFT -> {

                /* A captura pode seguir autorizada, mas fora da Uber não há OCR. */
                uberIsOpen =
                    false

                lastOfferKey =
                    null

                lastOfferSeenAtMillis =
                    null

                stopRetryLoop()

                hideOfferOverlay()
            }
        }

        return START_STICKY
    }

    private fun startRetryLoop() {

        if (!uberIsOpen) return

        retryHandler.removeCallbacks(
            retryRunnable
        )

        retryHandler.postDelayed(
            retryRunnable,
            RETRY_INTERVAL
        )
    }

    private fun stopRetryLoop() {

        retryHandler.removeCallbacks(
            retryRunnable
        )
    }

    private fun startProjection(
        resultCode: Int,
        data: Intent
    ) {

        if (projection != null) {
            return
        }

        try {

            val manager =
                getSystemService(
                    MEDIA_PROJECTION_SERVICE
                ) as MediaProjectionManager

            val mediaProjection = manager.getMediaProjection(
                resultCode,
                data
            ) ?: throw IllegalStateException("O Android nÃ£o disponibilizou a captura de tela.")
            projection = mediaProjection

            mediaProjection.registerCallback(
                projectionCallback,
                mainHandler
            )

            val metrics =
                resources.displayMetrics

            val width =
                metrics.widthPixels

            val height =
                metrics.heightPixels

            val density =
                metrics.densityDpi

            val imageReader = ImageReader.newInstance(
                width,
                height,
                android.graphics.PixelFormat.RGBA_8888,
                3
            )
            reader = imageReader

            display = mediaProjection.createVirtualDisplay(
                "UberMetaAuto",
                width,
                height,
                density,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                imageReader.surface,
                null,
                mainHandler
            ) ?: throw IllegalStateException("NÃ£o foi possÃ­vel criar a captura de tela.")

            AppRuntimeState.markCaptureStarted(this)

        } catch (_: Throwable) {

            releaseCapture()

            try {
                projection?.stop()
            } catch (_: Throwable) {
            }

            projection =
                null

            AppRuntimeState.markCaptureStopped(this)
            CycleStatusNotifier.clear(this)
        }
    }

    private fun analyzeNextFrame() {

        /* Do not inspect screenshots when Uber Driver is not foreground. */
        if (
            !uberIsOpen ||
            !WorkSessionManager.canShowOffers() ||
            !RideLifecycleManager.canReceiveOffers()
        ) {
            return
        }

        /*
         * Nunca deixa dois OCR rodarem
         * simultaneamente.
         */
        if (
            !analyzing.compareAndSet(
                false,
                true
            )
        ) {
            return
        }

        val currentReader =
            reader

        if (currentReader == null) {

            analyzing.set(false)

            return
        }

        var bitmap: Bitmap? =
            null

        try {

            /*
             * Sempre pega o frame MAIS RECENTE.
             *
             * Isso evita usar uma imagem antiga.
             */
            val image =
                currentReader.acquireLatestImage()

            if (image == null) {

                analyzing.set(false)

                return
            }

            try {

                val plane =
                    image.planes[0]

                val buffer =
                    plane.buffer

                val pixelStride =
                    plane.pixelStride

                val rowStride =
                    plane.rowStride

                val rowPadding =
                    rowStride -
                        pixelStride *
                        image.width

                val bitmapWidth =
                    image.width +
                        rowPadding /
                        pixelStride

                bitmap =
                    Bitmap.createBitmap(
                        bitmapWidth,
                        image.height,
                        Bitmap.Config.ARGB_8888
                    )

                buffer.rewind()

                bitmap.copyPixelsFromBuffer(
                    buffer
                )

            } finally {

                image.close()
            }

            val frame =
                bitmap

            if (
                frame == null ||
                frame.isRecycled
            ) {

                analyzing.set(false)

                return
            }

            OcrAnalyzer.analyze(
                frame
            ) { _, offer ->

                try {

                    /*
                     * Se ainda não encontrou uma oferta
                     * completa, simplesmente espera
                     * o próximo frame.
                     */
                    if (offer == null) {
                        expireMissingOfferIfNeeded()
                        return@analyze
                    }

                    /*
                     * A captura pode continuar ativa durante uma pausa, mas
                     * ofertas so podem chegar ao fluxo quando a sessao foi
                     * iniciada e nao esta pausada.
                     */
                    if (
                        !uberIsOpen ||
                        !WorkSessionManager.canShowOffers() ||
                        !RideLifecycleManager.canReceiveOffers()
                    ) {
                        return@analyze
                    }

                    lastOfferSeenAtMillis = System.currentTimeMillis()

                    /*
                     * Todos os dados fazem parte da chave.
                     *
                     * Portanto, se o valor mudar,
                     * a nova oferta será processada.
                     */
                    val offerKey =
                        buildOfferKey(
                            offer
                        )

                    if (
                        offerKey ==
                        lastOfferKey
                    ) {
                        return@analyze
                    }

                    lastOfferKey =
                        offerKey

                    val decision =
                        DecisionEngine.decide(
                            offer,
                            Settings.targets(this)
                        )

                    val decisionText =
                        when (decision) {

                            RideDecision.TAKE ->
                                "PEGAR"

                            RideDecision.MAYBE ->
                                "TALVEZ"

                            RideDecision.PASS ->
                                "PASSAR"

                            RideDecision.UNKNOWN ->
                                "PASSAR"
                        }

                    val record = RideHistoryManager.findPendingMatching(offer)
                        ?.let { pending ->
                            if (pending.offer == offer) {
                                pending
                            } else {
                                RideHistoryManager.enrichPendingOffer(
                                    pending.id,
                                    offer,
                                    decision
                                ) ?: pending
                            }
                        }
                        ?: RideHistoryManager.registerDetected(
                            offer = offer,
                            type = offer.rideType,
                            decision = decision
                        )

                    if (RideLifecycleManager.pendingOfferId() != record.id) {
                        expirePendingOffer()
                    }

                    RideLifecycleManager.registerOffer(record)

                    CycleStatusNotifier.update(this)

                    val cycleText = buildCycleText()
                    val resultText =
                        buildResultText(
                            decisionText,
                            offer,
                            cycleText
                        )

                    startService(
                        Intent(
                            this,
                            OverlayService::class.java
                        ).apply {

                            action =
                                OverlayService.ACTION_RESULT

                            putExtra(
                                OverlayService.EXTRA_TEXT,
                                resultText
                            )

                            putExtra(OverlayService.EXTRA_DECISION, decisionText)
                            putExtra(OverlayService.EXTRA_FARE, offer.fare)
                            putExtra(OverlayService.EXTRA_TOTAL_MINUTES, offer.totalMinutes)
                            putExtra(OverlayService.EXTRA_TOTAL_KM, offer.totalKm)
                            putExtra(OverlayService.EXTRA_PER_KM, offer.reaisPerKm)
                            putExtra(
                                OverlayService.EXTRA_CUSTOMER_RATING,
                                offer.customerRating ?: Double.NaN
                            )
                            putExtra(OverlayService.EXTRA_CYCLE, cycleText)
                        }
                    )

                } finally {

                    try {

                        if (
                            !frame.isRecycled
                        ) {
                            frame.recycle()
                        }

                    } catch (_: Throwable) {
                    }

                    analyzing.set(false)
                }
            }

            /*
             * A posse do bitmap passou para o callback.
             */
            bitmap = null

        } catch (_: Throwable) {

            try {

                bitmap?.let {

                    if (!it.isRecycled) {
                        it.recycle()
                    }
                }

            } catch (_: Throwable) {
            }

            analyzing.set(false)
        }
    }

    private fun expireMissingOfferIfNeeded(
        nowMillis: Long = System.currentTimeMillis()
    ) {
        if (!uberIsOpen || !WorkSessionManager.canShowOffers()) return

        val lastSeen = lastOfferSeenAtMillis ?: return
        if (nowMillis - lastSeen < OFFER_MISSING_GRACE_MILLIS) return

        expirePendingOffer()
        lastOfferKey = null
        lastOfferSeenAtMillis = null
        showWaitingOverlay()
    }

    /** Mantem o pequeno painel de espera sobre a Uber mesmo sem oferta lida. */
    private fun ensureOverlayVisible() {
        if (!WorkSessionManager.canShowOffers()) return
        try {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_SHOW
            })
        } catch (_: Throwable) {
            // A permissao de sobreposicao pode ter sido retirada pelo usuario.
        }
    }

    private fun showWaitingOverlay() {
        if (!WorkSessionManager.canShowOffers()) return
        try {
            startService(Intent(this, OverlayService::class.java).apply {
                action = OverlayService.ACTION_RESULT
                putExtra(OverlayService.EXTRA_DECISION, "AGUARDANDO")
                putExtra(OverlayService.EXTRA_TEXT, "AGUARDANDO\nAguardando oferta...")
            })
        } catch (_: Throwable) {
            // O painel e opcional quando a permissao de sobreposicao nao existe.
        }
    }

    private fun expirePendingOffer() {
        RideLifecycleManager.confirmExpired()?.let { recordId ->
            RideHistoryManager.markExpired(recordId)
        }
    }

    private fun hideOfferOverlay() {
        startService(
            Intent(
                this,
                OverlayService::class.java
            ).apply {
                action = OverlayService.ACTION_HIDE
            }
        )
    }

    private fun buildOfferKey(
        offer: RideOffer
    ): String {

        return String.format(
            Locale.US,

            "%.2f|%.1f|%.2f|%.1f|%.2f|%s|%s",

            offer.fare,

            offer.pickupMinutes,

            offer.pickupKm,

            offer.tripMinutes,

            offer.tripKm,

            offer.rideType.name,

            offer.customerRating?.let { rating ->
                String.format(Locale.US, "%.2f", rating)
            } ?: "-"
        )
    }

    private fun buildResultText(
        decision: String,
        offer: RideOffer,
        cycleText: String
    ): String {
        return String.format(
            Locale.US,

            "%s\nR$ %.2f | %.1f min | %.2f km\n%.2f R$/h | %.2f R$/km\n%s\n%s",

            decision,

            offer.fare,

            offer.totalMinutes,

            offer.totalKm,

            offer.reaisPerHour,

            offer.reaisPerKm,

            offer.customerRating?.let { rating ->
                String.format(Locale("pt", "BR"), "%.2f ★", rating)
            } ?: "Nota indisponível",

            cycleText
        )
    }

    private fun buildCycleText(
        now: Long = System.currentTimeMillis()
    ): String {
        val cycle = CycleManager.current()
        return if (cycle == null) {
            "Ciclo não iniciado"
        } else {
            String.format(
                Locale.US,
                "C%d | R$ %.2f / meta R$ %.2f | %s",
                cycle.number,
                cycle.revenue,
                cycle.targetAmount,
                formatRemaining(cycle.remainingMillis(now))
            )
        }
    }

    private fun formatRemaining(millis: Long): String {
        val totalSeconds = millis / 1000L
        return String.format(
            Locale.US,
            "%02d:%02d",
            totalSeconds / 60L,
            totalSeconds % 60L
        )
    }

    private fun releaseCapture() {

        try {
            display?.release()
        } catch (_: Throwable) {
        }

        display =
            null

        try {
            reader?.close()
        } catch (_: Throwable) {
        }

        reader =
            null
    }

    private fun createChannel() {

        val manager =
            getSystemService(
                NOTIFICATION_SERVICE
            ) as NotificationManager

        manager.createNotificationChannel(
            NotificationChannel(
                CHANNEL,
                "Captura de tela",
                NotificationManager.IMPORTANCE_LOW
            )
        )
    }

    private fun Intent.captureAuthorizationData(): Intent? =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            getParcelableExtra(EXTRA_DATA, Intent::class.java)
        } else {
            legacyCaptureAuthorizationData()
        }

    @Suppress("DEPRECATION")
    private fun Intent.legacyCaptureAuthorizationData(): Intent? =
        getParcelableExtra(EXTRA_DATA)

    override fun onDestroy() {

        stopRetryLoop()

        mainHandler.removeCallbacksAndMessages(
            null
        )

        retryHandler.removeCallbacksAndMessages(
            null
        )

        try {

            projection?.unregisterCallback(
                projectionCallback
            )

        } catch (_: Throwable) {
        }

        releaseCapture()

        try {
            projection?.stop()
        } catch (_: Throwable) {
        }

        projection =
            null

        AppRuntimeState.markCaptureStopped(this)
        CycleStatusNotifier.clear(this)

        uberIsOpen =
            false

        analyzing.set(false)

        lastOfferKey =
            null

        super.onDestroy()
    }

    override fun onBind(
        intent: Intent?
    ): IBinder? {
        return null
    }
}
