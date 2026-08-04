package atropos.core.agent

import atropos.core.AtroposRepoRootLocator
import java.nio.file.Path

object AgentDaemonRootResolver {
    fun resolve(
        env: Map<String, String> = System.getenv(),
        userDir: Path = Path.of(System.getProperty("user.dir"))
    ): Path {
        val explicit = env["ATROPOS_ROOT"]?.trim()?.takeIf { it.isNotBlank() }
        return if (explicit != null) {
            Path.of(explicit).toAbsolutePath().normalize()
        } else {
            AtroposRepoRootLocator.resolve(userDir)
        }
    }
}
