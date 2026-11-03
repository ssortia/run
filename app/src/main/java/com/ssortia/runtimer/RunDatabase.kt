package com.ssortia.runtimer

import android.content.Context
import android.database.sqlite.SQLiteException
import android.os.PowerManager
import android.util.Log
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Insert
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.launch

@Dao
interface RunDao {
    @Query("SELECT * FROM runs ORDER BY startedAtMs DESC")
    fun runs(): Flow<List<RunRecord>>

    @Insert
    suspend fun insert(record: RunRecord)

    @Query("DELETE FROM runs WHERE id = :id")
    suspend fun delete(id: Long)
}

@Database(entities = [RunRecord::class], version = 1, exportSchema = true)
abstract class RunDatabase : RoomDatabase() {
    abstract fun runDao(): RunDao
}

/**
 * История пробежек поверх [RunDatabase]. База одна на процесс: activity и сервис обращаются
 * к ней из разных потоков. Запись и удаление идут в собственной области корутин, потому что
 * область сервиса к моменту записи уже отменена, а activity может закрыться раньше конца удаления.
 */
object RunHistory {
    private const val TAG = "RunHistory"
    private const val DATABASE_NAME = "runs.db"
    private const val SAVE_WAKE_LOCK_TAG = "RunTimer:save-run"
    private const val SAVE_WAKE_LOCK_TIMEOUT_MS = 10_000L

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Не by lazy: для построения нужен контекст, а он приходит только при вызове
    private var instance: RunDatabase? = null

    @Synchronized
    private fun database(context: Context): RunDatabase =
        instance ?: Room.databaseBuilder(context.applicationContext, RunDatabase::class.java, DATABASE_NAME)
            .build()
            .also { instance = it }

    /**
     * Записи, новые сверху. Ошибка чтения пишется в лог и даёт пустой список, после неё поток
     * завершается. `IllegalStateException` от Room не ловится: при смене схемы это ошибка разработки.
     */
    fun runs(context: Context): Flow<List<RunRecord>> =
        database(context).runDao().runs().catch { e ->
            if (e !is SQLiteException) throw e
            Log.w(TAG, "Не удалось прочитать историю", e)
            emit(emptyList())
        }

    /**
     * Сохраняет запись под отдельным wake lock: после `onDestroy` сервиса основной wake lock
     * уже снят, и при заблокированном экране процессор мог бы уснуть до конца записи.
     * Вызывать до снятия основного wake lock, чтобы между ними не было окна.
     */
    fun save(context: Context, record: RunRecord) {
        val dao = database(context).runDao()
        // Без подсчёта ссылок и с проверкой isHeld: release() после таймаута бросил бы исключение
        val lock = context.getSystemService(PowerManager::class.java)
            .newWakeLock(PowerManager.PARTIAL_WAKE_LOCK, SAVE_WAKE_LOCK_TAG)
            .apply {
                setReferenceCounted(false)
                acquire(SAVE_WAKE_LOCK_TIMEOUT_MS)
            }
        scope.launch {
            try {
                dao.insert(record)
            } catch (e: SQLiteException) {
                Log.w(TAG, "Не удалось сохранить пробежку", e)
            } finally {
                if (lock.isHeld) lock.release()
            }
        }
    }

    fun delete(context: Context, id: Long) {
        val dao = database(context).runDao()
        scope.launch {
            try {
                dao.delete(id)
            } catch (e: SQLiteException) {
                Log.w(TAG, "Не удалось удалить пробежку $id", e)
            }
        }
    }
}
