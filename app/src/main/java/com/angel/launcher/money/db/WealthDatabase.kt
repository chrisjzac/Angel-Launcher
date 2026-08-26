package com.angel.launcher.money.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

/**
 * The Wealth pane's own database — everything else on the launcher still runs
 * on DataStore (see Prefs.kt). Nothing here is ever written to a network call.
 */
@Database(
    entities = [TransactionEntity::class, AccountEntity::class, CategoryRuleEntity::class],
    version = 1,
    exportSchema = false,
)
@TypeConverters(Converters::class)
abstract class WealthDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun accountDao(): AccountDao
    abstract fun categoryRuleDao(): CategoryRuleDao

    companion object {
        @Volatile private var instance: WealthDatabase? = null

        fun get(context: Context): WealthDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    WealthDatabase::class.java,
                    "wealth.db",
                ).build().also { instance = it }
            }
    }
}
