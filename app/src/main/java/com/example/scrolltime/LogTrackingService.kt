package com.example.scrolltime

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat

class LogTrackingService : AccessibilityService() {

    private lateinit var timeManager: TimeManager
    private val handler = Handler(Looper.getMainLooper())
    private var checkRunnable: Runnable? = null
    private var timerRunnable: Runnable? = null
    private var isTimerRunning = false
    private var isBlocked = false
    private var currentPackage = ""

    private data class TrackingSession(
        val packageName: String,
        var startTime: Long = 0,
        var isShorts: Boolean = false,
        var isTracking: Boolean = false
    )

    private val sessions = mutableMapOf<String, TrackingSession>()

    override fun onCreate() {
        super.onCreate()
        timeManager = TimeManager(this)
        createNotificationChannel()
        Log.d("Tracker", "Сервис запущен")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        val appConfig = AppList.apps.find { it.packageName == packageName } ?: return
        if (!appConfig.isEnabled) return

        currentPackage = packageName

        when (packageName) {
            "com.google.android.youtube" -> handleYouTube(packageName)
            "com.zhiliaoapp.musically" -> handleTikTok(packageName)
            "com.instagram.android" -> handleInstagram(packageName)
        }
    }

    private fun handleTikTok(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReels = isTikTokReels(rootNode)
        Log.d("Tracker", "TikTok: ${if (isReels) "REELS" else "Обычный"}")
        processAppUsage(packageName, isReels)
    }

    private fun handleYouTube(packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        val isShorts = isYouTubeShorts(rootNode)

        if (!isShorts) {
            Log.d("Tracker", "📱 YouTube: Обычное видео - не отслеживаем")
            val session = sessions[packageName]
            if (session?.isTracking == true && session.isShorts) {
                Log.d("Tracker", "🔄 Выход из Shorts, останавливаем таймер")
                stopSimpleTimer()
                session.isTracking = false
            }
            return
        }

        Log.d("Tracker", "📱 YouTube: SHORTS ✅")
        processAppUsage(packageName, true)
    }
    private fun handleInstagram(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReels = isInstagramReels(rootNode)
        Log.d("Tracker", "Instagram: ${if (isReels) "REELS" else "Обычный"}")
        processAppUsage(packageName, isReels)
    }

