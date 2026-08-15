/* SPDX-License-Identifier: AGPL-3.0-only */
package com.atropos.android.app.bridge

import org.json.JSONObject

data class MobileAnswer(
    val value: String,
    val health: String,
    val signal: String
)

data class MobileSixAnswers(
    val objective: MobileAnswer,
    val doing: MobileAnswer,
    val why: MobileAnswer,
    val progress: MobileAnswer,
    val next: MobileAnswer,
    val evidence: MobileAnswer
)

object SixAnswersParser {
    fun parse(body: String): MobileSixAnswers? = runCatching {
        val root = JSONObject(body)
        if (!root.optBoolean("ok", false)) return null
        val ans = root.getJSONObject("answers")
        fun parseAns(key: String): MobileAnswer {
            val obj = ans.getJSONObject(key)
            return MobileAnswer(
                value = obj.getString("value"),
                health = obj.getString("health"),
                signal = obj.getString("signal")
            )
        }
        MobileSixAnswers(
            objective = parseAns("objective"),
            doing = parseAns("doing"),
            why = parseAns("why"),
            progress = parseAns("progress"),
            next = parseAns("next"),
            evidence = parseAns("evidence")
        )
    }.getOrNull()
}
