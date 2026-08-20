package com.joinville.ubermetaauto

/**
 * Período efetivamente trabalhado dentro de um ciclo.
 *
 * O intervalo é aberto no início de uma corrida e fechado ao terminar. Pausas
 * continuam registradas à parte, para não serem confundidas com tempo vazio.
 */
data class WorkInterval(
    val rideRecordId: String,
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null
) {

    fun durationMillis(nowMillis: Long): Long {
        val end = endedAtMillis ?: nowMillis
        return (end - startedAtMillis).coerceAtLeast(0L)
    }
}
