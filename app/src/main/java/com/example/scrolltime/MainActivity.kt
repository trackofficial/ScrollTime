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
import android.widget.ImageButton
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen

class MainActivity : AppCompatActivity() {

    companion object {
        private const val PERMISSION_REQUEST_CODE = 100
        private const val OVERLAY_PERMISSION_REQUEST = 101
    }

    private lateinit var tvNotificationStatus: TextView
    private lateinit var tvOverlayStatus: TextView
    private lateinit var tvAccessibilityStatus: TextView
    private lateinit var btnCheckPermissions: ImageButton
    private lateinit var btnOpenSettings: ImageButton
    private lateinit var btnEnableAccessibility: ImageButton

    private var isDialogShowing = false

    override fun onCreate(savedInstanceState: Bundle?) {
        installSplashScreen()
        super.onCreate(savedInstanceState)
        setContentView(R.layout.main_screen)

        initViews()
        setupButtons()
        checkAllPermissions()
    }

    private fun initViews() {
        tvNotificationStatus = findViewById(R.id.tvNotificationStatus)
        tvOverlayStatus = findViewById(R.id.tvOverlayStatus)
        tvAccessibilityStatus = findViewById(R.id.tvAccessibilityStatus)
        btnCheckPermissions = findViewById(R.id.btnCheckPermissions)
        btnOpenSettings = findViewById(R.id.btnOpenSettings)
        btnEnableAccessibility = findViewById(R.id.btnEnableAccessibility)
    }

    private fun setupButtons() {
        btnCheckPermissions.setOnClickListener {
            checkAllPermissions()
            Toast.makeText(this, "Разрешения проверены", Toast.LENGTH_SHORT).show()
        }

        btnOpenSettings.setOnClickListener {
            startActivity(Intent(this, SettingsActivity::class.java))
        }

        btnEnableAccessibility.setOnClickListener {
            if (PermissionHelper.isAccessibilityServiceEnabled(this, LogTrackingService::class.java)) {
                Toast.makeText(this, "Служба доступности уже включена", Toast.LENGTH_SHORT).show()
                checkAllPermissions()
            } else {
                showAccessibilityDialog()
            }
        }
    }

    private fun checkAllPermissions() {
        val permissions = PermissionHelper.checkAllPermissions(this)
        updatePermissionsStatus(permissions)
        requestMissingPermissions(permissions)
    }

    private fun updatePermissionsStatus(permissions: Map<String, Boolean>) {
        // Уведомления
        val notificationStatus = if (permissions["notifications"] == true) "активно" else "неактивно"
        tvNotificationStatus.text = "Уведомления: "
        tvNotificationStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
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
        tvNotificationStatus.append(notificationSpan)

        // Оверлей
        val overlayStatus = if (permissions["overlay"] == true) "активно" else "неактивно"
        tvOverlayStatus.text = "Поверх других окон: "
        tvOverlayStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
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
        tvOverlayStatus.append(overlaySpan)

        // Доступность
        val accessibilityStatus = if (permissions["accessibility"] == true) "активно" else "неактивно"
        tvAccessibilityStatus.text = "Сл. Доступности: "
        tvAccessibilityStatus.setTextColor(ContextCompat.getColor(this, android.R.color.white))
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
        tvAccessibilityStatus.append(accessibilitySpan)
    }

    private fun requestMissingPermissions(permissions: Map<String, Boolean>) {
        val permissionsToRequest = mutableListOf<String>()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (!permissions["notifications"]!!) {
                permissionsToRequest.add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        if (!permissions["overlay"]!! && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(this)) {
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:$packageName")
                )
                startActivityForResult(intent, OVERLAY_PERMISSION_REQUEST)
                return
            }
        }

        if (permissionsToRequest.isNotEmpty()) {
            ActivityCompat.requestPermissions(
                this,
                permissionsToRequest.toTypedArray(),
                PERMISSION_REQUEST_CODE
            )
        }
    }

    private fun showAccessibilityDialog() {
        if (isFinishing || isDestroyed || isDialogShowing) return

        isDialogShowing = true

        try {
            AlertDialog.Builder(this, android.R.style.Theme_DeviceDefault_Light_Dialog_Alert)
                .setTitle("Включение службы доступности")
                .setMessage(
                    "Для отслеживания времени в приложениях необходимо включить службу доступности.\n\n" +
                            "ИНСТРУКЦИЯ:\n" +
                            "1️Нажмите 'Перейти в настройки'\n" +
                            "Найдите в списке 'Scroll Time'\n" +
                            "Включите переключатель\n" +
                            "Вернитесь в приложение"
                )
                .setPositiveButton("Перейти в настройки") { _, _ ->
                    isDialogShowing = false
                    PermissionHelper.openAccessibilitySettings(this)
                }
                .setNegativeButton("Позже") { _, _ ->
                    isDialogShowing = false
                }
                .setCancelable(false)
                .show()
        } catch (e: Exception) {
            isDialogShowing = false
            e.printStackTrace()
        }
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

            if (deniedPermissions.isEmpty()) {
                Toast.makeText(this, "Все разрешения предоставлены", Toast.LENGTH_SHORT).show()
                checkAllPermissions()
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == OVERLAY_PERMISSION_REQUEST) {
            checkAllPermissions()
        }
    }

    override fun onResume() {
        super.onResume()
        if (!isFinishing && !isDestroyed) {
            checkAllPermissions()
        }
    }
}