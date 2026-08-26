package com.angel.launcher.money.db

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * Auto-created from a parsed issuer + last 4 the first time they are seen.
 * id is deterministic (issuer + last4), so re-parsing the same account never
 * creates a duplicate row.
 */
@Entity(tableName = "accounts")
data class AccountEntity(
    @PrimaryKey val id: String,
    val issuer: String,
    val last4: String?,
    val type: AccountType,
    val displayName: String,
    val userLabel: String? = null,
) {
    companion object {
        fun idFor(issuer: String, last4: String?) = "$issuer|${last4.orEmpty()}"
    }
}
