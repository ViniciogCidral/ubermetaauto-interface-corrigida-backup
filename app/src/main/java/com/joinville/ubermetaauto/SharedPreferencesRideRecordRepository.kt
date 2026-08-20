package com.joinville.ubermetaauto

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Armazenamento local dos registros de corrida, sem depender de servidor. */
class SharedPreferencesRideRecordRepository(
    context: Context
) : RideRecordRepository {

    private val preferences = context.applicationContext.getSharedPreferences(
        PREFS,
        Context.MODE_PRIVATE
    )

    private val records = readRecords().toMutableList()

    override fun insert(record: RideRecord) {
        records.add(record)
        persist()
    }

    override fun replace(record: RideRecord) {
        val index = records.indexOfFirst { it.id == record.id }
        if (index >= 0) {
            records[index] = record
            persist()
        }
    }

    override fun findById(recordId: String): RideRecord? =
        records.firstOrNull { it.id == recordId }

    override fun all(): List<RideRecord> = records.toList()

    override fun latest(): RideRecord? = records.lastOrNull()

    private fun persist() {
        val array = JSONArray()
        records.forEach { record -> array.put(record.toJson()) }
        preferences.edit().putString(KEY_RECORDS, array.toString()).apply()
    }

    private fun readRecords(): List<RideRecord> {
        val raw = preferences.getString(KEY_RECORDS, null) ?: return emptyList()

        return runCatching {
            val array = JSONArray(raw)
            buildList {
                for (index in 0 until array.length()) {
                    recordFromJson(array.getJSONObject(index))?.let(::add)
                }
            }
        }.getOrDefault(emptyList())
    }

    private fun RideRecord.toJson(): JSONObject = JSONObject().apply {
        put("id", id)
        put("detectedAtMillis", detectedAtMillis)
        put("fare", offer.fare)
        put("pickupMinutes", offer.pickupMinutes)
        put("pickupKm", offer.pickupKm)
        put("tripMinutes", offer.tripMinutes)
        put("tripKm", offer.tripKm)
        put("rideType", offer.rideType.name)
        put("customerRating", offer.customerRating)
        put("type", type.name)
        put("decision", decision.name)
        put("outcome", outcome.name)
        put("startedAtMillis", startedAtMillis)
        put("completedAtMillis", completedAtMillis)
    }

    private fun recordFromJson(json: JSONObject): RideRecord? = runCatching {
        RideRecord(
            id = json.getString("id"),
            detectedAtMillis = json.getLong("detectedAtMillis"),
            offer = RideOffer(
                fare = json.getDouble("fare"),
                pickupMinutes = json.getDouble("pickupMinutes"),
                pickupKm = json.getDouble("pickupKm"),
                tripMinutes = json.getDouble("tripMinutes"),
                tripKm = json.getDouble("tripKm"),
                rideType = enumValue(json.optString("rideType"), RideType.UNKNOWN),
                customerRating = json.optNullableDouble("customerRating")
            ),
            type = enumValue(json.optString("type"), RideType.UNKNOWN),
            decision = enumValue(json.optString("decision"), RideDecision.UNKNOWN),
            outcome = enumValue(json.optString("outcome"), RideOutcome.PENDING),
            startedAtMillis = json.optNullableLong("startedAtMillis"),
            completedAtMillis = json.optNullableLong("completedAtMillis")
        )
    }.getOrNull()

    private fun JSONObject.optNullableLong(key: String): Long? =
        if (isNull(key)) null else optLong(key)

    private fun JSONObject.optNullableDouble(key: String): Double? =
        if (isNull(key) || !has(key)) null else optDouble(key)

    private inline fun <reified T : Enum<T>> enumValue(
        value: String,
        fallback: T
    ): T = enumValues<T>().firstOrNull { it.name == value } ?: fallback

    private companion object {
        const val PREFS = "ride_history"
        const val KEY_RECORDS = "records"
    }
}
