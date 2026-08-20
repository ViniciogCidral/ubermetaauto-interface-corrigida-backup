package com.joinville.ubermetaauto

/**
 * Contrato de armazenamento dos registros de corrida.
 *
 * A implementação atual fica apenas na memória. Uma implementação persistente
 * poderá usar este mesmo contrato quando a persistência for adicionada, sem
 * acoplar OCR, decisão ou interface ao modo de armazenamento.
 */
interface RideRecordRepository {

    fun insert(record: RideRecord)

    fun replace(record: RideRecord)

    fun findById(recordId: String): RideRecord?

    fun all(): List<RideRecord>

    fun latest(): RideRecord?
}

/** Implementação temporária usada até a fase de persistência. */
class InMemoryRideRecordRepository : RideRecordRepository {

    private val records = mutableListOf<RideRecord>()

    override fun insert(record: RideRecord) {
        records.add(record)
    }

    override fun replace(record: RideRecord) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) {
            records[index] = record
        }
    }

    override fun findById(recordId: String): RideRecord? =
        records.firstOrNull { it.id == recordId }

    override fun all(): List<RideRecord> = records.toList()

    override fun latest(): RideRecord? = records.lastOrNull()
}
