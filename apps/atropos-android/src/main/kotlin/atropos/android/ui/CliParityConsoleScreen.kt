/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.android.ui

import androidx.compose.runtime.Composable

/**
 * F-AND-001 CLI Parity Console Screen.
 * Delegates to AndroidSovereignShell.
 */
@Composable
fun CliParityConsoleScreen(
    viewState: AndroidShellViewState,
    onExecuteCommand: (String) -> Unit,
    onSelectProject: (String) -> Unit
) {
    AndroidSovereignShell(
        viewState = viewState,
        onExecuteCommand = onExecuteCommand,
        onSelectProject = onSelectProject
    )
}
