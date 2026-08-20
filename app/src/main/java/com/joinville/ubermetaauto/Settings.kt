package com.joinville.ubermetaauto

import android.content.Context

object Settings {

    private const val PREFS = "settings"

    private const val META_HORA = "meta_hora"
    private const val META_KM = "meta_km"

    private const val DEFAULT_META_HORA = 67.0
    private const val DEFAULT_META_KM = 2.2

    fun save(
        context: Context,
        hora: Double,
        km: Double
    ) {

        context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .edit()
            .putFloat(
                META_HORA,
                hora.toFloat()
            )
            .putFloat(
                META_KM,
                km.toFloat()
            )
            .apply()
    }

    fun metaHora(
        context: Context
    ): Double {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getFloat(
                META_HORA,
                DEFAULT_META_HORA.toFloat()
            )
            .toDouble()
    }

    fun metaKm(
        context: Context
    ): Double {

        return context
            .getSharedPreferences(
                PREFS,
                Context.MODE_PRIVATE
            )
            .getFloat(
                META_KM,
                DEFAULT_META_KM.toFloat()
            )
            .toDouble()
    }

    fun targets(context: Context): RideTargets =
        RideTargets(
            hourly = metaHora(context),
            perKm = metaKm(context)
        )
}
