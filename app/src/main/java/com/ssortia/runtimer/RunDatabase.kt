package com.ssortia.runtimer

import android.content.Context
import android.database.sqlite.SQLiteException
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
 * Ловится только `SQLiteException`, обработчика у области нет намеренно: другое исключение
 * (например, `IllegalStateException` от Room при смене схемы без миграции) роняет процесс,
 * и запись теряется, но ошибка разработки не прячется.
 */
object RunHistory {
    private const val TAG = "RunHistory"
    private const val DATABASE_NAME = "runs.db"

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // Не by lazy: для построения нужен контекст, а он приходит только при вызове
    private var instance: RunDatabase? = null

    @Synchronized
    private fun database(context: Context): RunDatabase =
        instance ?: Room.databaseBuilder(context.applicationContext, RunDatabase::class.java, DATABASE_NAME)
            .build()
            .also { instance = it }

    /**
     * Записи, новые сверху. Ошибка чтения пишется в лог и завершает поток без нового значения:
     * экран показывает последний полученный список или ничего, а не «Пробежек пока нет».
     */
    fun runs(context: Context): Flow<List<RunRecord>> =
        database(context).runDao().runs().catch { e ->
            if (e !is SQLiteException) throw e
            Log.w(TAG, "Не удалось прочитать историю", e)
        }

    /**
     * Сохраняет запись. Отдельного wake lock нет: вставка одной строки короткая, сон процессора
     * её только приостановит, а от завершения процесса без сервиса wake lock всё равно не спасает.
     */
    fun save(context: Context, record: RunRecord) {
        val dao = database(context).runDao()
        scope.launch {
            try {
                dao.insert(record)
            } catch (e: SQLiteException) {
                Log.w(TAG, "Не удалось сохранить пробежку", e)
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
