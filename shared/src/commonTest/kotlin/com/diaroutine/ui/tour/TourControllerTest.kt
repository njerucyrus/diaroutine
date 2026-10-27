package com.diaroutine.ui.tour

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue

class TourControllerTest {
    private var finished = 0
    private val tour = TourController(onFinished = { finished++ })

    @Test
    fun walksThroughEveryStepThenFinishesOnce() {
        tour.start()
        assertEquals(AppTourSteps.first(), tour.current)
        repeat(AppTourSteps.size - 1) { tour.next() }
        assertEquals(AppTourSteps.last(), tour.current)

        tour.next()
        assertFalse(tour.isActive)
        assertNull(tour.current)
        assertEquals(1, finished)

        tour.next()
        tour.finish()
        assertEquals(1, finished)
    }

    @Test
    fun skippingFinishesStraightAway() {
        tour.start()
        tour.next()
        tour.finish()
        assertFalse(tour.isActive)
        assertEquals(1, finished)
    }

    @Test
    fun backStopsAtTheFirstStep() {
        tour.start(at = 1)
        tour.back()
        tour.back()
        assertEquals(0, tour.index)
        assertTrue(tour.isActive)
    }

    @Test
    fun everyTargetedStepHasATargetOnTheTodayScreenOrNavBar() {
        val targets = AppTourSteps.mapNotNull { it.target }.toSet()
        assertEquals(TourTarget.entries.toSet(), targets)
    }
}
