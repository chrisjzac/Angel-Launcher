package com.angel.launcher.money.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface CategoryRuleDao {
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(rule: CategoryRuleEntity)

    @Query("SELECT * FROM category_rules ORDER BY userCreated DESC")
    suspend fun getAll(): List<CategoryRuleEntity>

    @Query("SELECT * FROM category_rules ORDER BY userCreated DESC")
    fun observeAll(): Flow<List<CategoryRuleEntity>>
}
