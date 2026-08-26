package com.angel.launcher.money.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * matchPattern is a lowercase substring matched against a transaction's
 * merchantDisplay. userCreated rows come from a long-press recategorize and
 * are checked before the built-in defaults in Categorizer, so a user's call
 * always wins over the shipped mapping.
 */
@Entity(tableName = "category_rules")
data class CategoryRuleEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val matchPattern: String,
    val category: String,
    val userCreated: Boolean,
)
