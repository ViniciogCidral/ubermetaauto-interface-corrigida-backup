package com.joinville.ubermetaauto

import android.content.Context
import java.util.UUID

/** Coordena um turno ativo e conserva os turnos encerrados. */
object ShiftManager {

    private var currentShift: WorkShift? = null
    private var plannedShift: ShiftPlan? = null
    private val completedShifts = mutableListOf<WorkShift>()
    private var store: ShiftStateStore? = null

    @Synchronized
    fun initialize(context: Context) {
        if (store != null) return

        val stateStore = ShiftStateStore(context)
        store = stateStore
        val saved = stateStore.load()
        currentShift = saved.current
        plannedShift = saved.plan
        completedShifts.clear()
        completedShifts.addAll(saved.completed)
    }

    @Synchronized
    fun current(): WorkShift? = currentShift

    @Synchronized
    fun plan(): ShiftPlan? = plannedShift

    @Synchronized
    fun savePlan(plan: ShiftPlan) {
        plannedShift = plan
        persist()
    }

    @Synchronized
    fun clearPlan() {
        plannedShift = null
        persist()
    }

    @Synchronized
    fun history(): List<WorkShift> = completedShifts.toList()

    @Synchronized
    fun start(
        plannedStartMillis: Long? = null,
        plannedEndMillis: Long? = null,
        startedAtMillis: Long = System.currentTimeMillis()
    ): WorkShift {
        currentShift?.let { return it }

        return WorkShift(
            id = UUID.randomUUID().toString(),
            plannedStartMillis = plannedStartMillis,
            plannedEndMillis = plannedEndMillis,
            startedAtMillis = startedAtMillis
        ).also {
            currentShift = it
            persist()
        }
    }

    /** Inicia um turno usando o planejamento previamente salvo. */
    @Synchronized
    fun startPlanned(
        startedAtMillis: Long = System.currentTimeMillis()
    ): WorkShift? {
        val plan = plannedShift ?: return null

        return start(
            plannedStartMillis = plan.startsAtMillis,
            plannedEndMillis = plan.endsAtMillis,
            startedAtMillis = startedAtMillis
        ).let { shift ->
            if (shift.plannedPauses == plan.plannedPauses) {
                shift
            } else {
                shift.copy(plannedPauses = plan.plannedPauses).also {
                    currentShift = it
                    persist()
                }
            }
        }
    }

    @Synchronized
    fun addCycle(cycle: WorkCycle) {
        val shift = currentShift ?: return
        currentShift = shift.copy(cycleNumbers = shift.cycleNumbers + cycle.number)
        persist()
    }

    @Synchronized
    fun addCompletedRide(record: RideRecord) {
        val shift = currentShift ?: return
        if (record.id in shift.completedRideIds) return

        currentShift = shift.copy(
            completedRideIds = shift.completedRideIds + record.id,
            revenue = shift.revenue + record.offer.fare
        )
        persist()
    }

    @Synchronized
    fun pause(nowMillis: Long = System.currentTimeMillis()) {
        val shift = currentShift ?: return
        if (shift.pauses.lastOrNull()?.endedAtMillis == null) return

        currentShift = shift.copy(
            pauses = shift.pauses + WorkPause(startedAtMillis = nowMillis)
        )
        persist()
    }

    @Synchronized
    fun resume(nowMillis: Long = System.currentTimeMillis()) {
        val shift = currentShift ?: return
        val pause = shift.pauses.lastOrNull() ?: return
        if (pause.endedAtMillis != null) return

        currentShift = shift.copy(
            pauses = shift.pauses.dropLast(1) + pause.copy(
                endedAtMillis = nowMillis.coerceAtLeast(pause.startedAtMillis)
            )
        )
        persist()
    }

    @Synchronized
    fun stop(nowMillis: Long = System.currentTimeMillis()): WorkShift? {
        val shift = currentShift ?: return null
        val pauses = shift.pauses.toMutableList()
        val openPause = pauses.lastOrNull()
        if (openPause != null && openPause.endedAtMillis == null) {
            pauses[pauses.lastIndex] = openPause.copy(endedAtMillis = nowMillis)
        }

        return shift.copy(
            endedAtMillis = nowMillis.coerceAtLeast(shift.startedAtMillis),
            pauses = pauses
        ).also { completed ->
            completedShifts.add(completed)
            currentShift = null
            persist()
        }
    }

    private fun persist() {
        store?.save(currentShift, completedShifts, plannedShift)
    }
}
