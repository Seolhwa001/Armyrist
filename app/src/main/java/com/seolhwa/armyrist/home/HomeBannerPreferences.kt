package com.seolhwa.armyrist.home

import android.content.Context
import org.json.JSONObject

enum class HomeBannerMode { TIME_PLAN, NOTICE, HIDDEN }

data class HomeBannerConfig(
    val mode: HomeBannerMode = HomeBannerMode.TIME_PLAN,
    val planId: String? = null,
    val currentHeading: String = "현재 진행중인 시간계획",
    val nextHeading: String = "다음 실시사항",
    val noticeTitle: String = "오늘의 안내",
    val noticeBody: String = "설정에서 자유롭게 작성해보세요."
) {
    fun normalized() = copy(
        planId = planId?.trim()?.takeIf { it.isNotEmpty() },
        currentHeading = currentHeading.trim().take(60).ifBlank { "현재 진행중인 시간계획" },
        nextHeading = nextHeading.trim().take(60).ifBlank { "다음 실시사항" },
        noticeTitle = noticeTitle.trim().take(80).ifBlank { "오늘의 안내" },
        noticeBody = noticeBody.trim().take(1000)
    )
}

object HomeBannerCodec {
    fun encode(config: HomeBannerConfig): String {
        val c = config.normalized()
        return JSONObject().put("mode", c.mode.name).put("planId", c.planId ?: JSONObject.NULL)
            .put("currentHeading", c.currentHeading).put("nextHeading", c.nextHeading)
            .put("noticeTitle", c.noticeTitle).put("noticeBody", c.noticeBody).toString()
    }
    fun decode(raw: String?): HomeBannerConfig = runCatching {
        val j = JSONObject(raw ?: return HomeBannerConfig())
        val defaults = HomeBannerConfig()
        HomeBannerConfig(
            mode = HomeBannerMode.entries.firstOrNull { it.name == j.optString("mode") } ?: defaults.mode,
            planId = if (j.isNull("planId")) null else j.optString("planId").takeIf { it.isNotBlank() },
            currentHeading = j.optString("currentHeading", defaults.currentHeading),
            nextHeading = j.optString("nextHeading", defaults.nextHeading),
            noticeTitle = j.optString("noticeTitle", defaults.noticeTitle),
            noticeBody = j.optString("noticeBody", defaults.noticeBody)
        ).normalized()
    }.getOrDefault(HomeBannerConfig())
}

class HomeBannerPreferences(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("killomris_home_banner_v1", Context.MODE_PRIVATE)
    fun load(): HomeBannerConfig = HomeBannerCodec.decode(prefs.getString("config", null))
    fun save(value: HomeBannerConfig): Boolean = prefs.edit().putString("config", HomeBannerCodec.encode(value)).commit()
}
