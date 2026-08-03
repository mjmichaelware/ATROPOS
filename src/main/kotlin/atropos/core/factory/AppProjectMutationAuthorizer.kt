package atropos.core.factory

import java.nio.file.Path

fun interface AppProjectMutationAuthorizer {
    fun requireAllowed(repoRoot: Path, target: Path)
}
