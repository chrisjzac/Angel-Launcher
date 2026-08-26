package com.angel.launcher.money

import android.Manifest
import android.app.Application
import android.content.pm.PackageManager
import androidx.core.content.ContextCompat
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import androidx.paging.insertSeparators
import androidx.paging.map
import com.angel.launcher.data.Prefs
import com.angel.launcher.money.db.AccountEntity
import com.angel.launcher.money.db.TxnDirection
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

data class AccountChip(val id: String, val label: String, val totalPaise: Long, val count: Int)

data class WealthSummary(
    val totalPaise: Long = 0,
    val count: Int = 0,
    val accounts: List<AccountChip> = emptyList(),
)

class WealthViewModel(app: Application) : AndroidViewModel(app) {

    private val repo = WealthRepository.get(app)
    private val zone = ZoneId.systemDefault()

    private val _smsGranted = MutableStateFlow(hasSmsPermission(app))
    val smsGranted: StateFlow<Boolean> = _smsGranted.asStateFlow()

    private val _backfilling = MutableStateFlow(false)
    val backfilling: StateFlow<Boolean> = _backfilling.asStateFlow()

    private val _expandedId = MutableStateFlow<Long?>(null)
    val expandedId: StateFlow<Long?> = _expandedId.asStateFlow()

    val debitItems: Flow<PagingData<WealthListItem>> = buildPager(TxnDirection.DEBIT)
    val creditItems: Flow<PagingData<WealthListItem>> = buildPager(TxnDirection.CREDIT)

    val debitSummary: StateFlow<WealthSummary> = buildSummary(TxnDirection.DEBIT)
    val creditSummary: StateFlow<WealthSummary> = buildSummary(TxnDirection.CREDIT)

    init {
        if (_smsGranted.value) maybeBackfill()
    }

    /** Re-read on resume: the grant happens in a system dialog outside this ViewModel. */
    fun refreshPermissionState() {
        val granted = hasSmsPermission(getApplication())
        _smsGranted.value = granted
        if (granted) maybeBackfill()
    }

    fun onPermissionResult(grants: Map<String, Boolean>) {
        if (grants.values.all { it }) {
            _smsGranted.value = true
            maybeBackfill()
        }
    }

    fun toggleExpanded(id: Long) {
        _expandedId.value = if (_expandedId.value == id) null else id
    }

    fun recategorize(id: Long, merchantDisplay: String, category: String) {
        viewModelScope.launch { repo.recategorize(id, merchantDisplay, category) }
    }

    private fun maybeBackfill() {
        val app = getApplication<Application>()
        viewModelScope.launch {
            if (Prefs.wealthSmsBackfilled(app).first()) return@launch
            _backfilling.value = true
            runCatching { SmsIngestor.backfill(app) }
            Prefs.setWealthSmsBackfilled(app, true)
            _backfilling.value = false
        }
    }

    private fun hasSmsPermission(app: Application): Boolean =
        ContextCompat.checkSelfPermission(app, Manifest.permission.READ_SMS) ==
            PackageManager.PERMISSION_GRANTED &&
            ContextCompat.checkSelfPermission(app, Manifest.permission.RECEIVE_SMS) ==
            PackageManager.PERMISSION_GRANTED

    private fun buildSummary(direction: TxnDirection): StateFlow<WealthSummary> =
        combine(repo.summary(direction), repo.accountBreakdown(direction), repo.accounts()) { summary, breakdown, accounts ->
            val byId = accounts.associateBy(AccountEntity::id)
            val chips = breakdown.map { row ->
                val account = byId[row.accountId]
                AccountChip(
                    id = row.accountId,
                    label = account?.userLabel ?: account?.displayName ?: row.accountId,
                    totalPaise = row.total,
                    count = row.count,
                )
            }
            WealthSummary(totalPaise = summary.total, count = summary.count, accounts = chips)
        }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), WealthSummary())

    private fun buildPager(direction: TxnDirection): Flow<PagingData<WealthListItem>> =
        Pager(PagingConfig(pageSize = 50, enablePlaceholders = false)) { repo.pagingSource(direction) }
            .flow
            .map { paging -> paging.map { WealthListItem.Row(it) as WealthListItem } }
            .map { paging -> paging.insertSeparators { before, after -> dayHeaderBetween(direction, before, after) } }
            .map { paging -> paging.insertSeparators { before, after -> monthHeaderBetween(direction, before, after) } }
            .cachedIn(viewModelScope)

    /** Every day group is started by exactly one of these — pass 2 leans on that. */
    private suspend fun dayHeaderBetween(
        direction: TxnDirection,
        before: WealthListItem?,
        after: WealthListItem?,
    ): WealthListItem? {
        val afterRow = after as? WealthListItem.Row ?: return null
        val beforeRow = before as? WealthListItem.Row
        val afterDay = localDate(afterRow.item.txn.timestampMillis)
        if (beforeRow != null && localDate(beforeRow.item.txn.timestampMillis) == afterDay) return null

        val start = afterDay.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = afterDay.plusDays(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val total = repo.periodTotal(direction, start, end)
        return WealthListItem.DayHeader(afterDay, dayLabel(afterDay), total)
    }

    /** Runs after dayHeaderBetween, so before/after may now be day headers too. */
    private suspend fun monthHeaderBetween(
        direction: TxnDirection,
        before: WealthListItem?,
        after: WealthListItem?,
    ): WealthListItem? {
        val afterDate = dateOf(after) ?: return null
        val afterMonth = afterDate.withDayOfMonth(1)
        val beforeMonth = dateOf(before)?.withDayOfMonth(1)
        if (beforeMonth == afterMonth) return null

        val start = afterMonth.atStartOfDay(zone).toInstant().toEpochMilli()
        val end = afterMonth.plusMonths(1).atStartOfDay(zone).toInstant().toEpochMilli() - 1
        val total = repo.periodTotal(direction, start, end)
        return WealthListItem.MonthHeader(monthLabel(afterMonth), total)
    }

    private fun dateOf(item: WealthListItem?): LocalDate? = when (item) {
        is WealthListItem.Row -> localDate(item.item.txn.timestampMillis)
        is WealthListItem.DayHeader -> item.date
        else -> null
    }

    private fun localDate(millis: Long): LocalDate = Instant.ofEpochMilli(millis).atZone(zone).toLocalDate()

    private fun dayLabel(date: LocalDate): String {
        val today = LocalDate.now(zone)
        return when (date) {
            today -> "Today"
            today.minusDays(1) -> "Yesterday"
            else -> date.format(DAY_FORMAT)
        }
    }

    private fun monthLabel(monthStart: LocalDate): String = monthStart.format(MONTH_FORMAT)

    companion object {
        private val DAY_FORMAT = DateTimeFormatter.ofPattern("EEE d MMM", Locale.getDefault())
        private val MONTH_FORMAT = DateTimeFormatter.ofPattern("MMMM yyyy", Locale.getDefault())
    }
}
