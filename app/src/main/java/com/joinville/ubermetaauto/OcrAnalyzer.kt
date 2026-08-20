package com.joinville.ubermetaauto

import android.graphics.Bitmap
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.Text
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import kotlin.math.abs

object OcrAnalyzer {

    private val recognizer =
        TextRecognition.getClient(
            TextRecognizerOptions.DEFAULT_OPTIONS
        )

    private val moneyRegex =
        Regex(
            """R\$\s*([0-9]+(?:[.,][0-9]{1,2})?)""",
            RegexOption.IGNORE_CASE
        )

    private val minKmRegex =
        Regex(
            """(\d+(?:[.,]\d+)?)\s*min(?:utos?)?\s*\(\s*([0-9]+(?:[.,][0-9]+)?)\s*km\s*\)""",
            RegexOption.IGNORE_CASE
        )

    private val ratingNumberRegex =
        Regex("""(?<![\d.,])([0-5](?:[.,]\d{1,2})?)(?![\d.,])""")

    fun analyze(
        bitmap: Bitmap,
        callback: (String?, RideOffer?) -> Unit
    ) {

        if (bitmap.isRecycled) {
            callback(null, null)
            return
        }

        val image =
            InputImage.fromBitmap(bitmap, 0)

        recognizer
            .process(image)
            .addOnSuccessListener { result ->

                try {

                    val text =
                        result.text
                            .replace('\n', ' ')
                            .replace('\r', ' ')
                            .replace(
                                Regex("\\s+"),
                                " "
                            )
                            .trim()

                    if (text.isEmpty()) {
                        callback(null, null)
                        return@addOnSuccessListener
                    }

                    /*
                     * Primeiro tentamos encontrar o preço
                     * pela posição em relação ao UberX.
                     */
                    val fare = findUberFare(result) ?: run {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    /*
                     * Os tempos/km continuam sendo obtidos
                     * normalmente do texto reconhecido.
                     */
                    val normalized =
                        text

                    val pairs =
                        minKmRegex
                            .findAll(normalized)
                            .toList()

                    /*
                     * Se não temos pelo menos os dois
                     * deslocamentos, ainda não temos uma
                     * oferta completa.
                     */
                    if (pairs.size < 2) {

                        callback(
                            text,
                            null
                        )

                        return@addOnSuccessListener
                    }

                    val pickup =
                        pairs[0]

                    val trip =
                        pairs[1]

                    val pickupMinutes = parseDecimal(pickup.groupValues[1]) ?: run {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    val pickupKm = parseDecimal(pickup.groupValues[2]) ?: run {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    val tripMinutes = parseDecimal(trip.groupValues[1]) ?: run {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    val tripKm = parseDecimal(trip.groupValues[2]) ?: run {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    /*
                     * Sem preço localizado corretamente,
                     * NÃO usamos um R$ antigo encontrado
                     * em outra parte da tela.
                     */
                    if (
                        fare <= 0.0 ||
                        pickupMinutes <= 0.0 ||
                        tripMinutes <= 0.0 ||
                        pickupKm < 0.0 ||
                        tripKm <= 0.0
                    ) {

                        callback(
                            text,
                            null
                        )

                        return@addOnSuccessListener
                    }

                    if (pickupMinutes > 180.0) {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    if (tripMinutes > 300.0) {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    if (pickupKm > 100.0) {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    if (tripKm > 500.0) {
                        callback(text, null)
                        return@addOnSuccessListener
                    }

                    val offer =
                        RideOffer(
                            fare = fare,
                            pickupMinutes = pickupMinutes,
                            pickupKm = pickupKm,
                            tripMinutes = tripMinutes,
                            tripKm = tripKm,
                            rideType = detectRideType(result),
                            customerRating = detectCustomerRating(result)
                        )

                    callback(
                        text,
                        offer
                    )

                } catch (_: Throwable) {

                    callback(
                        null,
                        null
                    )
                }
            }
            .addOnFailureListener {

                callback(
                    null,
                    null
                )
            }
    }

    /*
     * Procura o preço REAL da oferta.
     *
     * Regra:
     *
     * UberX
     *   ↓
     * preço
     *
     * O preço precisa estar abaixo do UberX
     * e próximo dele.
     */
    private fun findUberFare(
    result: Text
): Double? {

    data class Candidate(
        val value: Double,
        val x: Float,
        val top: Float
    )

    val referenceBoxes =
        mutableListOf<android.graphics.Rect>()

    val candidates =
        mutableListOf<Candidate>()

    for (block in result.textBlocks) {

        for (line in block.lines) {

            val lineText =
                line.text
                    .replace(
                        Regex("\\s+"),
                        " "
                    )
                    .trim()

            val normalizedLine =
                lineText
                    .lowercase()
                    .replace(
                        Regex("[^a-záéíóúãõç]"),
                        ""
                    )

            /*
             * Referências aceitas:
             *
             * UberX
             * Priority
             * Exclusivo
             * Exclusiva
             *
             * O UberX continua sendo a referência
             * principal para as corridas normais.
             */
            val isUberX =
                normalizedLine.contains("uberx")

            val isPriority =
                normalizedLine.contains("priority")

            val isExclusive =
                normalizedLine.contains("exclusivo") ||
                normalizedLine.contains("exclusiva")

            if (
                isUberX ||
                isPriority ||
                isExclusive
            ) {

                line.boundingBox?.let {
                    referenceBoxes.add(it)
                }
            }

            /*
             * Procura qualquer valor monetário
             * reconhecido pelo OCR.
             */
            val match =
                moneyRegex.find(lineText)

            if (match != null) {

                val value = parseDecimal(match.groupValues[1]) ?: continue

                line.boundingBox?.let { box ->

                    if (value > 0.0) {

                        candidates.add(
                            Candidate(
                                value = value,
                                x =
                                    (
                                        box.left +
                                            box.right
                                        ) / 2f,
                                top =
                                    box.top.toFloat()
                            )
                        )
                    }
                }
            }
        }
    }

    /*
     * Sem UberX/Priority/Exclusivo,
     * não consideramos a tela uma oferta.
     */
    if (referenceBoxes.isEmpty()) {
        return null
    }

    var best: Candidate? =
        null

    var bestScore =
        Float.MAX_VALUE

    for (referenceBox in referenceBoxes) {

        val referenceX =
            (
                referenceBox.left +
                    referenceBox.right
                ) / 2f

        val referenceBottom =
            referenceBox.bottom.toFloat()

        for (candidate in candidates) {

            /*
             * O preço precisa aparecer abaixo
             * da identificação da modalidade.
             */
            if (
                candidate.top <
                referenceBottom
            ) {
                continue
            }

            val horizontal =
                abs(
                    candidate.x -
                        referenceX
                )

            /*
             * Mantemos praticamente a mesma
             * tolerância que já funcionava
             * para UberX.
             */
            if (horizontal > 500f) {
                continue
            }

            val vertical =
                candidate.top -
                    referenceBottom

            if (vertical > 700f) {
                continue
            }

            val score =
                vertical +
                    horizontal * 0.35f

            if (score < bestScore) {

                bestScore =
                    score

                best =
                    candidate
            }
        }
    }

    return best?.value
}

private fun detectRideType(result: Text): RideType {
    val text = result.text.lowercase()

    return when {
        text.contains("priority") -> RideType.PRIORITY
        text.contains("exclusivo") || text.contains("exclusiva") ->
            RideType.EXCLUSIVE
        text.contains("uberx") -> RideType.NORMAL
        else -> RideType.UNKNOWN
    }
}

/**
 * Lê a nota somente quando existe contexto suficiente para não confundir uma
 * distância ou tempo da oferta com uma avaliação do passageiro.
 */
private fun detectCustomerRating(result: Text): Double? {
    data class Candidate(
        val value: Double,
        val box: android.graphics.Rect,
        val hasStar: Boolean
    )

    val labelBoxes = mutableListOf<android.graphics.Rect>()
    val candidates = mutableListOf<Candidate>()

    for (block in result.textBlocks) {
        for (line in block.lines) {
            val text = line.text.trim()
            val normalized = text.lowercase()
            val box = line.boundingBox ?: continue

            val isRatingLabel = normalized.contains("passageiro") ||
                normalized.contains("cliente") ||
                normalized.contains("avaliacao") ||
                normalized.contains("avaliação") ||
                normalized.contains("nota")

            if (isRatingLabel) {
                labelBoxes.add(box)
            }

            val hasStar = text.contains('\u2605') ||
                text.contains('\u2b50') ||
                normalized.contains("estrela")

            ratingNumberRegex.findAll(text).forEach { match ->
                val value = parseDecimal(match.groupValues[1]) ?: return@forEach
                if (value !in 0.0..5.0) return@forEach

                candidates.add(Candidate(value, box, hasStar || isRatingLabel))
            }
        }
    }

    /* Estrela ou rótulo na própria linha é evidência suficiente. */
    candidates.firstOrNull { it.hasStar }?.let { return it.value }

    /* Também aceita número próximo ao rótulo, quando o OCR separa em linhas. */
    return candidates.firstOrNull { candidate ->
        labelBoxes.any { label ->
            val horizontalGap = maxOf(
                label.left - candidate.box.right,
                candidate.box.left - label.right,
                0
            )
            val verticalGap = maxOf(
                label.top - candidate.box.bottom,
                candidate.box.top - label.bottom,
                0
            )

            horizontalGap <= 260 && verticalGap <= 150
        }
    }?.value
}

private fun parseDecimal(text: String): Double? {

    val cleaned =
        text
            .trim()
            .replace("R$", "")
            .replace(" ", "")

    if (cleaned.isEmpty()) {
        return null
    }

    return try {

        when {
            cleaned.contains(",") &&
                cleaned.contains(".") -> {

                val lastComma =
                    cleaned.lastIndexOf(",")

                val lastDot =
                    cleaned.lastIndexOf(".")

                if (lastComma > lastDot) {
                    cleaned
                        .replace(".", "")
                        .replace(",", ".")
                        .toDouble()
                } else {
                    cleaned
                        .replace(",", "")
                        .toDouble()
                }
            }

            cleaned.contains(",") -> {
                cleaned
                    .replace(",", ".")
                    .toDouble()
            }

            else -> {
                cleaned.toDouble()
            }
        }

    } catch (
        _: NumberFormatException
    ) {
        null
    }
}
}