    private fun isTikTokReels(node: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            val viewId = current.viewIdResourceName ?: ""
            if (viewId.contains("video", ignoreCase = true) ||
                viewId.contains("reel", ignoreCase = true) ||
                viewId.contains("short", ignoreCase = true)) {
                return true
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun isYouTubeShorts(node: AccessibilityNodeInfo): Boolean {
        Log.d("Tracker", "🔍 НАЧАЛО ОПРЕДЕЛЕНИЯ SHORTS (упрощенный)")

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            val text = current.text?.toString() ?: ""
            val desc = current.contentDescription?.toString() ?: ""
            val viewId = current.viewIdResourceName ?: ""

            if (text.equals("Shorts", ignoreCase = true) ||
                text.equals("Шортс", ignoreCase = true) ||
                text.contains("Shorts ", ignoreCase = true) ||
                desc.contains("Shorts", ignoreCase = true)) {
                Log.d("Tracker", "🔍 Найден текст SHORTS: '$text'")
                return true
            }

            if (viewId.contains("shorts", ignoreCase = true) &&
                !viewId.contains("player", ignoreCase = true) &&
                !viewId.contains("video", ignoreCase = true)) {
                Log.d("Tracker", "🔍 Найден ID с shorts: $viewId")
                return true
            }

            if (current.childCount >= 3) {
                var hasLike = false
                var hasDislike = false
                var hasShare = false

                for (i in 0 until current.childCount) {
                    val child = current.getChild(i) ?: continue
                    val childDesc = child.contentDescription?.toString() ?: ""
                    val childText = child.text?.toString() ?: ""

                    if (childDesc.contains("лайк", ignoreCase = true) ||
                        childText.contains("like", ignoreCase = true)) {
                        hasLike = true
                    }
                    if (childDesc.contains("дизлайк", ignoreCase = true) ||
                        childText.contains("dislike", ignoreCase = true)) {
                        hasDislike = true
                    }
                    if (childDesc.contains("поделиться", ignoreCase = true) ||
                        childText.contains("share", ignoreCase = true)) {
                        hasShare = true
                    }

                    // Если нашли все три
                    if (hasLike && hasDislike && hasShare) {
                        Log.d("Tracker", "🔍 Найдены кнопки лайк+дизлайк+шара вместе")
                        return true
                    }
                }
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        Log.d("Tracker", "🔍 SHORTS НЕ НАЙДЕНЫ")
        return false
    }

    private fun isInstagramReels(node: AccessibilityNodeInfo): Boolean {
        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            val text = current.text?.toString() ?: ""
            if (text.contains("reel", ignoreCase = true)) {
                return true
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }
        return false
    }

    private fun processAppUsage(packageName: String, isShorts: Boolean) {
        if (isBlocked) {
            blockShorts()
            return
        }

        var session = sessions[packageName]
        if (session == null) {
            session = TrackingSession(packageName)
            sessions[packageName] = session
        }

        val appConfig = AppList.apps.find { it.packageName == packageName } ?: return

        if (isShorts && timeManager.isShortsLimitExceeded(packageName, appConfig.shortsLimitMinutes)) {
            isBlocked = true
            blockShorts()
            showNotificationAndCloseApp(
                "Лимит Shorts превышен",
                "Вы использовали ${appConfig.shortsLimitMinutes} минут Shorts"
            )
            return
        }

        if (timeManager.isAppLimitExceeded(packageName, appConfig.dailyLimitMinutes)) {
            isBlocked = true
            blockApp(packageName)
            showNotificationAndCloseApp(
                "Лимит приложения превышен",
                "Вы использовали ${appConfig.dailyLimitMinutes} минут в $packageName"
            )
            return
        }

        if (!session.isTracking) {
            session.isTracking = true
            session.startTime = System.currentTimeMillis()
            session.isShorts = isShorts

            if (isShorts) {
                Log.d("Tracker", "Начало отслеживания SHORTS: $packageName")
                startSimpleTimer(packageName)
                startPeriodicCheck(packageName)
            }

        } else if (session.isShorts != isShorts) {
            stopSimpleTimer()

            val duration = (System.currentTimeMillis() - session.startTime) / 1000
            if (session.isShorts) {
                timeManager.addShortsTime(packageName, duration)
            }
            timeManager.addTotalTime(packageName, duration)

            Log.d("Tracker", "Смена режима, добавлено ${duration}с")

            session.startTime = System.currentTimeMillis()
            session.isShorts = isShorts

            if (isShorts) {
                startSimpleTimer(packageName)
            }
        }
    }

    private fun startSimpleTimer(packageName: String) {
        if (isBlocked) return

        isTimerRunning = true

        timerRunnable = object : Runnable {
            override fun run() {
                if (!isTimerRunning || isBlocked) return

                timeManager.addShortsTime(packageName, 1)
                timeManager.addTotalTime(packageName, 1)

                val shortsTime = timeManager.getShortsTimeForApp(packageName)

                val appConfig = AppList.apps.find { it.packageName == packageName }
                if (appConfig != null && shortsTime >= appConfig.shortsLimitMinutes * 60) {
                    isBlocked = true
                    blockShorts()
                    showNotificationAndCloseApp(
                        "Лимит Shorts превышен",
                        "Вы использовали ${appConfig.shortsLimitMinutes} минут"
                    )
                    stopSimpleTimer()
                    return
                }

                handler.postDelayed(this, 1000)
            }
        }

        handler.post(timerRunnable!!)
    }

    private fun stopSimpleTimer() {
        isTimerRunning = false
        timerRunnable?.let { handler.removeCallbacks(it) }
        timerRunnable = null
    }

    private fun startPeriodicCheck(packageName: String) {
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = object : Runnable {
            override fun run() {
                if (!isBlocked) {
                    checkAndBlockIfNeeded(packageName)
                }
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(checkRunnable!!)
    }

    private fun checkAndBlockIfNeeded(packageName: String) {
        if (isBlocked) return

        val appConfig = AppList.apps.find { it.packageName == packageName } ?: return

        if (timeManager.isShortsLimitExceeded(packageName, appConfig.shortsLimitMinutes)) {
            isBlocked = true
            blockShorts()
            showNotificationAndCloseApp(
                "Лимит Shorts превышен",
                "Вы использовали ${appConfig.shortsLimitMinutes} минут Shorts"
            )
            return
        }

        if (timeManager.isAppLimitExceeded(packageName, appConfig.dailyLimitMinutes)) {
            isBlocked = true
            blockApp(packageName)
            showNotificationAndCloseApp(
                "Лимит приложения превышен",
                "Вы использовали ${appConfig.dailyLimitMinutes} минут в $packageName"
            )
        }
    }

    private fun blockShorts() {
        try {
            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val startX = (screenWidth / 2).toFloat()
            val startY = (screenHeight * 0.3f).toFloat()
            val endY = (screenHeight * 0.7f).toFloat()

            val path = android.graphics.Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)

            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 500))
            dispatchGesture(gestureBuilder.build(), null, null)

            Log.d("Tracker", "Блокировка Shorts выполнена")
        } catch (e: Exception) {
            Log.e("Tracker", "Ошибка блокировки: ${e.message}")
        }
    }

    private fun blockApp(packageName: String) {
        try {
            performGlobalAction(GLOBAL_ACTION_BACK)
            Log.d("Tracker", "Приложение заблокировано")
        } catch (e: Exception) {
            Log.e("Tracker", "Ошибка блокировки приложения: ${e.message}")
        }
    }

    private fun showNotificationAndCloseApp(title: String, message: String) {
        showNotification(title, message)
        closeApp()
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("Tracker", "Нет разрешения на уведомления")
                return
            }
        }

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(this, "block_channel")
                .setContentTitle("$title")
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            notificationManager.notify(1001, notification)
            Log.d("Tracker", "Уведомление показано")
        } catch (e: Exception) {
            Log.e("Tracker", "Ошибка показа уведомления: ${e.message}")
        }
    }

    private fun closeApp() {
        try {
            val intent = Intent(Intent.ACTION_MAIN)
            intent.addCategory(Intent.CATEGORY_HOME)
            intent.flags = Intent.FLAG_ACTIVITY_NEW_TASK
            startActivity(intent)

            stopSelf()

            Log.d("Tracker", "Приложение закрыто")
        } catch (e: Exception) {
            Log.e("Tracker", "Ошибка закрытия приложения: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "block_channel",
                "Блокировка контента",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Уведомления о превышении лимитов времени"
                setVibrationPattern(longArrayOf(0, 500, 200, 500))
                enableVibration(true)
            }

            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            notificationManager.createNotificationChannel(channel)
        }
    }

    override fun onInterrupt() {
        stopSimpleTimer()
        checkRunnable?.let { handler.removeCallbacks(it) }
        Log.d("Tracker", "Сервис прерван")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSimpleTimer()
        checkRunnable?.let { handler.removeCallbacks(it) }
        Log.d("Tracker", "Сервис остановлен")
    }
    private fun debugScreenContent(node: AccessibilityNodeInfo, depth: Int = 0, maxDepth: Int = 3) {
        if (depth > maxDepth) return

        val indent = "  ".repeat(depth)

        try {
            val text = node.text?.toString() ?: ""
            val desc = node.contentDescription?.toString() ?: ""
            val viewId = node.viewIdResourceName ?: ""
            val className = node.className?.toString() ?: ""

            if (text.isNotEmpty() || desc.isNotEmpty()) {
                Log.d("Tracker", "$indent Текст: '$text'")
                Log.d("Tracker", "$indent Описание: '$desc'")
                Log.d("Tracker", "$indent ID: '$viewId'")
                Log.d("Tracker", "$indent Класс: '$className'")
                Log.d("Tracker", "$indent---")
            }

            if (text.contains("shorts", ignoreCase = true) ||
                desc.contains("shorts", ignoreCase = true) ||
                viewId.contains("shorts", ignoreCase = true)) {
                Log.w("Tracker", "SHORTS!")
                Log.w("Tracker", "Текст: '$text'")
                Log.w("Tracker", "Описание: '$desc'")
                Log.w("Tracker", "ID: '$viewId'")
                Log.w("Tracker", "Класс: '$className'")
            }

            for (i in 0 until node.childCount) {
                node.getChild(i)?.let { debugScreenContent(it, depth + 1, maxDepth) }
            }
        } catch (e: Exception) {

        }
    }
}