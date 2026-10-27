package com.diaroutine.app

import com.diaroutine.domain.model.AppLockSettings
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertNull
import kotlin.test.assertTrue
import kotlin.time.Clock
import kotlin.time.Duration
import kotlin.time.Duration.Companion.seconds
import kotlin.time.Instant

class AppLockTest {
    private class TestClock : Clock {
        var now = Instant.fromEpochSeconds(1_800_000_000)
        override fun now() = now
        fun advance(by: Duration) {
            now += by
        }
    }

    private val clock = TestClock()
    private val lock = AppLock(clock)

    /** A stand-in for the system prompt that answers [result] when [respond] is called. */
    private class Prompt {
        var pending: ((Boolean) -> Unit)? = null
        val authenticator: ((Boolean) -> Unit) -> Unit = { pending = it }
        fun respond(result: Boolean) = pending!!.invoke(result)
    }

    @Test
    fun undecidedUntilTheSettingIsKnown() {
        assertNull(lock.locked.value)
        lock.apply(AppLockSettings(enabled = false))
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun locksOnColdStartWhenOn() {
        lock.apply(AppLockSettings(enabled = true))
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun turningLockOnWhileUsingTheAppDoesNotLockImmediately() {
        lock.apply(AppLockSettings(enabled = false))
        lock.apply(AppLockSettings(enabled = true))
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun relocksOnlyAfterTheChosenDelay() {
        lock.apply(AppLockSettings(enabled = false))
        lock.apply(AppLockSettings(enabled = true, afterSeconds = 60))

        lock.onAppHidden()
        clock.advance(59.seconds)
        lock.onAppShown()
        assertEquals(false, lock.locked.value)

        lock.onAppHidden()
        clock.advance(60.seconds)
        lock.onAppShown()
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun immediatelyMeansEveryTimeTheAppIsLeft() {
        lock.apply(AppLockSettings(enabled = false))
        lock.apply(AppLockSettings(enabled = true, afterSeconds = 0))
        lock.onAppHidden()
        lock.onAppShown()
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun theSystemPromptHidingTheAppDoesNotCountAsLeaving() {
        lock.apply(AppLockSettings(enabled = true))
        val prompt = Prompt()
        lock.authenticate(prompt.authenticator)
        assertTrue(lock.isAuthenticating)

        // A PIN screen covers the app while the person verifies.
        lock.onAppHidden()
        clock.advance(30.seconds)
        lock.onAppShown()
        prompt.respond(true)

        assertFalse(lock.isAuthenticating)
        assertEquals(false, lock.locked.value)
    }

    @Test
    fun aFailedOrCancelledPromptStaysLocked() {
        lock.apply(AppLockSettings(enabled = true))
        val prompt = Prompt()
        var reported: Boolean? = null
        lock.authenticate(prompt.authenticator) { reported = it }
        prompt.respond(false)
        assertEquals(false, reported)
        assertEquals(true, lock.locked.value)
    }

    @Test
    fun turningLockOffUnlocks() {
        lock.apply(AppLockSettings(enabled = true))
        lock.apply(AppLockSettings(enabled = false))
        assertEquals(false, lock.locked.value)
        lock.onAppHidden()
        lock.onAppShown()
        assertEquals(false, lock.locked.value)
    }
}
