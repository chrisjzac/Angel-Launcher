package com.angel.launcher.money.db

import androidx.paging.PagingSource
import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import kotlinx.coroutines.flow.Flow

data class DirectionSummary(val total: Long, val count: Int)

data class AccountBreakdown(
    val accountId: String,
    val total: Long,
    val count: Int,
)

@Dao
interface TransactionDao {

    /** Returns the new rowid, or -1 if dedupeHash already existed. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insert(txn: TransactionEntity): Long

    @Query("UPDATE transactions SET category = :category WHERE id = :id")
    suspend fun setCategory(id: Long, category: String)

    @Query(
        """UPDATE transactions
           SET rawSmsBody = :rawSmsBody, smsSender = :smsSender, dedupeHash = :dedupeHash,
               balanceAfterPaise = COALESCE(:balanceAfterPaise, balanceAfterPaise)
           WHERE id = :id""",
    )
    suspend fun promoteToSms(
        id: Long,
        rawSmsBody: String,
        smsSender: String,
        dedupeHash: String,
        balanceAfterPaise: Long?,
    )

    /**
     * The other half of cross-source dedupe: same amount, same direction,
     * same account, within a few minutes of each other — a notification and
     * an SMS for the same real-world transaction rarely land at the same
     * millisecond, but always land within a few minutes of one another.
     */
    @Query(
        """SELECT * FROM transactions
           WHERE amountPaise = :amountPaise AND direction = :direction AND accountId = :accountId
             AND timestampMillis BETWEEN :fromMillis AND :toMillis
           ORDER BY ABS(timestampMillis - :centerMillis) LIMIT 1""",
    )
    suspend fun findFuzzyMatch(
        amountPaise: Long,
        direction: TxnDirection,
        accountId: String,
        centerMillis: Long,
        fromMillis: Long,
        toMillis: Long,
    ): TransactionEntity?

    @Transaction
    @Query("SELECT * FROM transactions WHERE direction = :direction ORDER BY timestampMillis DESC")
    fun pagingSource(direction: TxnDirection): PagingSource<Int, TransactionWithAccount>

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) AS total, COUNT(*) AS count
           FROM transactions WHERE direction = :direction""",
    )
    fun summary(direction: TxnDirection): Flow<DirectionSummary>

    @Query(
        """SELECT accountId, SUM(amountPaise) AS total, COUNT(*) AS count
           FROM transactions WHERE direction = :direction
           GROUP BY accountId ORDER BY total DESC""",
    )
    fun accountBreakdown(direction: TxnDirection): Flow<List<AccountBreakdown>>

    @Query(
        """SELECT COALESCE(SUM(amountPaise), 0) FROM transactions
           WHERE direction = :direction AND timestampMillis BETWEEN :fromMillis AND :toMillis""",
    )
    suspend fun periodTotal(direction: TxnDirection, fromMillis: Long, toMillis: Long): Long

    @Query("SELECT COUNT(*) FROM transactions")
    suspend fun count(): Int
}
