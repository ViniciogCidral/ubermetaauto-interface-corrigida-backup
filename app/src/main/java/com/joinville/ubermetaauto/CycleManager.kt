package com.joinville.ubermetaauto

import android.content.Context

/**
 * Coordena ciclos sequenciais de trabalho.
 *
 * A interface pode consultar [current] para exibir o contador. Chamadas a
 * [refresh] encerram ciclos vencidos e criam automaticamente o próximo ciclo.
 */
object CycleManager {

    private var currentCycle: WorkCycle? = null
    private val completedCycles = mutableListOf<CycleRecord>()
    private var store: CycleStateStore? = null

    @Synchronized
    fun initialize(context: Context) {
        if (store != null) return

        val stateStore = CycleStateStore(context)
        store = stateStore
        val saved = stateStore.load()
        currentCycle = saved.current
        completedCycles.clear()
        completedCycles.addAll(saved.completed)
    }

    @Synchronized
    fun current(): WorkCycle? = currentCycle

    @Synchronized
    fun history(): List<CycleRecord> = completedCycles.toList()

    @Synchronized
    fun start(
        hourlyTarget: Double,
        startedAtMillis: Long = System.currentTimeMillis()
    ): WorkCycle {
        require(hourlyTarget > 0.0) { "A meta por hora deve ser maior que zero." }

        val active = currentCycle
        if (active != null && !active.isFinished(startedAtMillis)) {
            return active
        }

        if (active != null) {
            finish(active, active.expectedFinishedAtMillis(startedAtMillis))
        }

        val cycle = WorkCycle(
            number = completedCycles.size + 1,
            startedAtMillis = startedAtMillis,
            hourlyTarget = hourlyTarget
        )

        currentCycle = cycle
        persist()
        return cycle
    }

    @Synchronized
    fun refresh(nowMillis: Long = System.currentTimeMillis()): WorkCycle? {
        var active = currentCycle ?: return null

        while (active.isFinished(nowMillis)) {
            val endedAt = active.expectedFinishedAtMillis(nowMillis)
            finish(active, endedAt)

            active = WorkCycle(
                number = completedCycles.size + 1,
                startedAtMillis = endedAt,
                hourlyTarget = active.hourlyTarget
            )

            currentCycle = active
        }

        persist()
        return active
    }

    @Synchronized
    fun registerCompletedRide(record: RideRecord): WorkCycle? {
        val active = refresh() ?: return null
        if (record.id in active.countedRideIds) return active

        val updated = active.copy(
            revenue = active.revenue + record.offer.fare,
            rideCount = active.rideCount + 1,
            countedRideIds = active.countedRideIds + record.id
        )

        currentCycle = updated
        persist()
        return updated
    }

    /** Marca o início efetivo de uma corrida no ciclo ativo. */
    @Synchronized
    fun registerRideStarted(
        recordId: String,
        startedAtMillis: Long = System.currentTimeMillis()
    ): WorkCycle? {
        val active = refresh(startedAtMillis) ?: return null
        if (active.isPaused()) return active

        if (active.workIntervals.any { it.rideRecordId == recordId }) {
            return active
        }

        return active.copy(
            workIntervals = active.workIntervals + WorkInterval(
                rideRecordId = recordId,
                startedAtMillis = startedAtMillis
            )
        ).also { updated ->
            currentCycle = updated
            persist()
        }
    }

    /** Fecha o período de trabalho e soma a corrida ao faturamento do ciclo. */
    @Synchronized
    fun registerRideCompleted(
        record: RideRecord,
        completedAtMillis: Long = System.currentTimeMillis()
    ): WorkCycle? {
        var active = refresh(completedAtMillis) ?: return null
        val index = active.workIntervals.indexOfLast {
            it.rideRecordId == record.id && it.endedAtMillis == null
        }

        if (index >= 0) {
            val intervals = active.workIntervals.toMutableList()
            val interval = intervals[index]
            intervals[index] = interval.copy(
                endedAtMillis = completedAtMillis.coerceAtLeast(interval.startedAtMillis)
            )
            active = active.copy(workIntervals = intervals)
            currentCycle = active
            persist()
        }

        return registerCompletedRide(record)
    }

    @Synchronized
    fun pause(nowMillis: Long = System.currentTimeMillis()): WorkCycle? {
        val active = refresh(nowMillis) ?: return null
        if (active.isPaused()) return active

        return active.copy(
            pauses = active.pauses + WorkPause(startedAtMillis = nowMillis)
        ).also { paused ->
            currentCycle = paused
            persist()
        }
    }

    @Synchronized
    fun resume(nowMillis: Long = System.currentTimeMillis()): WorkCycle? {
        val active = currentCycle ?: return null
        val openPause = active.pauses.lastOrNull() ?: return active
        if (openPause.endedAtMillis != null) return active

        return active.copy(
            pauses = active.pauses.dropLast(1) + openPause.copy(
                endedAtMillis = nowMillis.coerceAtLeast(openPause.startedAtMillis)
            )
        ).also { resumed ->
            currentCycle = resumed
            persist()
        }
    }

    @Synchronized
    fun stop(nowMillis: Long = System.currentTimeMillis()): CycleRecord? {
        val active = currentCycle ?: return null
        finish(active, nowMillis.coerceAtLeast(active.startedAtMillis))
        return completedCycles.lastOrNull()
    }

    private fun finish(cycle: WorkCycle, finishedAtMillis: Long) {
        val end = finishedAtMillis.coerceAtLeast(cycle.startedAtMillis)

        val pauses = cycle.pauses.toMutableList()
        val openPause = pauses.lastOrNull()
        if (openPause != null && openPause.endedAtMillis == null) {
            pauses[pauses.lastIndex] = openPause.copy(endedAtMillis = end)
        }

        val intervals = cycle.workIntervals.map { interval ->
            if (interval.endedAtMillis == null) {
                interval.copy(endedAtMillis = end.coerceAtLeast(interval.startedAtMillis))
            } else {
                interval
            }
        }

        val finished = cycle.copy(
            pauses = pauses,
            workIntervals = intervals,
            finishedAtMillis = end
        )

        completedCycles.add(CycleRecord.from(finished))
        currentCycle = null
        persist()
    }

    private fun persist() {
        store?.save(currentCycle, completedCycles)
    }
}
