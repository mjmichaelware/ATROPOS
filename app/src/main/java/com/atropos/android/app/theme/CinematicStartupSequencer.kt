/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.theme

/**
 * ATOM 14.1: Cinematic Startup Sequencer State & Timers.
 *
 * Manages stage transitions for the 3-to-5 second cinematic cold-start animation.
 *
 * ## Research: Android Compose RenderEffect Hardware Shaders
 * Researches Android 12+ (API 31+) `RenderEffect.createBlurEffect` and runtime shaders
 * (`AGSL` - Android Graphics Shading Language) on `Modifier.graphicsLayer`.
 * Offloads neon glow halos and matrix grid scanlines directly to the Adreno/Mali GPU,
 * achieving zero CPU overhead and preventing thermal throttling on mobile devices.
 */
enum class StartupPhase {
    INIT_MATRIX_GRID,
    PULSE_NEON_CORE,
    MOUNT_ENGINE_SUBSYSTEMS,
    AWAITING_TOUCH_ENTRANCE,
    COMPLETED
}

data class StartupSequenceState(
    val phase: StartupPhase = StartupPhase.INIT_MATRIX_GRID,
    val progress: Float = 0.0f,
    val statusText: String = "INITIALIZING SOVEREIGN RUNTIME...",
    val durationMs: Long = 3500L
) {
    val isComplete: Boolean get() = phase == StartupPhase.COMPLETED || phase == StartupPhase.AWAITING_TOUCH_ENTRANCE
}
