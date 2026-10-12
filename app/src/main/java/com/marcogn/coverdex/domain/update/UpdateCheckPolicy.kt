package com.marcogn.coverdex.domain.update

import java.time.Duration
import java.time.Instant

/** How often the startup check runs. "Check now" (Info screen) ignores it, also with [NEVER]. */
enum class UpdateFrequency { NEVER, EVERY_START, DAILY, WEEKLY }

val DEFAULT_UPDATE_FREQUENCY = UpdateFrequency.WEEKLY

object UpdateCheckPolicy {

    /**
     * Whether the startup check is due. [lastCheckAt] is the last check that got an answer from
     * GitHub (`null` = never). A [now] *before* [lastCheckAt] means the clock went back (manual change,
     * a bad RTC): treated as due, otherwise a clock set a year ahead once would block checks for a year.
     */
    fun isDue(frequency: UpdateFrequency, lastCheckAt: Instant?, now: Instant): Boolean {
        val interval = when (frequency) {
            UpdateFrequency.NEVER -> return false
            UpdateFrequency.EVERY_START -> return true
            UpdateFrequency.DAILY -> Duration.ofDays(1)
            UpdateFrequency.WEEKLY -> Duration.ofDays(7)
        }
        if (lastCheckAt == null || now.isBefore(lastCheckAt)) return true
        return !now.isBefore(lastCheckAt.plus(interval))
    }
}
