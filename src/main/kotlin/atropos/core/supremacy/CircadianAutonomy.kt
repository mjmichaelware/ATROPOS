/* SPDX-License-Identifier: AGPL-3.0-only */
/**
 * B-SUP-014: Circadian/Human-Presence Autonomy
 *
 * Implements circadian rhythm awareness and human presence detection
 * to schedule autonomous operations during appropriate hours.
 */
package atropos.core.supremacy

import java.nio.file.Files
import java.nio.file.Path
import java.time.Duration
import java.time.Instant
import java.time.LocalTime
import java.time.ZoneId

object CircadianAutonomy {
    data class CircadianConfig(
        val activeStart: LocalTime = LocalTime.of(8, 0),   // 8 AM
        val activeEnd: LocalTime = LocalTime.of(22, 0),    // 10 PM
        val timezone: ZoneId = ZoneId.systemDefault(),
        val humanPresenceRequired: Boolean = false
    )

    data class AutonomyState(
        val isActivePeriod: Boolean,
        val humanPresent: Boolean,
        val nextTransition: Instant?,
        val config: CircadianConfig
    ) {
        val canRunAutonomous: Boolean
            get() = isActivePeriod && (!config.humanPresenceRequired || humanPresent)
    }

    private var config = CircadianConfig()
    private var humanPresent = false

    /**
     * Updates the circadian configuration.
     */
    fun updateConfig(newConfig: CircadianConfig) {
        config = newConfig
    }

    /**
     * Updates human presence status.
     */
    fun setHumanPresent(present: Boolean) {
        humanPresent = present
    }

    /**
     * Gets the current autonomy state.
     */
    fun getState(): AutonomyState {
        val now = LocalTime.now(config.timezone)
        val isActive = now >= config.activeStart && now <= config.activeEnd

        val nextTransition = if (isActive) {
            // Next transition is activeEnd
            Instant.now().atZone(config.timezone).toLocalDate().atTime(config.activeEnd).atZone(config.timezone).toInstant()
        } else {
            // Next transition is activeStart (tomorrow if past activeEnd)
            val today = Instant.now().atZone(config.timezone).toLocalDate()
            val nextStart = if (now.isAfter(config.activeEnd)) today.plusDays(1) else today
            nextStart.atTime(config.activeStart).atZone(config.timezone).toInstant()
        }

        return AutonomyState(
            isActivePeriod = isActive,
            humanPresent = humanPresent,
            nextTransition = nextTransition,
            config = config
        )
    }

    /**
     * Checks if autonomous operations can run now.
     */
    fun canRunNow(): Boolean = getState().canRunAutonomous

    /**
     * Persists circadian config to CAS.
     */
    fun persistConfig(casDir: Path): Path {
        Files.createDirectories(casDir)
        val file = casDir.resolve("circadian-config.json")
        val json = """
            {
                "activeStart": "${config.activeStart}",
                "activeEnd": "${config.activeEnd}",
                "timezone": "${config.timezone}",
                "humanPresenceRequired": ${config.humanPresenceRequired}
            }
        """.trimIndent()
        Files.writeString(file, json.trimIndent())
        return file
    }

    /**
     * CLI command to show circadian status.
     */
    fun showStatus(): String {
        val state = getState()
        return """
            Circadian Autonomy Status:
              Active Period: ${config.activeStart} - ${config.activeEnd} (${config.timezone})
              Currently Active: ${state.isActivePeriod}
              Human Present: ${state.humanPresent}
              Can Run Autonomous: ${state.canRunAutonomous}
              Next Transition: ${state.nextTransition}
        """.trimIndent()
    }
}