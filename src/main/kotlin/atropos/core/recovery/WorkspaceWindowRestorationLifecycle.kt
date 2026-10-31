package atropos.core.recovery

import java.nio.file.Path

/**
 * ATOM W13: WORKSPACE WINDOW RESTORATION LIFECYCLE
 * Path: src/main/kotlin/atropos/core/recovery/WorkspaceWindowRestorationLifecycle.kt
 *
 * Controls the behavior of reopened windows and workspaces across application restarts.
 * Supports restoring all previous windows, only folder workspaces, a single empty session,
 * or starting completely clean based on configuration.
 */

enum class WindowRestorationMode(val configValue: String) {
    ALL("all"),
    FOLDERS("folders"),
    ONE("one"),
    NONE("none");

    companion object {
        fun fromConfig(value: String?): WindowRestorationMode =
            values().firstOrNull { it.configValue.equals(value?.trim(), ignoreCase = true) } ?: ALL
    }
}

data class WindowSessionState(
    val windowId: String,
    val workspaceRoot: Path?,
    val openFiles: List<Path> = emptyList(),
    val activeFile: Path? = null,
    val isFolderWorkspace: Boolean = workspaceRoot != null
)

data class WorkspaceRestorationPlan(
    val mode: WindowRestorationMode,
    val windowsToRestore: List<WindowSessionState>,
    val shouldOpenEmptyWindow: Boolean
)

object WorkspaceWindowRestorationLifecycle {

    /**
     * Resolves the restoration plan based on configuration and cached session snapshots.
     */
    fun resolveRestorationPolicy(
        mode: WindowRestorationMode,
        previousSessions: List<WindowSessionState>
    ): WorkspaceRestorationPlan {
        return when (mode) {
            WindowRestorationMode.ALL -> {
                if (previousSessions.isEmpty()) {
                    WorkspaceRestorationPlan(mode, emptyList(), shouldOpenEmptyWindow = true)
                } else {
                    WorkspaceRestorationPlan(mode, previousSessions, shouldOpenEmptyWindow = false)
                }
            }
            WindowRestorationMode.FOLDERS -> {
                val folderWorkspaces = previousSessions.filter { it.isFolderWorkspace }
                WorkspaceRestorationPlan(
                    mode = mode,
                    windowsToRestore = folderWorkspaces,
                    shouldOpenEmptyWindow = folderWorkspaces.isEmpty()
                )
            }
            WindowRestorationMode.ONE -> {
                val primaryWindow = previousSessions.firstOrNull { it.isFolderWorkspace }
                    ?: previousSessions.firstOrNull()
                if (primaryWindow != null) {
                    WorkspaceRestorationPlan(mode, listOf(primaryWindow), shouldOpenEmptyWindow = false)
                } else {
                    WorkspaceRestorationPlan(mode, emptyList(), shouldOpenEmptyWindow = true)
                }
            }
            WindowRestorationMode.NONE -> {
                WorkspaceRestorationPlan(mode, emptyList(), shouldOpenEmptyWindow = true)
            }
        }
    }
}
