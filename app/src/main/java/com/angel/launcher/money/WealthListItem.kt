package com.angel.launcher.money

import com.angel.launcher.money.db.TransactionWithAccount
import java.time.LocalDate

sealed interface WealthListItem {
    data class Row(val item: TransactionWithAccount) : WealthListItem
    data class DayHeader(val date: LocalDate, val label: String, val totalPaise: Long) : WealthListItem
    data class MonthHeader(val label: String, val totalPaise: Long) : WealthListItem
}
