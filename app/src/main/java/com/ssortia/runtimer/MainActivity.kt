package com.ssortia.runtimer

import android.Manifest
import android.content.ActivityNotFoundException
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Color
import android.media.AudioManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.PowerManager
import android.provider.Settings
import android.util.Log
import android.widget.Toast
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
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.ssortia.runtimer.ui.RunTimerTheme
import com.ssortia.runtimer.ui.WorkoutScreen
import com.ssortia.runtimer.ui.darkStatusIcons

private const val TAG = "MainActivity"

/** Разрешение на уведомления существует с Android 13, на старых версиях запрашивать нечего. */
fun needsNotificationRequest(sdkInt: Int, granted: Boolean): Boolean =
    sdkInt >= Build.VERSION_CODES.TIRAMISU && !granted

/** Остановка при открытом запросе означает уход пользователя, если это не поворот телефона. */
fun abandonsPermissionRequest(pending: Boolean, changingConfigurations: Boolean): Boolean =
    pending && !changingConfigurations

class MainActivity : ComponentActivity() {
    private val store by lazy { SettingsStore(this) }
    // Один поток на activity: вызов в setContent создавал бы новый Flow при рекомпозиции
    private val runs by lazy { RunHistory.runs(applicationContext) }
    private val audioManager by lazy { getSystemService(AudioManager::class.java) }
    private val powerManager by lazy { getSystemService(PowerManager::class.java) }

    // Пробный сигнал ползунка. Громкость задаётся только конструктором, поэтому на каждое
    // отпускание создаётся новый Beeper, а прежний отпускается
    private var previewBeeper: Beeper? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        // Между сигналами ничего не играет, и без этого кнопки меняли бы громкость звонка.
        // Поток тот же, что в isMediaMuted(): канал сигналов следует громкости медиа
        volumeControlStream = AudioManager.STREAM_MUSIC
        enableEdgeToEdge()
        setContent {
            val state by WorkoutService.state.collectAsStateWithLifecycle()
            // Пробная дорожка не должна жить всю тренировку. Отпускается по уходу из Idle, а не по
            // «Старт»: форма видна, пока сервис не опубликовал отсчёт, и ползунок ещё можно отпустить
            val idle = state is WorkoutState.Idle
            LaunchedEffect(idle) {
                if (!idle) releasePreview()
            }
            // Второй запрос при открытом диалоге система отклоняет сразу, и тренировка
            // запустилась бы за диалогом. Флаг переживает поворот вместе с самим запросом
            var permissionPending by rememberSaveable { mutableStateOf(false) }
            // Уход фиксируется в момент ухода: ответ приходит при возвращении, когда activity
            // снова запущена, а отмена выглядит как отказ, поэтому в обработчике их не отличить
            var requestAbandoned by rememberSaveable { mutableStateOf(false) }
            LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
                if (abandonsPermissionRequest(permissionPending, isChangingConfigurations)) {
                    requestAbandoned = true
                }
            }
            // Признак неточный: белый список Doze лишь косвенно говорит о разрешённой работе в фоне.
            // Начальное значение читается сразу, иначе предупреждение мигнуло бы при первом показе.
            // Перечитывается на ON_RESUME: переключатель включают в системных настройках
            // и возвращаются кнопкой «Назад»
            var ignoringBatteryOptimizations by remember {
                mutableStateOf(readIgnoringBatteryOptimizations())
            }
            LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
                ignoringBatteryOptimizations = readIgnoringBatteryOptimizations()
            }
            // Вне WorkoutScreen: запрос должен пережить смену экрана и поворот телефона.
            // Результат не важен: без разрешения таймер работает, просто уведомления не видно
            val permissionLauncher = rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission(),
            ) {
                val abandoned = requestAbandoned
                permissionPending = false
                requestAbandoned = false
                // Если пользователь ушёл от запроса, «Старт» нажимается заново
                if (!abandoned) startWorkout()
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
                    loadVolume = store::loadSignalVolume,
                    loadVibration = store::loadVibration,
                    loadDuckMusic = store::loadDuckMusic,
                    loadStartDelay = store::loadStartDelaySec,
                    runs = runs,
                    showBackgroundWarning = !ignoringBatteryOptimizations,
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
                    onVolumeChange = ::saveVolumeAndPreview,
                    onVibrationChange = store::saveVibration,
                    onDuckMusicChange = store::saveDuckMusic,
                    onStartDelayChange = store::saveStartDelaySec,
                    onDeleteRun = { id -> RunHistory.delete(applicationContext, id) },
                    onOpenAppDetails = ::openAppDetails,
                    onStop = { stopService(Intent(this, WorkoutService::class.java)) },
                    onPause = { sendCommand(WorkoutService.pauseIntent(this)) },
                    onResume = { sendCommand(WorkoutService.resumeIntent(this)) },
                )
            }
        }
    }

    override fun onStop() {
        releasePreview()
        super.onStop()
    }

    private fun saveVolumeAndPreview(percent: Int) {
        store.saveSignalVolume(percent)
        releasePreview()
        // Иначе пробный сигнал молча не слышен, и ползунок выглядит сломанным
        if (audioManager.isMediaMuted()) {
            Toast.makeText(this, R.string.warning_muted, Toast.LENGTH_SHORT).show()
        }
        // Готовится только INTERVAL: дорожки создаются в главном потоке
        val beeper = Beeper(audioManager, resources, percent, store.loadDuckMusic(), setOf(Signal.INTERVAL))
        if (!beeper.isReady) Log.w(TAG, "Пробный сигнал не подготовлен")
        beeper.play(Signal.INTERVAL)
        previewBeeper = beeper
    }

    private fun releasePreview() {
        previewBeeper?.release()
        previewBeeper = null
    }

    /** Команда паузы или продолжения. Сервис уже на переднем плане, поэтому хватает startService(). */
    private fun sendCommand(intent: Intent) {
        try {
            startService(intent)
        } catch (e: IllegalStateException) {
            Log.w(TAG, "Система не приняла команду сервису", e)
        }
    }

    private fun readIgnoringBatteryOptimizations(): Boolean =
        powerManager.isIgnoringBatteryOptimizations(packageName)

    /** Страница приложения в системных настройках: на realme там «Использование батареи». */
    private fun openAppDetails() {
        val intent = Intent(
            Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
            Uri.fromParts("package", packageName, null),
        )
        try {
            startActivity(intent)
        } catch (e: ActivityNotFoundException) {
            // На урезанной прошивке страницы может не быть
            Log.w(TAG, "Страница приложения в настройках не найдена", e)
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
            startForegroundService(
                WorkoutService.startIntent(this, settings.toWorkout()),
            )
        } catch (e: IllegalStateException) {
            // В том числе ForegroundServiceStartNotAllowedException на API 31+: форма остаётся
            // на экране, и «Старт» можно нажать снова
            Log.w(TAG, "Система не разрешила запустить сервис", e)
        }
    }
}
