/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.cli.ui

import atropos.cli.config.ConfigurationManager

class TerminalTheme(
    private val capabilities: ConfigurationManager
) {
    val colorEnabled: Boolean
        get() = capabilities.isColorEnabled

    fun brand(text: String): String = style(text, "1;36")
    fun success(text: String): String = style(text, "1;32")
    fun error(text: String): String = style(text, "1;31")
    fun warning(text: String): String = style(text, "33")
    fun metadata(text: String): String = style(text, "38;5;245")
    fun subdued(text: String): String = style(text, "38;5;239")
    fun strong(text: String): String = style(text, "1;37")
    fun path(text: String): String = style(text, "36")
    fun code(text: String): String = style(text, "38;5;252")
    fun headerBrand(text: String): String = style(text, "1;36;48;5;235")
    fun headerText(text: String): String = style(text, "38;5;250;48;5;235")
    fun footer(text: String): String = style(text, "38;5;245;48;5;235")
    fun selection(text: String): String = style(text, "30;46")

    fun reset(): String = if (colorEnabled) "\u001B[0m" else ""

    private fun style(text: String, code: String): String =
        if (colorEnabled && text.isNotEmpty()) {
            "\u001B[${code}m$text\u001B[0m"
        } else {
            text
        }
}
