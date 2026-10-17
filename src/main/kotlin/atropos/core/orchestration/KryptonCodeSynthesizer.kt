/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.orchestration

import java.nio.file.Path

/**
 * Top-level synthesizer router for KryptonStore modules.
 * Delegates cleanly between core source, storage, and test/asset synthesizers.
 */
object KryptonCodeSynthesizer {

    fun synthesize(payload: String, worktreeRoot: Path, request: WaveExecutionRequest): SynthesisResult {
        return when {
            payload.startsWith("tests/") || payload == "README.md" || payload == "LICENSE" || payload == "requirements.txt" || payload == ".env.example" ->
                KryptonTestAssetSynthesizer.synthesize(payload, worktreeRoot, request)
            payload.startsWith("krypton/wal/") || payload.startsWith("krypton/storage/") ->
                KryptonStorageSynthesizer.synthesize(payload, worktreeRoot)
            else ->
                KryptonSourceSynthesizer.synthesize(payload, worktreeRoot, request)
        }
    }
}
