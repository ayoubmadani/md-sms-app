package me.capcom.smsgateway.data

import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import me.capcom.smsgateway.data.dao.MessagesDao
import me.capcom.smsgateway.data.entities.Message
import me.capcom.smsgateway.data.entities.MessageRecipient
import me.capcom.smsgateway.data.entities.MessageState
import me.capcom.smsgateway.data.entities.RecipientState
import me.capcom.smsgateway.modules.device.db.DeviceKey
import me.capcom.smsgateway.modules.device.db.DeviceKeysDao
import me.capcom.smsgateway.modules.logs.db.LogEntriesDao
import me.capcom.smsgateway.modules.logs.db.LogEntry

// MD SMS: fresh schema (v1) — the upstream migration history is not needed for a new app.
@Database(
    entities = [
        Message::class,
        MessageRecipient::class,
        RecipientState::class,
        MessageState::class,
        LogEntry::class,
        DeviceKey::class,
    ],
    version = 1,
)
@TypeConverters(Converters::class)
abstract class AppDatabase : RoomDatabase() {
    abstract fun messagesDao(): MessagesDao
    abstract fun logDao(): LogEntriesDao
    abstract fun deviceKeysDao(): DeviceKeysDao

    companion object {
        fun getDatabase(context: android.content.Context): AppDatabase {
            return Room.databaseBuilder(
                context,
                AppDatabase::class.java,
                "mdsms"
            )
                .fallbackToDestructiveMigration()
                .allowMainThreadQueries()
                .build()
        }
    }
}
