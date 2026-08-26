package com.angel.launcher.money.db

import androidx.room.ColumnInfo
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey
import androidx.room.Relation

/**
 * smsSender is null for a notification-derived row — that is the only signal
 * this table keeps about provenance, and it is what dedupe reads to decide
 * whether an incoming notification is upgrading a standing row to full SMS
 * data or is itself the duplicate. See WealthRepository.
 */
@Entity(
    tableName = "transactions",
    indices = [
        Index(value = ["timestampMillis"]),
        Index(value = ["direction", "timestampMillis"]),
        Index(value = ["dedupeHash"], unique = true),
    ],
)
data class TransactionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestampMillis: Long,
    val amountPaise: Long,
    val direction: TxnDirection,
    val accountId: String,
    val merchantRaw: String,
    val merchantDisplay: String,
    val category: String,
    val mode: TxnMode,
    val balanceAfterPaise: Long?,
    val rawSmsBody: String,
    val smsSender: String?,
    @ColumnInfo(name = "dedupeHash") val dedupeHash: String,
)

data class TransactionWithAccount(
    @Embedded val txn: TransactionEntity,
    @Relation(parentColumn = "accountId", entityColumn = "id")
    val account: AccountEntity?,
)
