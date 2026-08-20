package com.joinville.ubermetaauto

import android.content.Context
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId

/**
 * Fonte central dos registros de corrida.
 *
 * Por enquanto os dados ficam em memória. O repositório foi separado para que
 * a próxima implementação persistente não precise alterar os consumidores
 * deste gerenciador.
 */
object RideHistoryManager {

    private var repository: RideRecordRepository =
        InMemoryRideRecordRepository()

    private var isPersistentRepositoryInitialized = false

    /** Deve ser chamado no início do app antes de usar o histórico. */
    @Synchronized
    fun initialize(context: Context) {
        if (isPersistentRepositoryInitialized) return

        repository = SharedPreferencesRideRecordRepository(context)
        isPersistentRepositoryInitialized = true
    }

    @Synchronized
    fun registerDetected(
        offer: RideOffer,
        type: RideType = RideType.UNKNOWN,
        decision: RideDecision = RideDecision.UNKNOWN,
        detectedAtMillis: Long = System.currentTimeMillis()
    ): RideRecord {
        val record = RideRecord(
            detectedAtMillis = detectedAtMillis,
            offer = offer,
            type = type,
            decision = decision
        )

        repository.insert(record)
        return record
    }

    @Synchronized
    fun markStarted(
        recordId: String,
        startedAtMillis: Long = System.currentTimeMillis()
    ): RideRecord? = update(recordId) { record ->
        if (record.startedAtMillis == null) {
            record.copy(
                startedAtMillis = startedAtMillis,
                outcome = RideOutcome.ACCEPTED
            )
        } else {
            record
        }
    }

    @Synchronized
    fun markCompleted(
        recordId: String,
        completedAtMillis: Long = System.currentTimeMillis()
    ): RideRecord? = update(recordId) { record ->
        if (record.completedAtMillis == null) {
            record.copy(
                completedAtMillis = completedAtMillis,
                outcome = RideOutcome.COMPLETED
            )
        } else {
            record
        }
    }

    @Synchronized
    fun all(): List<RideRecord> = repository.all()

    @Synchronized
    fun latest(): RideRecord? = repository.latest()

    /**
     * Procura uma oferta ainda pendente com os mesmos dados reconhecidos.
     * Usado para não duplicar uma tela que já estava aberta antes de o serviço
     * de captura ser recriado.
     */
    @Synchronized
    fun findPendingMatching(offer: RideOffer): RideRecord? =
        repository.all().asReversed().firstOrNull { record ->
            record.outcome == RideOutcome.PENDING &&
                record.offer.hasSameTripDataAs(offer)
        }

    /** Atualiza dados que o OCR reconheceu depois, sem duplicar a oferta. */
    @Synchronized
    fun enrichPendingOffer(
        recordId: String,
        offer: RideOffer,
        decision: RideDecision
    ): RideRecord? =
        update(recordId) { record ->
            if (record.outcome == RideOutcome.PENDING) {
                record.copy(
                    offer = offer,
                    type = offer.rideType,
                    decision = decision
                )
            } else {
                record
            }
        }

    @Synchronized
    fun query(
        filter: RideHistoryFilter = RideHistoryFilter(),
        today: LocalDate = LocalDate.now()
    ): List<RideRecord> {
        val dateRange = dateRange(filter, today)

        return repository.all()
            .asSequence()
            .filter { record ->
                val detectedDate = Instant.ofEpochMilli(record.detectedAtMillis)
                    .atZone(ZoneId.systemDefault())
                    .toLocalDate()

                (dateRange == null || detectedDate in dateRange) &&
                    (filter.type == null || record.type == filter.type) &&
                    (filter.decision == null || record.decision == filter.decision) &&
                    (filter.outcome == null || record.outcome == filter.outcome)
            }
            .sortedByDescending { it.detectedAtMillis }
            .toList()
    }

    @Synchronized
    fun markRejected(recordId: String): RideRecord? = update(recordId) { record ->
        if (record.outcome == RideOutcome.PENDING) {
            record.copy(outcome = RideOutcome.REJECTED)
        } else {
            record
        }
    }

    @Synchronized
    fun markExpired(recordId: String): RideRecord? = update(recordId) { record ->
        if (record.outcome == RideOutcome.PENDING) {
            record.copy(outcome = RideOutcome.EXPIRED)
        } else {
            record
        }
    }

    private fun update(
        recordId: String,
        transform: (RideRecord) -> RideRecord
    ): RideRecord? {
        val record = repository.findById(recordId) ?: return null
        val updated = transform(record)
        repository.replace(updated)
        return updated
    }

    private fun dateRange(
        filter: RideHistoryFilter,
        today: LocalDate
    ): ClosedRange<LocalDate>? {
        return when (filter.period) {
            HistoryPeriod.ALL -> null
            HistoryPeriod.TODAY -> today..today
            HistoryPeriod.YESTERDAY -> today.minusDays(1)..today.minusDays(1)
            HistoryPeriod.WEEK -> today.minusDays(6)..today
            HistoryPeriod.MONTH -> today.withDayOfMonth(1)..today
            HistoryPeriod.THREE_MONTHS -> today.minusMonths(3).plusDays(1)..today
            HistoryPeriod.SIX_MONTHS -> today.minusMonths(6).plusDays(1)..today
            HistoryPeriod.CUSTOM -> {
                val start = filter.customStartDate ?: return null
                val end = filter.customEndDate ?: start
                minOf(start, end)..maxOf(start, end)
            }
        }
    }
}
