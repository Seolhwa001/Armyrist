package com.seolhwa.armyrist.stage2.domain

import java.time.LocalDateTime
import java.time.format.DateTimeFormatter

object TemplateEngine {
    private val dateFormatter = DateTimeFormatter.ofPattern("yyyy-MM-dd")
    private val timeFormatter = DateTimeFormatter.ofPattern("HH:mm")

    fun apply(
        template: ReportTemplate?,
        toolResult: ToolResult,
        userProfile: UserProfile,
        previewTime: LocalDateTime = LocalDateTime.now()
    ): String {
        if (template == null) return toolResult.body

        val replacements = mapOf(
            "{사용자}" to userProfile.displayName,
            "{제목}" to toolResult.title,
            "{전달내용}" to toolResult.body,
            "{날짜}" to previewTime.format(dateFormatter),
            "{시간}" to previewTime.format(timeFormatter)
        )

        // Split known tokens first and append replacement values without recursively
        // interpreting tokens that may exist inside a replacement value.
        val regex = Regex(
            replacements.keys.joinToString("|") { Regex.escape(it) }
        )

        return regex.replace(template.body) { match ->
            replacements[match.value] ?: match.value
        }
    }
}
