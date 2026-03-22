package com.wenhao.record.tracking

import java.nio.charset.Charset

object TrackingTextSanitizer {

    private val gb18030: Charset = Charset.forName("GB18030")

    fun normalize(text: String?): String {
        if (text.isNullOrBlank()) return text.orEmpty()

        val repaired = runCatching {
            String(text.toByteArray(gb18030), Charsets.UTF_8)
        }.getOrDefault(text)

        val candidate = if (score(repaired) >= score(text)) repaired else text
        return candidate
            .replace("?{", "\${")
            .replace("\uFF1A\uFF0C", "\uFF1A")
            .replace("\uFF0C\uFF0C", "\uFF0C")
            .replace("\u3002\u3002", "\u3002")
            .trim()
    }

    private fun score(text: String): Int {
        var score = 0
        text.forEach { char ->
            when {
                char.code in 0x4E00..0x9FFF -> score += 3
                char.code in 0x20..0x7E -> score += 1
                char.code in setOf(0xFF0C, 0x3002, 0xFF1A, 0xFF08, 0xFF09) -> score += 1
                char == '\uFFFD' || char == '?' -> score -= 2
            }
        }
        return score
    }
}
