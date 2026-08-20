package com.joinville.ubermetaauto

/** Reconhece somente estados explícitos da interface do Uber Driver. */
object RideLifecycleSignalDetector {

    enum class Signal {
        ACCEPTED,
        REJECTED,
        COMPLETED,
        NONE
    }

    fun detect(visibleText: String): Signal {
        val text = normalize(visibleText)
        if (text.isBlank()) return Signal.NONE

        if (text.containsAny(COMPLETED_MARKERS)) return Signal.COMPLETED
        if (text.containsAny(ACCEPTED_MARKERS)) return Signal.ACCEPTED
        return Signal.NONE
    }

    /**
     * A recusa sÃ³ Ã© confiÃ¡vel quando vem do evento de clique no controle de
     * recusa. O texto desse controle tambÃ©m pode ficar visÃ­vel antes de a
     * pessoa tocar nele, portanto nÃ£o pode ser usado como estado da tela.
     */
    fun isExplicitRejectionAction(clickedText: String): Boolean {
        val text = normalize(clickedText)
        return text.containsAny(REJECTED_ACTION_MARKERS)
    }

    private fun String.containsAny(markers: Set<String>): Boolean =
        markers.any { marker -> contains(marker) }

    private fun normalize(value: String): String =
        value.lowercase()
            .replace(Regex("[áàâãä]"), "a")
            .replace(Regex("[éèêë]"), "e")
            .replace(Regex("[íìîï]"), "i")
            .replace(Regex("[óòôõö]"), "o")
            .replace(Regex("[úùûü]"), "u")
            .replace("ç", "c")

    private val ACCEPTED_MARKERS = setOf(
        "a caminho do embarque",
        "a caminho do passageiro",
        "navegar ate o embarque",
        "chegue ao local de embarque",
        "iniciar viagem",
        "passageiro embarcou"
    )

    private val REJECTED_ACTION_MARKERS = setOf(
        "recusar corrida",
        "recusar viagem",
        "rejeitar corrida",
        "rejeitar viagem",
        "nao aceitar"
    )

    private val COMPLETED_MARKERS = setOf(
        "viagem concluida",
        "ganhos desta viagem",
        "avaliar passageiro"
    )
}
