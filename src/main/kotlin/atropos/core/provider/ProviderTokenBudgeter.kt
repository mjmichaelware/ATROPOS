/* SPDX-License-Identifier: AGPL-3.0-only */
package atropos.core.provider

import java.util.Locale
import java.util.concurrent.ConcurrentHashMap
import java.util.regex.Pattern

/**
 * Dynamic token budgeter and quota capping engine.
 * Prevents credit exhaustion failures (such as HTTP 402) by calculating minimal
 * patch token budgets and dynamically clamping max_tokens based on provider limits.
 */
object ProviderTokenBudgeter {
    private const val DEFAULT_STANDARD_BUDGET = 2048
    private const val MAX_REPAIR_BUDGET = 2048
    private const val MIN_TOKEN_FLOOR = 256
    private const val BALANCE_SAFETY_MARGIN = 50

    private val providerTokenCaps = ConcurrentHashMap<String, Int>()
    private val balancePatterns = listOf(
        Pattern.compile("balance\\s+only\\s+affording\\s+(\\d+)\\s+tokens", Pattern.CASE_INSENSITIVE),
        Pattern.compile("afford(?:ing)?\\s+(\\d+)\\s+tokens", Pattern.CASE_INSENSITIVE),
        Pattern.compile("balance\\s*[:=]\\s*(\\d+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("remaining\\s*[:=]\\s*(\\d+)\\s+tokens", Pattern.CASE_INSENSITIVE),
        Pattern.compile("credit\\s+balance\\s+(?:of\\s+)?(\\d+)", Pattern.CASE_INSENSITIVE),
        Pattern.compile("(\\d+)\\s+tokens?\\s+remaining", Pattern.CASE_INSENSITIVE)
    )

    fun calculateRepairBudget(targetFileLines: Int): Int {
        val safeLines = targetFileLines.coerceAtLeast(1)
        val calculated = safeLines * 4 + 512
        return minOf(calculated, MAX_REPAIR_BUDGET)
    }

    fun budgetFor(
        providerId: String,
        requestedTokens: Int? = null,
        targetFileLines: Int? = null
    ): Int {
        val clean = providerId.trim().lowercase(Locale.US)
        val baseBudget = when {
            requestedTokens != null && requestedTokens > 0 -> requestedTokens
            targetFileLines != null && targetFileLines > 0 -> calculateRepairBudget(targetFileLines)
            else -> DEFAULT_STANDARD_BUDGET
        }

        val knownCap = providerTokenCaps[clean]
        return if (knownCap != null && knownCap > 0) {
            val clamped = (knownCap - BALANCE_SAFETY_MARGIN).coerceAtLeast(MIN_TOKEN_FLOOR)
            minOf(baseBudget, clamped)
        } else {
            baseBudget
        }
    }

    fun recordFeedback(
        providerId: String,
        statusCode: Int,
        headers: Map<String, List<String>>,
        body: String
    ) {
        val clean = providerId.trim().lowercase(Locale.US)
        val headerTokens = parseHeaderTokens(headers)
        if (headerTokens != null && headerTokens > 0) {
            recordCap(clean, headerTokens)
            return
        }

        if (statusCode == 402 || body.contains("balance", ignoreCase = true) || body.contains("affording", ignoreCase = true)) {
            val parsed = parseBodyBalance(body)
            if (parsed != null && parsed > 0) {
                recordCap(clean, parsed)
            } else if (statusCode == 402) {
                recordCap(clean, MIN_TOKEN_FLOOR)
            }
        }
    }

    private fun parseHeaderTokens(headers: Map<String, List<String>>): Int? {
        val targetHeaders = listOf(
            "x-ratelimit-remaining-tokens",
            "x-ratelimit-limit-tokens",
            "cf-aig-token-count-remaining",
            "x-openrouter-tokens-remaining"
        )
        for ((k, values) in headers) {
            val cleanKey = k.trim().lowercase(Locale.US)
            if (cleanKey in targetHeaders && values.isNotEmpty()) {
                val raw = values.firstOrNull()?.trim().orEmpty()
                raw.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    private fun parseBodyBalance(body: String): Int? {
        if (body.isBlank()) return null
        for (pattern in balancePatterns) {
            val matcher = pattern.matcher(body)
            if (matcher.find()) {
                val numStr = matcher.group(1)
                numStr?.toIntOrNull()?.let { return it }
            }
        }
        return null
    }

    fun recordCap(providerId: String, cap: Int) {
        val clean = providerId.trim().lowercase(Locale.US)
        if (cap > 0) {
            providerTokenCaps[clean] = cap
        }
    }

    fun getCap(providerId: String): Int? =
        providerTokenCaps[providerId.trim().lowercase(Locale.US)]

    fun reset() {
        providerTokenCaps.clear()
    }
}
