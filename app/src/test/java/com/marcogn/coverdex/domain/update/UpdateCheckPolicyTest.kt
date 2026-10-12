package com.marcogn.coverdex.domain.update

import java.time.Duration
import java.time.Instant
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateCheckPolicyTest {

    private val last = Instant.parse("2026-10-11T10:00:00Z")
    private fun isDue(frequency: UpdateFrequency, lastCheckAt: Instant?, now: Instant) =
        UpdateCheckPolicy.isDue(frequency, lastCheckAt, now)

    @Test
    fun `default is weekly`() {
        assertEquals(UpdateFrequency.WEEKLY, DEFAULT_UPDATE_FREQUENCY)
    }

    @Test
    fun `never is never due, even without a previous check`() {
        assertFalse(isDue(UpdateFrequency.NEVER, null, last))
        assertFalse(isDue(UpdateFrequency.NEVER, last, last.plus(Duration.ofDays(365))))
    }

    @Test
    fun `every start is always due, even right after a check`() {
        assertTrue(isDue(UpdateFrequency.EVERY_START, null, last))
        assertTrue(isDue(UpdateFrequency.EVERY_START, last, last))
    }

    @Test
    fun `daily and weekly are due when never checked`() {
        assertTrue(isDue(UpdateFrequency.DAILY, null, last))
        assertTrue(isDue(UpdateFrequency.WEEKLY, null, last))
    }

    @Test
    fun `daily is due exactly at 24 hours, not a millisecond before`() {
        val day = Duration.ofDays(1)
        assertFalse(isDue(UpdateFrequency.DAILY, last, last))
        assertFalse(isDue(UpdateFrequency.DAILY, last, last.plus(day).minusMillis(1)))
        assertTrue(isDue(UpdateFrequency.DAILY, last, last.plus(day)))
        assertTrue(isDue(UpdateFrequency.DAILY, last, last.plus(Duration.ofDays(30))))
    }

    @Test
    fun `weekly is due exactly at 7 days, not a millisecond before`() {
        val week = Duration.ofDays(7)
        assertFalse(isDue(UpdateFrequency.WEEKLY, last, last.plus(Duration.ofDays(6))))
        assertFalse(isDue(UpdateFrequency.WEEKLY, last, last.plus(week).minusMillis(1)))
        assertTrue(isDue(UpdateFrequency.WEEKLY, last, last.plus(week)))
    }

    @Test
    fun `a clock that went back before the last check makes the check due`() {
        val earlier = last.minus(Duration.ofMinutes(1))
        assertTrue(isDue(UpdateFrequency.DAILY, last, earlier))
        assertTrue(isDue(UpdateFrequency.WEEKLY, last, earlier))
        assertFalse(isDue(UpdateFrequency.NEVER, last, earlier))
    }
}
