package com.example.scrolltime

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.text.SpannableString
import android.text.Spanned
import android.text.style.ForegroundColorSpan
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.SeekBar
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.content.res.ResourcesCompat

class SettingsActivity : AppCompatActivity() {

    private lateinit var timeManager: TimeManager
    private lateinit var linearLayout: LinearLayout
    private lateinit var tvSettingsNotification: TextView
    private lateinit var tvSettingsOverlay: TextView
    private lateinit var tvSettingsAccessibility: TextView

    private val PERMISSION_REQUEST_CODE = 100

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_settings)

        timeManager = TimeManager(this)
        linearLayout = findViewById(R.id.settingsContainer)

        tvSettingsNotification = findViewById(R.id.tvSettingsNotification)
        tvSettingsOverlay = findViewById(R.id.tvSettingsOverlay)
        tvSettingsAccessibility = findViewById(R.id.tvSettingsAccessibility)

        findViewById<Button>(R.id.btnResetAll).setOnClickListener {
            resetAllSettings()
        }

        checkAndRequestPermissions()
        updatePermissionsStatus()
        loadSettings()
    }

    private fun updatePermissionsStatus() {
        val permissions = PermissionHelper.checkAllPermissions(this)

        val notificationStatus = if (permissions["notifications"] == true) "активно" else "неактивно"
        tvSettingsNotification.text = "Уведомления: "
        tvSettingsNotification.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        val notificationSpan = SpannableString(notificationStatus)
        notificationSpan.setSpan(
            ForegroundColorSpan(
                if (permissions["notifications"] == true) {
                    ContextCompat.getColor(this, R.color.green_for)
                } else {
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                }
            ),
            0,
            notificationStatus.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvSettingsNotification.append(notificationSpan)

        val overlayStatus = if (permissions["overlay"] == true) "активно" else "неактивно"
        tvSettingsOverlay.text = "Поверх других окон: "
        tvSettingsOverlay.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        val overlaySpan = SpannableString(overlayStatus)
        overlaySpan.setSpan(
            ForegroundColorSpan(
                if (permissions["overlay"] == true) {
                    ContextCompat.getColor(this, R.color.green_for)
                } else {
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                }
            ),
            0,
            overlayStatus.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvSettingsOverlay.append(overlaySpan)

        val accessibilityStatus = if (permissions["accessibility"] == true) "активно" else "неактивно"
        tvSettingsAccessibility.text = "Сл. Доступности: "
        tvSettingsAccessibility.setTextColor(ContextCompat.getColor(this, android.R.color.white))
        val accessibilitySpan = SpannableString(accessibilityStatus)
        accessibilitySpan.setSpan(
            ForegroundColorSpan(
                if (permissions["accessibility"] == true) {
                    ContextCompat.getColor(this, R.color.green_for)
                } else {
                    ContextCompat.getColor(this, android.R.color.holo_orange_dark)
                }
            ),
            0,
            accessibilityStatus.length,
            Spanned.SPAN_EXCLUSIVE_EXCLUSIVE
        )
        tvSettingsAccessibility.append(accessibilitySpan)
    }

    private fun checkAndRequestPermissions() {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.POST_NOTIFICATIONS
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }

        if (!PermissionHelper.isAccessibilityServiceEnabled(this, LogTrackingService::class.java)) {
            showAccessibilityDialog()
        }
    }

    private fun showAccessibilityDialog() {
        AlertDialog.Builder(this)
            .setTitle("Требуется служба доступности")
            .setMessage(
                "Для отслеживания времени в приложениях необходимо включить службу доступности.\n\n" +
                        "1. Нажмите 'Перейти в настройки'\n" +
                        "2. Найдите 'Scroll Time' в списке\n" +
                        "3. Включите переключатель\n" +
                        "4. Вернитесь в приложение"
            )
            .setPositiveButton("Перейти в настройки") { _, _ ->
                PermissionHelper.openAccessibilitySettings(this)
            }
            .setNegativeButton("Позже", null)
            .setCancelable(false)
            .show()
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)

        if (requestCode == PERMISSION_REQUEST_CODE) {
            val deniedPermissions = mutableListOf<String>()
            permissions.forEachIndexed { index, permission ->
                if (grantResults[index] != PackageManager.PERMISSION_GRANTED) {
                    deniedPermissions.add(permission)
                }
            }

            if (deniedPermissions.isNotEmpty()) {
                val message = buildString {
                    append("Для работы приложения необходимы следующие разрешения:\n\n")
                    deniedPermissions.forEach { permission ->
                        when (permission) {
                            Manifest.permission.POST_NOTIFICATIONS -> {
                                append("Уведомления - чтобы сообщать о превышении лимита\n")
                            }
                        }
                    }
                    append("\nПожалуйста, предоставьте разрешения в настройках.")
                }

                AlertDialog.Builder(this)
                    .setTitle("Необходимы разрешения")
                    .setMessage(message)
                    .setPositiveButton("Открыть настройки") { _, _ ->
                        openAppSettings()
                    }
                    .setNegativeButton("Отмена", null)
                    .show()
            } else {
                updatePermissionsStatus()
            }
        }
    }

    private fun openAppSettings() {
        val intent = Intent(Settings.ACTION_APPLICATION_DETAILS_SETTINGS)
        intent.data = Uri.parse("package:$packageName")
        startActivity(intent)
    }

    override fun onResume() {
        super.onResume()
        updatePermissionsStatus()
        updateStatistics()
    }

    private fun updateStatistics() {
        for (i in 0 until linearLayout.childCount) {
            val card = linearLayout.getChildAt(i) as? LinearLayout ?: continue
            if (card.childCount >= 2) {
                val statsText = card.getChildAt(1) as? TextView
                if (statsText?.text?.toString()?.contains("Использовано сегодня") == true) {
                    val titleView = card.getChildAt(0) as? TextView
                    val packageName = when (titleView?.text?.toString()) {
                        "YouTube Shorts" -> "com.google.android.youtube"
                        "TikTok" -> "com.zhiliaoapp.musically"
                        "Instagram Reels" -> "com.instagram.android"
                        else -> null
                    }
                    if (packageName != null) {
                        val totalUsed = timeManager.getTotalTimeForApp(packageName) / 60
                        val shortsUsed = timeManager.getShortsTimeForApp(packageName) / 60
                        statsText.text = "Использовано сегодня:\nОбщее: ${totalUsed}мин  |  Короткие: ${shortsUsed}мин"
                    }
                }
            }
        }
    }

    private fun loadSettings() {
        linearLayout.removeAllViews()

        AppList.apps.forEach { appConfig ->
            val cardView = createSettingsCard(appConfig)
            linearLayout.addView(cardView)
        }
    }

    private fun createSettingsCard(appConfig: AppConfig): LinearLayout {
        val card = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(32, 24, 32, 24)

            val params = LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            )
            params.setMargins(16, 16, 16, 16)
            layoutParams = params
            setBackgroundColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.black))
        }

        val titleView = TextView(this).apply {
            text = when (appConfig.packageName) {
                "com.google.android.youtube" -> "Shorts"
                "com.zhiliaoapp.musically" -> "TikTok"
                "com.instagram.android" -> "Reels"
                else -> appConfig.appName
            }
            textSize = 20f
            setTypeface(ResourcesCompat.getFont(this@SettingsActivity, R.font.geist_semibold), android.graphics.Typeface.BOLD)
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
        }
        card.addView(titleView)

        val totalUsed = timeManager.getTotalTimeForApp(appConfig.packageName) / 60
        val shortsUsed = timeManager.getShortsTimeForApp(appConfig.packageName) / 60

        val statsText = TextView(this).apply {
            text = "Использовано сегодня:\nОбщее: ${totalUsed}мин  |  Короткие: ${shortsUsed}мин"
            textSize = 14f
            setPadding(0, 4, 0, 12)
            setTypeface(ResourcesCompat.getFont(this@SettingsActivity, R.font.geist_regular))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.darker_gray))
        }
        card.addView(statsText)

        val totalLabel = TextView(this).apply {
            text = "Общий лимит: ${appConfig.dailyLimitMinutes} минут"
            textSize = 16f
            setPadding(0, 4, 0, 2)
            setTypeface(ResourcesCompat.getFont(this@SettingsActivity, R.font.geist_regular))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
        }
        card.addView(totalLabel)

        val totalSeekBar = SeekBar(this).apply {
            max = 180
            progress = appConfig.dailyLimitMinutes
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    totalLabel.text = "Общий лимит: $progress минут"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveSetting(appConfig.packageName, "daily_limit", seekBar?.progress ?: 0)
                }
            })
        }
        card.addView(totalSeekBar)

        val shortsLabel = TextView(this).apply {
            text = "Лимит коротких: ${appConfig.shortsLimitMinutes} минут"
            textSize = 16f
            setPadding(0, 8, 0, 2)
            setTypeface(ResourcesCompat.getFont(this@SettingsActivity, R.font.geist_regular))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
        }
        card.addView(shortsLabel)

        val shortsSeekBar = SeekBar(this).apply {
            max = 30
            progress = appConfig.shortsLimitMinutes
            setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
                override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                    shortsLabel.text = "Лимит коротких: $progress минут"
                }
                override fun onStartTrackingTouch(seekBar: SeekBar?) {}
                override fun onStopTrackingTouch(seekBar: SeekBar?) {
                    saveSetting(appConfig.packageName, "shorts_limit", seekBar?.progress ?: 0)
                }
            })
        }
        card.addView(shortsSeekBar)

        val toggleContainer = LinearLayout(this).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = android.view.Gravity.CENTER_VERTICAL
            setPadding(0, 16, 0, 0)
        }

        val toggleLabel = TextView(this).apply {
            text = "Отслеживать"
            textSize = 16f
            setTypeface(ResourcesCompat.getFont(this@SettingsActivity, R.font.geist_regular))
            setTextColor(ContextCompat.getColor(this@SettingsActivity, android.R.color.white))
        }
        toggleContainer.addView(toggleLabel)

        val toggleSwitch = Switch(this).apply {
            isChecked = appConfig.isEnabled
            setOnCheckedChangeListener { _, isChecked ->
                saveSetting(appConfig.packageName, "enabled", isChecked)
            }
        }
        toggleContainer.addView(toggleSwitch, LinearLayout.LayoutParams(
            LinearLayout.LayoutParams.WRAP_CONTENT,
            LinearLayout.LayoutParams.WRAP_CONTENT
        ).apply { marginStart = 16 })

        card.addView(toggleContainer)

        return card
    }

    private fun saveSetting(packageName: String, key: String, value: Any) {
        val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
        val editor = prefs.edit()
        when (value) {
            is Int -> editor.putInt("$packageName:$key", value)
            is Boolean -> editor.putBoolean("$packageName:$key", value)
        }
        editor.apply()

        updateStatistics()
    }

    private fun resetAllSettings() {
        AlertDialog.Builder(this)
            .setTitle("Сбросить все настройки")
            .setMessage("Вы уверены, что хотите сбросить все лимиты и статистику?")
            .setPositiveButton("Да") { _, _ ->
                val prefs = getSharedPreferences("app_settings", MODE_PRIVATE)
                prefs.edit().clear().apply()

                val timePrefs = getSharedPreferences("scroll_time_prefs", MODE_PRIVATE)
                timePrefs.edit().clear().apply()

                loadSettings()
                Toast.makeText(this, "Настройки сброшены", Toast.LENGTH_SHORT).show()
            }
            .setNegativeButton("Нет", null)
            .show()
    }
}