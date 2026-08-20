package com.joinville.ubermetaauto

/** Intervalo em que um ciclo permaneceu pausado. */
data class WorkPause(
    val startedAtMillis: Long,
    val endedAtMillis: Long? = null
) {

    fun durationMillis(nowMillis: Long): Long {
        val end = endedAtMillis ?: nowMillis
        return (end - startedAtMillis).coerceAtLeast(0L)
    }
}
