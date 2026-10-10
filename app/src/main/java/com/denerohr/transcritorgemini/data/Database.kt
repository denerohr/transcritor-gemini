package com.denerohr.transcritorgemini.data

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Delete
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "transcriptions")
data class TranscriptionItem(
    @PrimaryKey(autoGenerate = true)
    val id: Long = 0,
    val fileName: String,
    val timestamp: Long = System.currentTimeMillis(),
    val status: String, // PROCESSANDO, CONCLUIDO, ERRO
    val transcriptionText: String? = null,
    val summaryText: String? = null,
    val reviewText: String? = null,
    val errorMessage: String? = null,
    val mediaUri: String? = null
)

@Dao
interface TranscriptionDao {
    @Query("SELECT * FROM transcriptions ORDER BY timestamp DESC")
    fun getAll(): Flow<List<TranscriptionItem>>

    @Query("SELECT * FROM transcriptions WHERE id = :id")
    suspend fun getById(id: Long): TranscriptionItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: TranscriptionItem): Long

    @Update
    suspend fun update(item: TranscriptionItem)

    @Delete
    suspend fun delete(item: TranscriptionItem)

    @Query("DELETE FROM transcriptions")
    suspend fun clearAll()
}

@Database(entities = [TranscriptionItem::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transcriptionDao(): TranscriptionDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getDatabase(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "transcriptions_db"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
