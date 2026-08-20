package com.joinville.ubermetaauto

import android.app.Activity
import android.app.AlertDialog
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.provider.Settings as AndroidSettings
import android.text.InputType
import android.view.Gravity
import android.view.Menu
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.LinearLayout
import android.widget.PopupMenu
import android.widget.TextView
import androidx.appcompat.app.AppCompatActivity
import com.joinville.ubermetaauto.databinding.ActivityMainBinding
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Calendar
import java.util.Locale
import kotlin.math.roundToInt

/** Tela principal final, conectada à lógica do turno e sem comandos ao Uber. */
class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding

    private val requestCapture = 501
    private var startAfterCaptureAuthorization = false
    private var selectedHistoryFilter = RideHistoryFilter(period = HistoryPeriod.TODAY)
    private var statusOverride: String? = null

    private val dashboardHandler = Handler(Looper.getMainLooper())
    private val dashboardRunnable = object : Runnable {
        override fun run() {
            renderScreen()
            dashboardHandler.postDelayed(this, DASHBOARD_REFRESH_MILLIS)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        RideHistoryManager.initialize(this)
        CycleManager.initialize(this)
        ShiftManager.initialize(this)
        RideLifecycleManager.initialize(this)
        WorkSessionManager.initialize(this)
        PlannedPauseScheduler.initialize(this)
        PlannedPauseScheduler.refresh()

        setupInteractions()
        requestNotificationPermissionIfNeeded()
        showPanel(Panel.TODAY)
        renderScreen()
    }

    override fun onResume() {
        super.onResume()
        PlannedPauseScheduler.refresh()
        dashboardHandler.removeCallbacks(dashboardRunnable)
        dashboardRunnable.run()
    }

    override fun onPause() {
        dashboardHandler.removeCallbacks(dashboardRunnable)
        super.onPause()
    }

    private fun setupInteractions() = with(binding) {
        btnMenu.setOnClickListener { showMenu() }
        btnPlanShortcut.setOnClickListener { showShiftPlanDialog() }
        btnPlanShift.setOnClickListener { showShiftPlanDialog() }
        btnEditGoals.setOnClickListener { showGoalsDialog() }
        btnPermissions.setOnClickListener { showPermissionsDialog() }

        tabToday.setOnClickListener { showPanel(Panel.TODAY) }
        tabHistory.setOnClickListener { showPanel(Panel.HISTORY) }
        tabData.setOnClickListener { showPanel(Panel.DATA) }

        btnStartSession.setOnClickListener { handleMainAction() }

        filterToday.setOnClickListener { selectHistory(HistoryPeriod.TODAY, "hoje") }
        filterYesterday.setOnClickListener { selectHistory(HistoryPeriod.YESTERDAY, "ontem") }
        filterMonth.setOnClickListener { selectHistory(HistoryPeriod.MONTH, "este mês") }
        filter3Months.setOnClickListener { selectHistory(HistoryPeriod.THREE_MONTHS, "últimos 3 meses") }
        filter6Months.setOnClickListener { selectHistory(HistoryPeriod.SIX_MONTHS, "últimos 6 meses") }
        filterAll.setOnClickListener { selectHistory(HistoryPeriod.ALL, "todo o período") }
        filterCustom.setOnClickListener { selectCustomHistoryPeriod() }
    }

    private fun showMenu() {
        PopupMenu(this, binding.btnMenu).apply {
            menu.add(Menu.NONE, MENU_PLAN, Menu.NONE, "Planejar turno")
            menu.add(Menu.NONE, MENU_GOALS, Menu.NONE, "Metas")
            menu.add(Menu.NONE, MENU_PERMISSIONS, Menu.NONE, "Permissões")
            if (WorkSessionManager.isStarted()) {
                menu.add(Menu.NONE, MENU_END_SHIFT, Menu.NONE, "Encerrar turno")
            }
            setOnMenuItemClickListener { item ->
                when (item.itemId) {
                    MENU_PLAN -> showShiftPlanDialog()
                    MENU_GOALS -> showGoalsDialog()
                    MENU_PERMISSIONS -> showPermissionsDialog()
                    MENU_END_SHIFT -> endCurrentShift(manual = true)
                }
                true
            }
            show()
        }
    }

    private fun handleMainAction() {
        when {
            !WorkSessionManager.isStarted() -> requestWorkSessionStart()
            WorkSessionManager.isPaused() -> resumeSession()
            else -> pauseSession()
        }
    }

    private fun requestWorkSessionStart() {
        val readiness = StartupCoordinator.readiness(this)
        if (!readiness.canRequestCaptureAuthorization) {
            statusOverride = readiness.userMessage()
            renderScreen()
            return
        }

        if (AppRuntimeState.isCaptureActive(this)) {
            activatePlannedWorkSession()
        } else {
            startAfterCaptureAuthorization = true
            requestScreenCaptureAuthorization()
        }
    }

    private fun activatePlannedWorkSession() {
        val shift = ShiftManager.startPlanned()
        if (shift == null) {
            statusOverride = "Planeje o turno antes de iniciar."
            renderScreen()
            return
        }

        WorkSessionManager.begin()
        PlannedPauseScheduler.refresh()
        CycleStatusNotifier.start(this)
        showOverlay()
        statusOverride = "Turno iniciado. O ciclo começa na primeira corrida aceita."
        renderScreen()
    }

    private fun pauseSession() {
        WorkSessionManager.pause()
        CycleStatusNotifier.update(this)
        statusOverride = "Turno pausado. Nenhuma nova oferta será analisada."
        renderScreen()
    }

    private fun resumeSession() {
        WorkSessionManager.resume()
        PlannedPauseScheduler.refresh()
        CycleStatusNotifier.update(this)
        showOverlay()
        statusOverride = "Turno retomado. Overlay reaberto."
        renderScreen()
    }

    private fun endCurrentShift(manual: Boolean) {
        val now = System.currentTimeMillis()
        RideLifecycleManager.confirmExpired()?.let(RideHistoryManager::markExpired)
        CycleManager.stop(now)
        ShiftManager.stop(now)
        WorkSessionManager.end()
        PlannedPauseScheduler.cancel()
        CycleStatusNotifier.clear(this)
        hideOverlay()
        statusOverride = if (manual) "Turno encerrado." else "Turno encerrado no horário planejado."
        renderScreen()
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)

        if (requestCode != requestCapture) return

        if (resultCode != Activity.RESULT_OK || data == null) {
            startAfterCaptureAuthorization = false
            statusOverride = "Captura não autorizada; o turno não foi iniciado."
            renderScreen()
            return
        }

        startForegroundService(
            Intent(this, ScreenCaptureService::class.java)
                .putExtra(ScreenCaptureService.EXTRA_RESULT_CODE, resultCode)
                .putExtra(ScreenCaptureService.EXTRA_DATA, data)
        )

        if (startAfterCaptureAuthorization) {
            startAfterCaptureAuthorization = false
            activatePlannedWorkSession()
        } else {
            statusOverride = "Captura ativa. Inicie um turno para analisar ofertas."
            renderScreen()
        }
    }

    private fun requestScreenCaptureAuthorization() {
        val manager = getSystemService(MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
        startActivityForResult(manager.createScreenCaptureIntent(), requestCapture)
    }

    private fun showOverlay() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_SHOW
        })
    }

    private fun hideOverlay() {
        startService(Intent(this, OverlayService::class.java).apply {
            action = OverlayService.ACTION_HIDE
        })
    }

    private fun showGoalsDialog() {
        val layout = dialogLayout()
        val hourly = dialogInput("Meta por hora (R$)", formatMoney(Settings.metaHora(this)))
        val perKm = dialogInput("Meta por km (R$)", formatMoney(Settings.metaKm(this)))
        layout.addView(hourly)
        layout.addView(perKm)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Suas metas")
            .setView(layout)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar") { _, _ ->
                val hourValue = parseNumber(hourly.text.toString())
                val kmValue = parseNumber(perKm.text.toString())
                if (hourValue == null || kmValue == null || hourValue <= 0.0 || kmValue <= 0.0) {
                    statusOverride = "Informe metas maiores que zero."
                } else {
                    Settings.save(this, hourValue, kmValue)
                    statusOverride = "Metas salvas."
                }
                renderScreen()
            }
            .create()
        showUmaDialog(dialog)
    }

    private fun showShiftPlanDialog() {
        // O turno ativo guarda uma cópia do plano (inclusive as pausas). Alterar
        // somente a preferência neste momento deixaria a tela e o agendador em
        // desacordo; a alteração fica disponível novamente ao encerrar o turno.
        if (WorkSessionManager.isStarted()) {
            statusOverride = "Encerre o turno atual antes de alterar o planejamento."
            renderScreen()
            return
        }

        val plan = ShiftManager.plan()
        val start = timeChoice("Começar às", plan?.startsAtMillis ?: timeToday(6, 0))
        val end = timeChoice("Terminar às", plan?.endsAtMillis ?: timeToday(22, 0))
        val pause = plan?.plannedPauses?.firstOrNull()
        val pauseStart = timeChoice("Pausa começa", pause?.startsAtMillis ?: timeToday(12, 0))
        val pauseEnd = timeChoice("Pausa termina", pause?.endsAtMillis ?: timeToday(13, 0))

        val layout = dialogLayout()
        layout.addView(start.view)
        layout.addView(end.view)
        layout.addView(pauseStart.view)
        layout.addView(pauseEnd.view)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Planejar turno")
            .setMessage("Use a mesma hora de início e fim da pausa para não programar pausa.")
            .setView(layout)
            .setNegativeButton("Cancelar", null)
            .setPositiveButton("Salvar turno") { _, _ ->
                runCatching {
                    buildShiftPlan(start, end, pauseStart, pauseEnd)
                }.onSuccess { savedPlan ->
                    ShiftManager.savePlan(savedPlan)
                    PlannedPauseScheduler.refresh()
                    statusOverride = "Turno salvo: ${savedPlan.expectedCycleCount} ciclos efetivos."
                }.onFailure { error ->
                    statusOverride = error.message ?: "Não foi possível salvar esse turno."
                }
                renderScreen()
            }
            .create()
        showUmaDialog(dialog)
    }

    private fun showPermissionsDialog() {
        val layout = dialogLayout()
        val permissionStatus = PermissionStatusReader.read(this)
        layout.addView(dialogText(permissionStatus.displayText()))

        val overlay = dialogAction("Autorizar sobreposição") {
            startActivity(Intent(AndroidSettings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
        }
        val accessibility = dialogAction("Abrir acessibilidade") {
            startActivity(Intent(AndroidSettings.ACTION_ACCESSIBILITY_SETTINGS))
        }
        val notifications = dialogAction("Autorizar notificações") {
            requestNotificationPermissionIfNeeded()
        }
        layout.addView(overlay)
        layout.addView(accessibility)
        layout.addView(notifications)

        val dialog = AlertDialog.Builder(this)
            .setTitle("Permissões")
            .setView(layout)
            .setPositiveButton("Fechar", null)
            .create()
        showUmaDialog(dialog)
    }

    private fun selectHistory(period: HistoryPeriod, label: String) {
        selectedHistoryFilter = RideHistoryFilter(period = period)
        binding.historyPeriodLabel.text = label
        renderHistory()
    }

    private fun selectCustomHistoryPeriod() {
        val today = Calendar.getInstance()
        DatePickerDialog(this, { _, year, month, day ->
            val start = LocalDate.of(year, month + 1, day)
            DatePickerDialog(this, { _, endYear, endMonth, endDay ->
                val end = LocalDate.of(endYear, endMonth + 1, endDay)
                if (end.isBefore(start)) {
                    statusOverride = "A data final deve ser posterior à inicial."
                    renderScreen()
                } else {
                    selectedHistoryFilter = RideHistoryFilter(
                        period = HistoryPeriod.CUSTOM,
                        customStartDate = start,
                        customEndDate = end
                    )
                    binding.historyPeriodLabel.text = "${formatDate(start)}–${formatDate(end)}"
                    renderHistory()
                }
            }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
        }, today.get(Calendar.YEAR), today.get(Calendar.MONTH), today.get(Calendar.DAY_OF_MONTH)).show()
    }

    private fun showPanel(panel: Panel) = with(binding) {
        panelToday.visibility = if (panel == Panel.TODAY) View.VISIBLE else View.GONE
        panelHistory.visibility = if (panel == Panel.HISTORY) View.VISIBLE else View.GONE
        panelData.visibility = if (panel == Panel.DATA) View.VISIBLE else View.GONE

        tabToday.setTextColor(getColor(if (panel == Panel.TODAY) R.color.uma_ink else R.color.uma_muted))
        tabHistory.setTextColor(getColor(if (panel == Panel.HISTORY) R.color.uma_ink else R.color.uma_muted))
        tabData.setTextColor(getColor(if (panel == Panel.DATA) R.color.uma_ink else R.color.uma_muted))
        renderScreen()
    }

    private fun renderScreen() {
        renderDashboard()
        renderHistory()
        renderData()
    }

    private fun renderDashboard() = with(binding) {
        val now = System.currentTimeMillis()
        val started = WorkSessionManager.isStarted()
        val paused = WorkSessionManager.isPaused()
        val cycle = if (started) CycleManager.refresh(now) else CycleManager.current()
        val shift = ShiftManager.current()
        val plan = ShiftManager.plan()

        metricHour.text = "R$ ${formatNumber(Settings.metaHora(this@MainActivity))}"
        metricKm.text = "R$ ${formatNumber(Settings.metaKm(this@MainActivity))}"
        metricRides.text = shift?.completedRideIds?.size?.toString() ?: "0"

        when {
            !started -> {
                heroCycleLabel.text = "CICLO 1 · AGUARDANDO INÍCIO"
                heroRevenue.text = "R$ 0,00"
                heroCycleMessage.text = "planeje seu turno para começar"
                heroTimer.text = "--:--"
                btnStartSession.text = "▶  INICIAR"
                setProgress(0f)
            }

            paused -> {
                heroCycleLabel.text = cycle?.let { "CICLO ${it.number} · PAUSADO" } ?: "TURNO PAUSADO"
                heroRevenue.text = "R$ ${formatNumber(cycle?.revenue ?: shift?.revenue ?: 0.0)}"
                heroCycleMessage.text = "captura ativa, ofertas suspensas"
                heroTimer.text = cycle?.remainingMillis(now)?.let(::formatDuration) ?: "--:--"
                btnStartSession.text = "▶  RETOMAR"
                setProgress(cycleProgress(cycle, now))
            }

            cycle == null -> {
                heroCycleLabel.text = "CICLO 1 · AGUARDANDO PRIMEIRA CORRIDA"
                heroRevenue.text = "R$ ${formatNumber(shift?.revenue ?: 0.0)}"
                heroCycleMessage.text = "captura ativa · esperando corrida aceita"
                heroTimer.text = "--:--"
                btnStartSession.text = "Ⅱ  PAUSAR"
                setProgress(0f)
            }

            else -> {
                heroCycleLabel.text = "CICLO ${cycle.number} · EM ANDAMENTO"
                heroRevenue.text = "R$ ${formatNumber(cycle.revenue)}"
                heroCycleMessage.text = "${cycle.rideCount} corrida(s) concluída(s)"
                heroTimer.text = formatDuration(cycle.remainingMillis(now))
                btnStartSession.text = "Ⅱ  PAUSAR"
                setProgress(cycleProgress(cycle, now))
            }
        }

        shiftPlan.text = when {
            shift != null && shift.plannedStartMillis != null && shift.plannedEndMillis != null -> {
                val total = shift.plannedEndMillis - shift.plannedStartMillis
                val pauses = shift.plannedPauses.sumOf { it.endsAtMillis - it.startsAtMillis }
                "PLANEJADO · ${formatClock(shift.plannedStartMillis)}–${formatClock(shift.plannedEndMillis)} · " +
                    "${cyclesFor(total - pauses)} CICLOS\n" +
                    "trabalhado ${formatDuration(shift.workedMillis(now))} · pausas ${formatDuration(shift.pausedMillis(now))}"
            }

            plan != null -> "PLANEJADO · ${formatClock(plan.startsAtMillis)}–${formatClock(plan.endsAtMillis)} · ${plan.expectedCycleCount} CICLOS"
            else -> "TURNO AINDA NÃO PLANEJADO"
        }

        renderLastRide()
        status.text = statusOverride ?: defaultStatus()
    }

    private fun renderLastRide() = with(binding) {
        val record = RideHistoryManager.all().asReversed()
            .firstOrNull { it.outcome == RideOutcome.COMPLETED }

        if (record == null) {
            lastRideTitle.text = "Nenhuma corrida concluída"
            lastRideDetails.text = "Os dados aparecerão depois da primeira corrida."
            return
        }

        val duration = if (record.startedAtMillis != null && record.completedAtMillis != null) {
            "${formatDuration(record.completedAtMillis - record.startedAtMillis)} real"
        } else {
            "${formatNumber(record.offer.totalMinutes)} min estimados"
        }
        val rating = record.offer.customerRating?.let { " · ${formatNumber(it)} ★" } ?: ""
        lastRideTitle.text = "${rideTypeLabel(record.type)} · R$ ${formatNumber(record.offer.fare)}"
        lastRideDetails.text = "${formatNumber(record.offer.pickupKm)} km até o cliente · " +
            "$duration · ${formatNumber(record.offer.totalKm)} km estimados · " +
            "R$ ${formatNumber(record.reaisPerKm)}/km$rating"
    }

    private fun renderHistory() {
        val records = RideHistoryManager.query(selectedHistoryFilter)
        binding.historyList.text = if (records.isEmpty()) {
            "Nenhuma oferta neste período."
        } else {
            records.joinToString("\n\n") { record ->
                val rating = record.offer.customerRating?.let { " · ${formatNumber(it)} ★" } ?: ""
                "${formatDateTime(record.detectedAtMillis)} · ${outcomeLabel(record.outcome)}\n" +
                    "${rideTypeLabel(record.type)} · R$ ${formatNumber(record.offer.fare)} · " +
                    "${formatNumber(record.offer.pickupKm)} km até o cliente\n" +
                    "${formatNumber(record.offer.totalMinutes)} min · ${formatNumber(record.offer.totalKm)} km · " +
                    "R$ ${formatNumber(record.reaisPerKm)}/km$rating"
            }
        }
    }

    private fun renderData() = with(binding) {
        val plan = ShiftManager.plan()
        dataMetaHour.text = "Meta por hora · R$ ${formatNumber(Settings.metaHora(this@MainActivity))}"
        dataMetaKm.text = "Meta por quilômetro · R$ ${formatNumber(Settings.metaKm(this@MainActivity))}"
        dataShift.text = plan?.let {
            "Turno planejado · ${formatClock(it.startsAtMillis)}–${formatClock(it.endsAtMillis)} · " +
                "${it.expectedCycleCount} ciclos efetivos"
        } ?: "Turno · ainda não planejado"
        dataDetails.text = StatisticsFormatter.current()
    }

    private fun setProgress(progress: Float) {
        binding.heroProgressFrame.post {
            val params = binding.heroProgressFill.layoutParams
            params.width = (binding.heroProgressFrame.width * progress.coerceIn(0f, 1f)).roundToInt()
            binding.heroProgressFill.layoutParams = params
        }
    }

    private fun cycleProgress(cycle: WorkCycle?, nowMillis: Long): Float {
        if (cycle == null || cycle.durationMillis <= 0L) return 0f
        return 1f - cycle.remainingMillis(nowMillis).toFloat() / cycle.durationMillis.toFloat()
    }

    private fun buildShiftPlan(
        start: TimeChoice,
        end: TimeChoice,
        pauseStart: TimeChoice,
        pauseEnd: TimeChoice
    ): ShiftPlan {
        val startMillis = timeToday(start.hour, start.minute)
        var endMillis = timeToday(end.hour, end.minute)
        if (endMillis <= startMillis) endMillis += DAY_MILLIS

        var pauseStartMillis = timeToday(pauseStart.hour, pauseStart.minute)
        while (pauseStartMillis < startMillis) pauseStartMillis += DAY_MILLIS
        var pauseEndMillis = timeToday(pauseEnd.hour, pauseEnd.minute)
        while (pauseEndMillis < pauseStartMillis) pauseEndMillis += DAY_MILLIS

        val pauses = if (pauseStart.hour == pauseEnd.hour && pauseStart.minute == pauseEnd.minute) {
            emptyList()
        } else {
            listOf(PlannedPause(pauseStartMillis, pauseEndMillis))
        }
        return ShiftPlan(startMillis, endMillis, pauses)
    }

    private fun timeChoice(label: String, millis: Long): TimeChoice {
        val calendar = Calendar.getInstance().apply { timeInMillis = millis }
        val choice = TimeChoice(
            label = label,
            hour = calendar.get(Calendar.HOUR_OF_DAY),
            minute = calendar.get(Calendar.MINUTE)
        )
        choice.view = dialogAction("") {
            TimePickerDialog(
                this,
                { _, hour, minute ->
                    choice.hour = hour
                    choice.minute = minute
                    renderTimeChoice(choice)
                },
                choice.hour,
                choice.minute,
                true
            ).show()
        }
        renderTimeChoice(choice)
        return choice
    }

    private fun dialogLayout() = LinearLayout(this).apply {
        orientation = LinearLayout.VERTICAL
        setPadding(dp(8), dp(4), dp(8), dp(4))
    }

    private fun dialogText(text: String) = TextView(this).apply {
        this.text = text
        textSize = 14f
        setTextColor(getColor(R.color.uma_ink))
        typeface = resources.getFont(R.font.dm_sans_variable)
        setPadding(0, dp(8), 0, dp(4))
    }

    private fun dialogInput(hint: String, value: String) = EditText(this).apply {
        this.hint = hint
        setText(value)
        inputType = InputType.TYPE_CLASS_NUMBER or InputType.TYPE_NUMBER_FLAG_DECIMAL
        typeface = resources.getFont(R.font.dm_sans_variable)
        textSize = 15f
        setPadding(dp(14), dp(10), dp(14), dp(10))
        setBackgroundResource(R.drawable.bg_button_outline)
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        )
    }

    private fun dialogAction(label: String, action: () -> Unit) = TextView(this).apply {
        text = label
        gravity = Gravity.CENTER
        textSize = 11f
        setTextColor(getColor(R.color.uma_ink))
        typeface = resources.getFont(R.font.dm_mono_regular)
        setPadding(dp(8), dp(12), dp(8), dp(12))
        setBackgroundResource(R.drawable.bg_button_outline)
        setOnClickListener { action() }
        layoutParams = LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ).apply { topMargin = dp(8) }
    }

    private fun showUmaDialog(dialog: AlertDialog) {
        dialog.setOnShowListener {
            dialog.window?.setBackgroundDrawableResource(R.drawable.bg_dialog)
            val titleId = resources.getIdentifier("alertTitle", "id", "android")
            dialog.findViewById<TextView>(titleId)?.apply {
                typeface = resources.getFont(R.font.fraunces_variable)
                textSize = 26f
            }
            listOf(
                AlertDialog.BUTTON_POSITIVE,
                AlertDialog.BUTTON_NEGATIVE,
                AlertDialog.BUTTON_NEUTRAL
            ).forEach { which ->
                dialog.getButton(which)?.apply {
                    typeface = resources.getFont(R.font.dm_mono_regular)
                    textSize = 10f
                    setTextColor(getColor(R.color.uma_ink))
                }
            }
        }
        dialog.show()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (
            Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            requestPermissions(arrayOf(android.Manifest.permission.POST_NOTIFICATIONS), REQUEST_NOTIFICATIONS)
        }
    }

    private fun defaultStatus(): String = when {
        WorkSessionManager.isPaused() -> "Turno pausado. Toque em Retomar para continuar."
        WorkSessionManager.isStarted() -> "Captura ativa. O ciclo começa na primeira corrida aceita."
        ShiftManager.plan() == null -> "Planeje o turno e confirme as permissões antes de iniciar."
        else -> "Turno planejado. Toque em Iniciar quando estiver pronto."
    }

    private fun timeToday(hour: Int, minute: Int): Long = Calendar.getInstance().apply {
        set(Calendar.HOUR_OF_DAY, hour)
        set(Calendar.MINUTE, minute)
        set(Calendar.SECOND, 0)
        set(Calendar.MILLISECOND, 0)
    }.timeInMillis

    private fun parseNumber(value: String): Double? {
        val raw = value.trim().replace("R$", "", ignoreCase = true).replace(" ", "")
        val normalized = when {
            raw.contains(',') && raw.contains('.') -> {
                if (raw.lastIndexOf(',') > raw.lastIndexOf('.')) {
                    raw.replace(".", "").replace(',', '.')
                } else {
                    raw.replace(",", "")
                }
            }

            raw.contains(',') -> raw.replace(',', '.')
            else -> raw
        }
        return normalized.toDoubleOrNull()
    }

    private fun formatMoney(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)

    private fun formatNumber(value: Double): String = String.format(Locale("pt", "BR"), "%.2f", value)

    private fun formatDuration(millis: Long): String {
        val seconds = (millis.coerceAtLeast(0L) / 1_000L)
        return String.format(Locale.US, "%02d:%02d", seconds / 60L, seconds % 60L)
    }

    private fun formatClock(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(CLOCK_FORMATTER)

    private fun formatDateTime(millis: Long): String = Instant.ofEpochMilli(millis)
        .atZone(ZoneId.systemDefault())
        .format(DATE_TIME_FORMATTER)

    private fun formatDate(date: LocalDate): String = date.format(DATE_FORMATTER)

    private fun cyclesFor(millis: Long): Int = ((millis.coerceAtLeast(0L) + HOUR_MILLIS - 1L) / HOUR_MILLIS).toInt()

    private fun outcomeLabel(outcome: RideOutcome): String = when (outcome) {
        RideOutcome.PENDING -> "AGUARDANDO"
        RideOutcome.ACCEPTED -> "ACEITA"
        RideOutcome.REJECTED -> "RECUSADA"
        RideOutcome.EXPIRED -> "EXPIRADA"
        RideOutcome.COMPLETED -> "CONCLUÍDA"
    }

    private fun rideTypeLabel(type: RideType): String = when (type) {
        RideType.NORMAL -> "UberX"
        RideType.PRIORITY -> "Priority"
        RideType.EXCLUSIVE -> "Exclusivo"
        RideType.UNKNOWN -> "Corrida"
    }

    private fun dp(value: Int): Int = (value * resources.displayMetrics.density).roundToInt()

    private fun renderTimeChoice(choice: TimeChoice) {
        choice.view.text = "${choice.label} · ${String.format(Locale.US, "%02d:%02d", choice.hour, choice.minute)}"
    }

    private class TimeChoice(
        val label: String,
        var hour: Int,
        var minute: Int
    ) {
        lateinit var view: TextView
    }

    private enum class Panel { TODAY, HISTORY, DATA }

    private companion object {
        const val REQUEST_NOTIFICATIONS = 502
        const val MENU_PLAN = 1
        const val MENU_GOALS = 2
        const val MENU_PERMISSIONS = 3
        const val MENU_END_SHIFT = 4
        const val DASHBOARD_REFRESH_MILLIS = 1_000L
        const val DAY_MILLIS = 24L * 60L * 60L * 1_000L
        const val HOUR_MILLIS = 60L * 60L * 1_000L
        val CLOCK_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("HH:mm")
        val DATE_TIME_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM HH:mm")
        val DATE_FORMATTER: DateTimeFormatter = DateTimeFormatter.ofPattern("dd/MM")
    }
}
