package com.ssortia.runtimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssortia.runtimer.ui.RunTimerTheme
import com.ssortia.runtimer.ui.WorkoutScreen

/** Разрешение на уведомления существует с Android 13, на старых версиях запрашивать нечего. */
fun needsNotificationRequest(sdkInt: Int, granted: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted

class MainActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(this) }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            val state by WorkoutService.state.collectAsStateWithLifecycle()
            // Вне WorkoutScreen: запрос должен пережить смену экрана и поворот телефона.
            // Результат не важен: без разрешения таймер работает, просто уведомления не видно
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) { startWorkout() }

            RunTimerTheme {
                WorkoutScreen(
                    state = state,
                    loadSettings = store::load,
                    onStart = { settings ->
                        store.save(settings)
                        if (needsNotificationRequest(Build.VERSION.SDK_INT, notificationsGranted())) {
                            permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                        } else {
                            startWorkout()
                        }
                    },
                    onStop = { stopService(Intent(this, WorkoutService::class.java)) },
                )
            }
        }
    }

    private fun notificationsGranted(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) ==
            PackageManager.PERMISSION_GRANTED

    /**
     * Настройки берутся из хранилища, а не из замыкания: при повороте во время запроса разрешения
     * activity создаётся заново, и ответ приходит уже в новый экземпляр.
     */
    private fun startWorkout() {
        startForegroundService(WorkoutService.startIntent(this, store.load().toWorkout()))
    }
}
