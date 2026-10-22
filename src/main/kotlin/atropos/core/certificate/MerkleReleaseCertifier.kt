/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.certificate

import atropos.core.release.FinalConvergenceReleaseGate
import java.nio.file.Path

/**
 * MerkleReleaseCertifier alias in atropos.core.certificate package.
 */
class MerkleReleaseCertifier(repoRoot: Path) {
    private val delegate = FinalConvergenceReleaseGate(repoRoot)

    fun certify(version: String, commitSha: String) = delegate.sealMasterRelease(version, commitSha)
}
