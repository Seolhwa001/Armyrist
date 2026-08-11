package com.seolhwa.armyrist.stage2.domain

import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TemplateEngineTest {
    @Test
    fun supportedVariablesReplace() {
        val template = ReportTemplate(
            id = "t",
            name = "기본",
            body = "{사용자}|{제목}|{전달내용}|{날짜}|{시간}",
            order = 0
        )

        val result = TemplateEngine.apply(
            template = template,
            toolResult = ToolResult("제목", "본문"),
            userProfile = UserProfile("홍길동"),
            previewTime = LocalDateTime.of(2026, 8, 11, 13, 20)
        )

        assertEquals("홍길동|제목|본문|2026-08-11|13:20", result)
    }

    @Test
    fun emptyUserBecomesEmptyString() {
        val template = ReportTemplate(
            id = "t",
            name = "기본",
            body = "{사용자} / {제목}",
            order = 0
        )

        val result = TemplateEngine.apply(
            template,
            ToolResult("도구 제목", "본문"),
            UserProfile("")
        )

        assertEquals(" / 도구 제목", result)
    }

    @Test
    fun unknownTokenPreservedAndReplacementIsNotRecursive() {
        val template = ReportTemplate(
            id = "t",
            name = "기본",
            body = "{사용자} {소속}",
            order = 0
        )

        val result = TemplateEngine.apply(
            template,
            ToolResult("제목", "본문"),
            UserProfile("{시간}")
        )

        assertEquals("{시간} {소속}", result)
    }

    @Test
    fun noneReturnsToolBody() {
        val result = TemplateEngine.apply(
            null,
            ToolResult("제목", "원본 본문"),
            UserProfile("홍길동")
        )

        assertEquals("원본 본문", result)
    }
}
