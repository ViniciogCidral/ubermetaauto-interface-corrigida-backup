package com.joinville.ubermetaauto

import android.content.Context

/**
 * Mantém a ligação entre uma oferta detectada e seu resultado real no Uber.
 *
 * Uma oferta não é considerada aceita apenas porque desapareceu da tela. A
 * aceitação depende de um sinal explícito recebido pela acessibilidade.
 */
object RideLifecycleManager {

    private var pendingOfferId: String? = null
    private var activeRideId: String? = null
    private var preferences: android.content.SharedPreferences? = null

    @Synchronized
    fun initialize(context: Context) {
        if (preferences != null) return

        preferences = context.applicationContext.getSharedPreferences(
            PREFS,
            Context.MODE_PRIVATE
        )
        pendingOfferId = preferences?.getString(KEY_PENDING_OFFER_ID, null)
        activeRideId = preferences?.getString(KEY_ACTIVE_RIDE_ID, null)
    }

    @Synchronized
    fun registerOffer(record: RideRecord) {
        if (activeRideId == null) {
            pendingOfferId = record.id
            persist()
        }
    }

    @Synchronized
    fun pendingOfferId(): String? = pendingOfferId

    @Synchronized
    fun activeRideId(): String? = activeRideId

    /** Uma nova oferta só pode ser registrada sem corrida ativa. */
    @Synchronized
    fun canReceiveOffers(): Boolean = activeRideId == null

    @Synchronized
    fun confirmAccepted(): String? {
        val recordId = pendingOfferId ?: return null
        pendingOfferId = null
        activeRideId = recordId
        persist()
        return recordId
    }

    @Synchronized
    fun confirmRejected(): String? {
        val recordId = pendingOfferId
        pendingOfferId = null
        persist()
        return recordId
    }

    /**
     * Marca a oferta como expirada sem classificá-la como recusa manual.
     * O chamador deve usar este método apenas com um sinal explícito do Uber.
     */
    @Synchronized
    fun confirmExpired(): String? {
        val recordId = pendingOfferId
        pendingOfferId = null
        persist()
        return recordId
    }

    @Synchronized
    fun confirmCompleted(): String? {
        val recordId = activeRideId
        activeRideId = null
        persist()
        return recordId
    }

    private fun persist() {
        preferences?.edit()
            ?.putString(KEY_PENDING_OFFER_ID, pendingOfferId)
            ?.putString(KEY_ACTIVE_RIDE_ID, activeRideId)
            ?.apply()
    }

    private const val PREFS = "ride_lifecycle"
    private const val KEY_PENDING_OFFER_ID = "pending_offer_id"
    private const val KEY_ACTIVE_RIDE_ID = "active_ride_id"
}
