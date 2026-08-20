package com.joinville.ubermetaauto

import android.app.Service
import android.content.Context
import android.content.Intent
import android.graphics.Color
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.os.IBinder
import android.view.Gravity
import android.view.MotionEvent
import android.view.View
import android.view.WindowManager
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Pequeno painel informativo sobre a tela da Uber.
 *
 * Ele nunca executa uma decisao pelo motorista: mostra o resultado e pode ser
 * arrastado, compactado e fechado. A raiz contem apenas o card; isso e
 * importante porque uma View MATCH_PARENT dentro de uma janela WRAP_CONTENT
 * faria o WindowManager medir uma area de tela inteira.
 */
class OverlayService : Service() {

    companion object {
        const val ACTION_RESULT = "com.joinville.ubermetaauto.RESULT"
        const val ACTION_HIDE = "com.joinville.ubermetaauto.HIDE"
        const val ACTION_SHOW = "com.joinville.ubermetaauto.SHOW"

        const val EXTRA_TEXT = "text"
        const val EXTRA_DECISION = "decision"
        const val EXTRA_FARE = "fare"
        const val EXTRA_TOTAL_MINUTES = "total_minutes"
        const val EXTRA_TOTAL_KM = "total_km"
        const val EXTRA_PER_KM = "per_km"
        const val EXTRA_CUSTOMER_RATING = "customer_rating"
        const val EXTRA_CYCLE = "cycle"

        private const val PREFS = "overlay"
        private const val PREF_X = "position_x"
        private const val PREF_Y = "position_y"
        private const val PREF_DOCKED = "docked"
        private const val NORMAL_WIDTH_DP = 214
        private const val DOCKED_WIDTH_DP = 118
        private const val MINIMIZED_SIZE_DP = 58
        /* So encaixa quando encosta, sem efeito de ima a distancia. */
        private const val EDGE_TOLERANCE_PX = 1
    }

    private lateinit var windowManager: WindowManager
    private lateinit var root: FrameLayout
    private lateinit var card: LinearLayout
    private lateinit var titleView: TextView
    private lateinit var fareView: TextView
    private lateinit var detailsView: TextView
    private lateinit var cycleView: TextView
    private lateinit var minimizeView: TextView
    private lateinit var closeView: TextView
    private lateinit var minimizedView: TextView

    private lateinit var layoutParams: WindowManager.LayoutParams
    private lateinit var minimizedLayoutParams: WindowManager.LayoutParams
    private var isAdded = false
    private var isMinimizedAdded = false
    private var isDocked = false
    private var currentDecision = Decision.AWAITING

    private val headlineTypeface: Typeface by lazy { resources.getFont(R.font.fraunces_variable) }
    private val monoTypeface: Typeface by lazy { resources.getFont(R.font.dm_mono_regular) }

    override fun onCreate() {
        super.onCreate()
        WorkSessionManager.initialize(this)
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        createViews()
    }

