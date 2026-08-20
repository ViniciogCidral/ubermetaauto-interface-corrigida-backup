package com.joinville.ubermetaauto

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class BusinessLogicTest {

    @Test
    fun `turno de dezesseis horas com quatro horas de pausa tem doze ciclos`() {
        val hour = 60L * 60L * 1_000L
        val plan = ShiftPlan(
            startsAtMillis = 6L * hour,
            endsAtMillis = 22L * hour,
            plannedPauses = listOf(
                PlannedPause(10L * hour, 14L * hour)
            )
        )

        assertEquals(16L * hour, plan.plannedDurationMillis)
        assertEquals(4L * hour, plan.plannedPauseMillis)
        assertEquals(12L * hour, plan.plannedWorkingDurationMillis)
        assertEquals(12, plan.expectedCycleCount)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `turno nao aceita pausas planejadas sobrepostas`() {
        val hour = 60L * 60L * 1_000L
        ShiftPlan(
            startsAtMillis = 6L * hour,
            endsAtMillis = 18L * hour,
            plannedPauses = listOf(
                PlannedPause(9L * hour, 11L * hour),
                PlannedPause(10L * hour, 12L * hour)
            )
        )
    }

    @Test
    fun `oferta calcula totais e indicadores sem incluir nota no calculo`() {
        val offer = RideOffer(
            fare = 18.70,
            pickupMinutes = 11.0,
            pickupKm = 2.4,
            tripMinutes = 20.0,
            tripKm = 8.4,
            customerRating = 4.95
        )

        assertEquals(31.0, offer.totalMinutes, 0.001)
        assertEquals(10.8, offer.totalKm, 0.001)
        assertEquals(36.1935, offer.reaisPerHour, 0.001)
        assertEquals(1.7314, offer.reaisPerKm, 0.001)
    }

    @Test
    fun `decisao respeita metas e faixa talvez`() {
        val offer = RideOffer(20.0, 10.0, 1.0, 10.0, 9.0)

        assertEquals(RideDecision.TAKE, DecisionEngine.decide(offer, 55.0, 2.0))
        assertEquals(RideDecision.MAYBE, DecisionEngine.decide(offer, 57.0, 2.1))
        assertEquals(RideDecision.PASS, DecisionEngine.decide(offer, 80.0, 3.0))
    }

    @Test
    fun `texto do botao de recusar nao e confundido com estado da corrida`() {
        assertEquals(
            RideLifecycleSignalDetector.Signal.NONE,
            RideLifecycleSignalDetector.detect("UberX Recusar corrida")
        )
        assertTrue(RideLifecycleSignalDetector.isExplicitRejectionAction("Recusar corrida"))
        assertFalse(RideLifecycleSignalDetector.isExplicitRejectionAction("A caminho do embarque"))
    }

    @Test
    fun `calculo de projecao nao inventa horas quando nao ha media`() {
        val projection = ProjectionCalculator.calculate(
            currentRevenue = 30.0,
            goalRevenue = 100.0,
            averageReaisPerHour = 0.0,
            remainingHours = 2.0
        )

        assertEquals(70.0, projection.amountRemaining, 0.001)
        assertEquals(35.0, projection.requiredReaisPerHour, 0.001)
        assertNull(projection.estimatedHoursToGoal)
    }
}
