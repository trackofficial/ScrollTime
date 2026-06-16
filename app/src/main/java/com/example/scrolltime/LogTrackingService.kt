package com.example.scrolltime

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
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
    private var isShortsBlocked = false
    private var isAppBlocked = false
    private var notificationShown = false
    private var isClosingInProgress = false
    private var lastCloseTime = 0L

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
        Log.d("Tracker", "Service started")
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val packageName = event.packageName?.toString() ?: return

        val appConfig = AppList.apps.find { it.packageName == packageName } ?: return
        if (!appConfig.isEnabled) return

        when (packageName) {
            "com.google.android.youtube" -> handleYouTube(packageName)
            "com.zhiliaoapp.musically" -> handleTikTok(packageName)
            "com.instagram.android" -> handleInstagram(packageName)
        }
    }

    private fun handleTikTok(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReels = isTikTokReels(rootNode)
        Log.d("Tracker", "TikTok: ${if (isReels) "REELS" else "Regular"}")
        processAppUsage(packageName, isReels)
    }

    private fun handleYouTube(packageName: String) {
        val rootNode = rootInActiveWindow ?: return

        val appConfig = AppList.apps.find { it.packageName == packageName }
        if (appConfig != null && timeManager.isAppLimitExceeded(packageName, appConfig.dailyLimitMinutes)) {
            isAppBlocked = true
            showNotification(
                "App limit exceeded",
                "You have used ${appConfig.dailyLimitMinutes} minutes in YouTube"
            )
            return
        }

        val isShorts = isYouTubeShorts(rootNode)

        if (!isShorts) {
            Log.d("Tracker", "YouTube: Regular video - not tracking")
            notificationShown = false
            // Если мы были в блокировке, но вышел из Shorts, снимаем флаг блокировки,
            // но только если лимит ещё не превышен (это уже сделано, но мы можем сбросить принудительно)
            // Чтобы не спамить, сбрасываем isClosingInProgress
            isClosingInProgress = false

            if (isShortsBlocked) {
                val shortsTime = timeManager.getShortsTimeForApp(packageName)
                if (appConfig != null && shortsTime < appConfig.shortsLimitMinutes * 60) {
                    isShortsBlocked = false
                    Log.d("Tracker", "Shorts block removed")
                }
            }

            val session = sessions[packageName]
            if (session?.isTracking == true && session.isShorts) {
                stopSimpleTimer()
                session.isTracking = false
                session.isShorts = false
                Log.d("Tracker", "Exited Shorts, timer stopped")
            }
            return
        }

        if (isShortsBlocked) {
            Log.d("Tracker", "Shorts blocked - closing only the Shorts video")
            closeShortsVideo()
            if (!notificationShown) {
                showNotification(
                    "Shorts limit exceeded",
                    "You have used ${appConfig?.shortsLimitMinutes ?: 5} minutes of Shorts"
                )
                notificationShown = true
            }
            return
        }

        Log.d("Tracker", "YouTube: SHORTS")
        notificationShown = false
        processAppUsage(packageName, true)
    }

    private fun handleInstagram(packageName: String) {
        val rootNode = rootInActiveWindow ?: return
        val isReels = isInstagramReels(rootNode)
        Log.d("Tracker", "Instagram: ${if (isReels) "REELS" else "Regular"}")
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
        var hasPlayer = false
        var hasShortsIndicator = false
        var hasHorizontalControls = false
        var hasLike = false
        var hasDislike = false
        var hasShare = false

        val queue = ArrayDeque<AccessibilityNodeInfo>()
        queue.add(node)

        while (queue.isNotEmpty()) {
            val current = queue.removeFirst()

            val viewId = current.viewIdResourceName ?: ""
            val className = current.className?.toString() ?: ""
            val text = current.text?.toString() ?: ""
            val desc = current.contentDescription?.toString() ?: ""

            if (viewId.contains("player", ignoreCase = true) ||
                viewId.contains("video_view", ignoreCase = true) ||
                className.contains("SurfaceView") ||
                className.contains("VideoView") ||
                className.contains("TextureView")) {
                hasPlayer = true
            }

            if (text.equals("Shorts", ignoreCase = true) ||
                text.equals("Шортс", ignoreCase = true) ||
                desc.contains("Shorts", ignoreCase = true) ||
                (viewId.contains("shorts", ignoreCase = true) &&
                        !viewId.contains("player", ignoreCase = true) &&
                        !viewId.contains("video", ignoreCase = true))) {
                hasShortsIndicator = true
            }

            if (viewId.contains("seek", ignoreCase = true) ||
                viewId.contains("progress", ignoreCase = true) ||
                viewId.contains("time", ignoreCase = true) ||
                className.contains("SeekBar") ||
                className.contains("ProgressBar")) {
                hasHorizontalControls = true
            }

            val lowerDesc = desc.lowercase()
            val lowerText = text.lowercase()
            val lowerId = viewId.lowercase()
            if (lowerDesc.contains("лайк") || lowerText.contains("like") || lowerText.contains("нравится") ||
                lowerId.contains("like")) {
                hasLike = true
            }
            if (lowerDesc.contains("дизлайк") || lowerText.contains("dislike") || lowerText.contains("не нравится") ||
                lowerId.contains("dislike")) {
                hasDislike = true
            }
            if (lowerDesc.contains("поделиться") || lowerText.contains("share") ||
                lowerId.contains("share")) {
                hasShare = true
            }

            for (i in 0 until current.childCount) {
                current.getChild(i)?.let { queue.add(it) }
            }
        }

        val isShorts = hasPlayer && hasShortsIndicator && ( (hasLike && hasDislike && hasShare) || !hasHorizontalControls )

        Log.d("Tracker", "isShorts: $isShorts, hasPlayer: $hasPlayer, hasShortsIndicator: $hasShortsIndicator, hasHorizontalControls: $hasHorizontalControls, like: $hasLike, dislike: $hasDislike, share: $hasShare")
        return isShorts
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
        val appConfig = AppList.apps.find { it.packageName == packageName } ?: return

        if (timeManager.isAppLimitExceeded(packageName, appConfig.dailyLimitMinutes)) {
            isAppBlocked = true
            showNotification(
                "App limit exceeded",
                "You have used ${appConfig.dailyLimitMinutes} minutes in $packageName"
            )
            return
        }

        if (isShorts && timeManager.isShortsLimitExceeded(packageName, appConfig.shortsLimitMinutes)) {
            isShortsBlocked = true
            closeShortsVideo()
            if (!notificationShown) {
                showNotification(
                    "Shorts limit exceeded",
                    "You have used ${appConfig.shortsLimitMinutes} minutes of Shorts"
                )
                notificationShown = true
            }
            return
        }

        if (isShorts && isShortsBlocked) {
            closeShortsVideo()
            if (!notificationShown) {
                showNotification(
                    "Shorts limit exceeded",
                    "You have used ${appConfig.shortsLimitMinutes} minutes of Shorts"
                )
                notificationShown = true
            }
            return
        }

        var session = sessions[packageName]
        if (session == null) {
            session = TrackingSession(packageName)
            sessions[packageName] = session
        }

        if (!session.isTracking) {
            session.isTracking = true
            session.startTime = System.currentTimeMillis()
            session.isShorts = isShorts

            if (isShorts) {
                Log.d("Tracker", "Started tracking SHORTS: $packageName")
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

            Log.d("Tracker", "Mode changed, added ${duration}s")

            session.startTime = System.currentTimeMillis()
            session.isShorts = isShorts

            if (isShorts) {
                startSimpleTimer(packageName)
            }
        }
    }

    private fun startSimpleTimer(packageName: String) {
        if (isAppBlocked) return

        stopSimpleTimer()
        isTimerRunning = true
        Log.d("Tracker", "Timer started for: $packageName")

        timerRunnable = object : Runnable {
            override fun run() {
                if (!isTimerRunning || isAppBlocked) {
                    return
                }

                timeManager.addShortsTime(packageName, 1)
                timeManager.addTotalTime(packageName, 1)

                val shortsTime = timeManager.getShortsTimeForApp(packageName)

                val appConfig = AppList.apps.find { it.packageName == packageName }
                if (appConfig != null && shortsTime >= appConfig.shortsLimitMinutes * 60) {
                    isShortsBlocked = true
                    closeShortsVideo()
                    if (!notificationShown) {
                        showNotification(
                            "Shorts limit exceeded",
                            "You have used ${appConfig.shortsLimitMinutes} minutes"
                        )
                        notificationShown = true
                    }
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
        timerRunnable?.let {
            handler.removeCallbacks(it)
            Log.d("Tracker", "Timer stopped")
        }
        timerRunnable = null
    }

    private fun startPeriodicCheck(packageName: String) {
        checkRunnable?.let { handler.removeCallbacks(it) }
        checkRunnable = object : Runnable {
            override fun run() {
                if (!isAppBlocked) {
                    checkAndBlockIfNeeded(packageName)
                }
                handler.postDelayed(this, 5000)
            }
        }
        handler.post(checkRunnable!!)
    }

    private fun checkAndBlockIfNeeded(packageName: String) {
        if (isAppBlocked) return

        val appConfig = AppList.apps.find { it.packageName == packageName } ?: return

        if (timeManager.isShortsLimitExceeded(packageName, appConfig.shortsLimitMinutes)) {
            isShortsBlocked = true
            closeShortsVideo()
            if (!notificationShown) {
                showNotification(
                    "Shorts limit exceeded",
                    "You have used ${appConfig.shortsLimitMinutes} minutes of Shorts"
                )
                notificationShown = true
            }
            return
        }

        if (timeManager.isAppLimitExceeded(packageName, appConfig.dailyLimitMinutes)) {
            isAppBlocked = true
            showNotification(
                "App limit exceeded",
                "You have used ${appConfig.dailyLimitMinutes} minutes in $packageName"
            )
        }
    }

    private fun closeShortsVideo() {
        val now = System.currentTimeMillis()
        if (isClosingInProgress || (now - lastCloseTime < 2000)) {
            Log.d("Tracker", "Close already in progress or too soon, skipping")
            return
        }

        isClosingInProgress = true
        lastCloseTime = now

        try {
            Log.d("Tracker", "Closing Shorts/Reels with swipe down")

            val displayMetrics = resources.displayMetrics
            val screenWidth = displayMetrics.widthPixels
            val screenHeight = displayMetrics.heightPixels

            val startX = (screenWidth / 2).toFloat()
            val startY = (screenHeight * 0.2f).toFloat()
            val endY = (screenHeight * 0.8f).toFloat()

            val path = android.graphics.Path()
            path.moveTo(startX, startY)
            path.lineTo(startX, endY)

            val gestureBuilder = GestureDescription.Builder()
            gestureBuilder.addStroke(GestureDescription.StrokeDescription(path, 0, 400))
            dispatchGesture(gestureBuilder.build(), null, null)

            Log.d("Tracker", "Swipe down performed")
        } catch (e: Exception) {
            Log.e("Tracker", "Close Shorts error: ${e.message}")
        } finally {
            handler.postDelayed({
                isClosingInProgress = false
            }, 2000)
        }
    }

    private fun showNotification(title: String, message: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    android.Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Log.d("Tracker", "No notification permission")
                return
            }
        }

        try {
            val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

            val notification = NotificationCompat.Builder(this, "block_channel")
                .setContentTitle(title)
                .setContentText(message)
                .setSmallIcon(android.R.drawable.ic_dialog_alert)
                .setAutoCancel(true)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setVibrate(longArrayOf(0, 500, 200, 500))
                .build()

            notificationManager.notify(1001, notification)
            Log.d("Tracker", "Notification shown")
        } catch (e: Exception) {
            Log.e("Tracker", "Notification error: ${e.message}")
        }
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val channel = NotificationChannel(
                "block_channel",
                "Content Blocking",
                NotificationManager.IMPORTANCE_HIGH
            ).apply {
                description = "Notifications about time limit exceeded"
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
        Log.d("Tracker", "Service interrupted")
    }

    override fun onDestroy() {
        super.onDestroy()
        stopSimpleTimer()
        checkRunnable?.let { handler.removeCallbacks(it) }
        Log.d("Tracker", "Service destroyed")
    }
}