    private fun createViews() {
        root = FrameLayout(this)

        card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(14), dp(10), dp(14), dp(10))
            elevation = dp(5).toFloat()
        }
        root.addView(
            card,
            FrameLayout.LayoutParams(dp(NORMAL_WIDTH_DP), FrameLayout.LayoutParams.WRAP_CONTENT)
        )

        val header = LinearLayout(this).apply {
            gravity = Gravity.CENTER_VERTICAL
            orientation = LinearLayout.HORIZONTAL
        }
        titleView = textView(13f, bold = true, mono = true).apply {
            text = "AGUARDANDO"
            layoutParams = LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f)
        }
        minimizeView = actionIcon("−", "Minimizar") { minimizeOverlay() }
        closeView = actionIcon("×", "Fechar") {
            hideAllOverlays()
            WorkSessionManager.pause()
            CycleStatusNotifier.update(this@OverlayService)
            stopSelf()
        }
        header.addView(titleView)
        header.addView(minimizeView)
        header.addView(closeView)
        card.addView(header)

        fareView = textView(24f, bold = true).apply {
            text = "Aguardando oferta"
            setPadding(0, dp(4), 0, 0)
            typeface = headlineTypeface
        }
        detailsView = textView(9.5f, bold = false, mono = true).apply {
            text = "Aguardando oferta..."
            maxLines = 1
            setSingleLine(true)
            setPadding(0, dp(3), 0, 0)
        }
        cycleView = textView(9f, bold = false, mono = true).apply {
            maxLines = 1
            setSingleLine(true)
            setPadding(0, dp(5), 0, 0)
        }
        card.addView(fareView)
        card.addView(detailsView)
        card.addView(cycleView)

        minimizedView = textView(15f, bold = true).apply {
            text = "UMA"
            gravity = Gravity.CENTER
            typeface = headlineTypeface
            setTextColor(Color.WHITE)
            minWidth = dp(MINIMIZED_SIZE_DP)
            minHeight = dp(MINIMIZED_SIZE_DP)
            setPadding(dp(7), 0, dp(7), 0)
            contentDescription = "Abrir overlay UMA"
        }

        setDragListener(card, minimized = false)
        setDragListener(minimizedView, minimized = true)
        renderAppearance()
    }

    private fun textView(sizeSp: Float, bold: Boolean, mono: Boolean = false): TextView = TextView(this).apply {
        textSize = sizeSp
        setTextColor(Color.rgb(17, 17, 17))
        typeface = when {
            mono -> monoTypeface
            bold -> Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            else -> Typeface.DEFAULT
        }
        includeFontPadding = false
    }

    private fun actionIcon(symbol: String, description: String, action: () -> Unit): TextView =
        textView(15f, bold = false).apply {
            text = symbol
            contentDescription = description
            gravity = Gravity.CENTER
            minWidth = dp(20)
            minHeight = dp(20)
            background = rounded(Color.TRANSPARENT, Color.rgb(17, 17, 17), 1, fullyRounded = true)
            setOnClickListener { action() }
        }

    private fun setDragListener(view: View, minimized: Boolean) {
        view.setOnTouchListener(object : View.OnTouchListener {
            private var startX = 0
            private var startY = 0
            private var touchX = 0f
            private var touchY = 0f
            private var dragging = false

            override fun onTouch(target: View?, event: MotionEvent?): Boolean {
                if (event == null) return false
                val params = if (minimized) minimizedLayoutParams else layoutParams

                when (event.actionMasked) {
                    MotionEvent.ACTION_DOWN -> {
                        if (!minimized && isDocked) setDocked(false)
                        startX = params.x
                        startY = params.y
                        touchX = event.rawX
                        touchY = event.rawY
                        dragging = false
                        return true
                    }

                    MotionEvent.ACTION_MOVE -> {
                        val deltaX = (event.rawX - touchX).roundToInt()
                        val deltaY = (event.rawY - touchY).roundToInt()
                        dragging = dragging || kotlin.math.abs(deltaX) > dp(3) || kotlin.math.abs(deltaY) > dp(3)
                        moveOverlay(params, startX + deltaX, startY + deltaY, minimized)
                        return true
                    }

                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (minimized && !dragging) {
                            showOverlay()
                        } else if (!minimized && dragging && touchesRightEdge()) {
                            setDocked(true)
                        } else {
                            savePosition(params.x, params.y, docked = false)
                        }
                        return true
                    }
                }
                return false
            }
        })
    }

    private fun moveOverlay(
        params: WindowManager.LayoutParams,
        requestedX: Int,
        requestedY: Int,
        minimized: Boolean
    ) {
        val target = if (minimized) minimizedView else root
        val width = target.width.coerceAtLeast(dp(if (minimized) MINIMIZED_SIZE_DP else NORMAL_WIDTH_DP))
        val height = target.height.coerceAtLeast(dp(if (minimized) MINIMIZED_SIZE_DP else 76))
        params.x = requestedX.coerceIn(0, (screenWidth() - width).coerceAtLeast(0))
        params.y = requestedY.coerceIn(0, (screenHeight() - height).coerceAtLeast(0))

        try {
            windowManager.updateViewLayout(target, params)
        } catch (_: Throwable) {
            // O usuario pode ter fechado a janela entre o toque e a atualizacao.
        }
    }

    private fun touchesRightEdge(): Boolean =
        layoutParams.x + root.width >= screenWidth() - EDGE_TOLERANCE_PX

    private fun showOverlay() {
        if (isAdded) return

        hideMinimizedOverlay()
        isDocked = preferences().getBoolean(PREF_DOCKED, false)
        layoutParams = windowParams(
            x = preferences().getInt(PREF_X, dp(12)),
            y = preferences().getInt(PREF_Y, dp(120))
        )

        try {
            windowManager.addView(root, layoutParams)
            isAdded = true
            AppRuntimeState.setOverlayEnabled(this, true)
            root.post { setDocked(isDocked) }
        } catch (_: Throwable) {
            isAdded = false
        }
    }

    private fun hideFullOverlay() {
        if (!isAdded) return
        try {
            windowManager.removeView(root)
        } catch (_: Throwable) {
        }
        isAdded = false
    }

    private fun minimizeOverlay() {
        if (!isAdded) return
        val x = layoutParams.x
        val y = layoutParams.y
        hideFullOverlay()

        minimizedLayoutParams = windowParams(x, y)
        try {
            windowManager.addView(minimizedView, minimizedLayoutParams)
            isMinimizedAdded = true
            AppRuntimeState.setOverlayEnabled(this, true)
        } catch (_: Throwable) {
            isMinimizedAdded = false
        }
    }

    private fun hideMinimizedOverlay() {
        if (!isMinimizedAdded) return
        try {
            windowManager.removeView(minimizedView)
        } catch (_: Throwable) {
        }
        isMinimizedAdded = false
    }

    private fun hideAllOverlays() {
        hideFullOverlay()
        hideMinimizedOverlay()
        AppRuntimeState.setOverlayEnabled(this, false)
    }

    private fun setDocked(docked: Boolean) {
        isDocked = docked
        fareView.visibility = if (docked) View.GONE else View.VISIBLE
        detailsView.visibility = if (docked) View.GONE else View.VISIBLE
        cycleView.visibility = if (docked) View.GONE else View.VISIBLE
        minimizeView.visibility = if (docked) View.GONE else View.VISIBLE
        closeView.visibility = if (docked) View.GONE else View.VISIBLE
        titleView.textSize = if (docked) 12f else 13f
        card.setPadding(
            dp(if (docked) 12 else 14),
            dp(if (docked) 10 else 10),
            dp(if (docked) 12 else 14),
            dp(if (docked) 10 else 10)
        )
        (card.layoutParams as FrameLayout.LayoutParams).width = dp(if (docked) DOCKED_WIDTH_DP else NORMAL_WIDTH_DP)
        card.requestLayout()
        renderAppearance()

        if (isAdded) {
            root.post {
                layoutParams.x = if (docked) {
                    (screenWidth() - root.width).coerceAtLeast(0)
                } else {
                    layoutParams.x.coerceAtMost((screenWidth() - root.width).coerceAtLeast(0))
                }
                try {
                    windowManager.updateViewLayout(root, layoutParams)
                } catch (_: Throwable) {
                }
                savePosition(layoutParams.x, layoutParams.y, docked)
            }
        }
    }

    private fun updateResult(intent: Intent) {
        val rawDecision = intent.getStringExtra(EXTRA_DECISION)
            ?: intent.getStringExtra(EXTRA_TEXT)?.lineSequence()?.firstOrNull()
            ?: "AGUARDANDO"
        currentDecision = Decision.from(rawDecision)

        titleView.text = currentDecision.label
        val fare = intent.getDoubleExtra(EXTRA_FARE, Double.NaN)
        val minutes = intent.getDoubleExtra(EXTRA_TOTAL_MINUTES, Double.NaN)
        val km = intent.getDoubleExtra(EXTRA_TOTAL_KM, Double.NaN)
        val perKm = intent.getDoubleExtra(EXTRA_PER_KM, Double.NaN)
        val rating = intent.getDoubleExtra(EXTRA_CUSTOMER_RATING, Double.NaN)
        val cycle = intent.getStringExtra(EXTRA_CYCLE).orEmpty()

        if (!fare.isNaN()) {
            fareView.text = String.format(Locale("pt", "BR"), "R$ %.2f", fare)
            detailsView.text = buildString {
                if (!minutes.isNaN()) append(String.format(Locale("pt", "BR"), "%.0f min", minutes))
                if (!km.isNaN()) append(if (isNotEmpty()) " · " else "").append(String.format(Locale("pt", "BR"), "%.1f km", km))
                if (!perKm.isNaN()) append(if (isNotEmpty()) " · " else "").append(String.format(Locale("pt", "BR"), "R$ %.2f/km", perKm))
                if (!rating.isNaN()) append(if (isNotEmpty()) " · " else "").append(String.format(Locale("pt", "BR"), "%.2f ★", rating))
            }
            cycleView.text = cycle
        } else {
            val details = intent.getStringExtra(EXTRA_TEXT).orEmpty().lineSequence().drop(1).joinToString(" · ")
            fareView.text = "Aguardando oferta"
            detailsView.text = if (details.isBlank()) "Aguardando oferta..." else details
            cycleView.text = cycle
        }

        renderAppearance()
        if (!isAdded && !isMinimizedAdded) showOverlay()
    }

    private fun renderAppearance() {
        val color = currentDecision.color
        card.background = rounded(Color.rgb(255, 253, 249), color, 2)
        titleView.setTextColor(color)
        fareView.setTextColor(Color.rgb(17, 17, 17))
        detailsView.setTextColor(Color.rgb(58, 55, 51))
        cycleView.setTextColor(Color.rgb(80, 76, 71))
        minimizedView.background = rounded(Color.rgb(17, 17, 17), color, 3, fullyRounded = true)
    }

    private fun rounded(fill: Int, stroke: Int, strokeDp: Int, fullyRounded: Boolean = false): GradientDrawable =
        GradientDrawable().apply {
            shape = GradientDrawable.RECTANGLE
            cornerRadius = dp(if (fullyRounded) 999 else 16).toFloat()
            setColor(fill)
            if (strokeDp > 0) setStroke(dp(strokeDp), stroke)
        }

    private fun windowParams(x: Int, y: Int) = WindowManager.LayoutParams(
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.WRAP_CONTENT,
        WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
        WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
        PixelFormat.TRANSLUCENT
    ).apply {
        gravity = Gravity.TOP or Gravity.START
        this.x = x
        this.y = y
    }

    private fun savePosition(x: Int, y: Int, docked: Boolean) {
        preferences().edit()
            .putInt(PREF_X, x)
            .putInt(PREF_Y, y)
            .putBoolean(PREF_DOCKED, docked)
            .apply()
    }

    private fun preferences() = getSharedPreferences(PREFS, Context.MODE_PRIVATE)
    private fun screenWidth(): Int = resources.displayMetrics.widthPixels
    private fun screenHeight(): Int = resources.displayMetrics.heightPixels
    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        when (intent?.action) {
            ACTION_RESULT -> updateResult(intent)
            ACTION_SHOW -> showOverlay()
            ACTION_HIDE -> {
                hideAllOverlays()
                stopSelf()
            }
        }
        return START_NOT_STICKY
    }

    override fun onDestroy() {
        hideAllOverlays()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null

    private enum class Decision(val label: String, val color: Int) {
        TAKE("PEGAR", Color.rgb(36, 174, 112)),
        MAYBE("TALVEZ", Color.rgb(226, 170, 32)),
        PASS("PASSAR", Color.rgb(211, 74, 72)),
        AWAITING("AGUARDANDO", Color.rgb(17, 17, 17));

        companion object {
            fun from(value: String): Decision = when (value.trim().uppercase()) {
                "PEGAR" -> TAKE
                "TALVEZ" -> MAYBE
                "PASSAR" -> PASS
                else -> AWAITING
            }
        }
    }
}
