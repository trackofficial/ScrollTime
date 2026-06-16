package com.example.scrolltime

data class AppConfig(
    val packageName: String,
    val appName: String,
    val dailyLimitMinutes: Int = 60,
    val shortsLimitMinutes: Int = 5,
    val isEnabled: Boolean = true,
    val iconRes: Int = 0
)

object AppList {
    val apps = listOf(
        AppConfig(
            packageName = "com.google.android.youtube",
            appName = "YouTube",
            dailyLimitMinutes = 360,
            shortsLimitMinutes = 60,
            iconRes = android.R.drawable.ic_media_play
        ),
        AppConfig(
            packageName = "com.zhiliaoapp.musically",
            appName = "TikTok",
            dailyLimitMinutes = 60,
            shortsLimitMinutes = 60,
            iconRes = android.R.drawable.ic_media_play
        ),
        AppConfig(
            packageName = "com.instagram.android",
            appName = "Instagram",
            dailyLimitMinutes = 60,
            shortsLimitMinutes = 60,
            iconRes = android.R.drawable.ic_media_play
        )
    )
}