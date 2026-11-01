package com.ssortia.runtimer

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssortia.runtimer.ui.RunTimerTheme
import com.ssortia.runtimer.ui.WorkoutScreen
import com.ssortia.runtimer.ui.darkStatusIcons

private const val TAG = "MainActivity"

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
            // Второй запрос при открытом диалоге система отклоняет сразу, и тренировка
            // запустилась бы за диалогом. Флаг переживает поворот вместе с самим запросом
            var permissionPending by rememberSaveable { mutableStateOf(false) }
            // Вне WorkoutScreen: запрос должен пережить смену экрана и поворот телефона.
            // Результат не важен: без разрешения таймер работает, просто уведомления не видно
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                permissionPending = false
                // Пользователь мог уйти из приложения с открытым диалогом
                if (lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) startWorkout()
            }

            // Значки следуют за цветом экрана: тёмный фон ходьбы бывает и в светлой теме
            val darkIcons = darkStatusIcons(state, isSystemInDarkTheme())
            LaunchedEffect(darkIcons) {
                enableEdgeToEdge(
                    statusBarStyle = if (darkIcons) {
                        SystemBarStyle.light(Color.TRANSPARENT, Color.TRANSPARENT)
                    } else {
                        SystemBarStyle.dark(Color.TRANSPARENT)
                    },
                )
            }

            RunTimerTheme {
                WorkoutScreen(
                    state = state,
                    loadSettings = store::load,
                    onStart = { settings ->
                        store.save(settings)
                        when {
                            !needsNotificationRequest(Build.VERSION.SDK_INT, notificationsGranted()) ->
                                startWorkout()

                            !permissionPending -> {
                                permissionPending = true
                                permissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                            }
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
        val settings = store.load()
        if (settings.validate() != null) return
        try {
            startForegroundService(WorkoutService.startIntent(this, settings.toWorkout()))
        } catch (e: IllegalStateException) {
            // В том числе ForegroundServiceStartNotAllowedException на API 31+: форма остаётся
            // на экране, и «Старт» можно нажать снова
            Log.w(TAG, "Система не разрешила запустить сервис", e)
        }
    }
}
