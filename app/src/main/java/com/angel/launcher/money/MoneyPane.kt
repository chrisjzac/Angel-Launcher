package com.angel.launcher.money

import android.Manifest
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.DirectionsCar
import androidx.compose.material.icons.outlined.LocalAtm
import androidx.compose.material.icons.outlined.LocalHospital
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.MarkEmailUnread
import androidx.compose.material.icons.outlined.MoreHoriz
import androidx.compose.material.icons.outlined.Movie
import androidx.compose.material.icons.outlined.ReceiptLong
import androidx.compose.material.icons.outlined.Restaurant
import androidx.compose.material.icons.outlined.ShoppingBag
import androidx.compose.material.icons.outlined.ShoppingBasket
import androidx.compose.material.icons.outlined.SwapHoriz
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.paging.LoadState
import androidx.paging.compose.LazyPagingItems
import androidx.paging.compose.collectAsLazyPagingItems
import androidx.paging.compose.itemKey
import com.angel.launcher.money.db.TxnDirection
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Type
import kotlinx.coroutines.delay
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Locale

private val Card = RoundedCornerShape(16.dp)
private val Small = RoundedCornerShape(14.dp)

@Composable
fun MoneyPane(
    model: MoneyViewModel,
    wealth: WealthViewModel,
    accent: Color,
    locked: Boolean,
    onUnlock: () -> Unit,
    onLeaveForResult: () -> Unit,
) {
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C08090D))
            .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 24.dp),
    ) {
        Tabs(tab) { tab = it }

        if (locked) UnlockBar(accent, onUnlock)

        if (tab == 0) {
            Payments(wealth, accent, locked, onLeaveForResult)
        } else {
            Stocks(model, accent, locked, onLeaveForResult)
        }
    }
}

@Composable
private fun Tabs(selected: Int, onSelect: (Int) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Fill, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Hairline, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf("PAYMENTS", "STOCKS").forEachIndexed { index, label ->
            val on = index == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (on) Palette.Ink.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(index) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Micro, color = if (on) Palette.Ink else Palette.Dim)
            }
        }
    }
}

/* ============================= Payments / Wealth ============================= */

private val SMS_PERMISSIONS = arrayOf(Manifest.permission.READ_SMS, Manifest.permission.RECEIVE_SMS)

@Composable
private fun Payments(
    wealth: WealthViewModel,
    accent: Color,
    locked: Boolean,
    onLeaveForResult: () -> Unit,
) {
    val context = LocalContext.current
    var direction by remember { mutableStateOf(TxnDirection.DEBIT) }

    val granted by wealth.smsGranted.collectAsState()
    val backfilling by wealth.backfilling.collectAsState()
    val expandedId by wealth.expandedId.collectAsState()
    val summary by (if (direction == TxnDirection.DEBIT) wealth.debitSummary else wealth.creditSummary).collectAsState()

    val permissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { wealth.onPermissionResult(it) }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) wealth.refreshPermissionState()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(modifier = Modifier.padding(top = 16.dp).fillMaxSize()) {
        WealthTabs(direction) { direction = it }

        if (!granted) {
            PermissionBar {
                onLeaveForResult()
                permissionLauncher.launch(SMS_PERMISSIONS)
            }
            Text(
                text = "Nothing to show until SMS access is granted. Everything it reads stays " +
                    "on this device — no message ever leaves the phone.",
                style = Type.Prose,
                color = Palette.Dim,
                modifier = Modifier.padding(top = 14.dp),
            )
            return@Column
        }

        if (backfilling) {
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 14.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Text("READING SMS INBOX", style = Type.MicroSmall, color = Palette.Dim)
            }
        }

        Row(
            modifier = Modifier.padding(top = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Figure(
                if (direction == TxnDirection.DEBIT) "OUT" else "IN",
                hide(locked, inrPaise(summary.totalPaise)),
                Modifier.weight(1f),
            )
            Mini("ENTRIES", hide(locked, summary.count.toString()), Palette.Ink, Modifier.weight(1f))
        }

        if (summary.accounts.isNotEmpty()) {
            Row(
                modifier = Modifier.padding(top = 10.dp).horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(7.dp),
            ) {
                summary.accounts.forEach { chip ->
                    AccountChipView(chip, locked)
                }
            }
        }

        WealthTimeline(
            pagingFlow = if (direction == TxnDirection.DEBIT) wealth.debitItems else wealth.creditItems,
            direction = direction,
            backfilling = backfilling,
            locked = locked,
            expandedId = expandedId,
            onToggle = wealth::toggleExpanded,
            onRecategorize = wealth::recategorize,
        )
    }
}

@Composable
private fun WealthTabs(selected: TxnDirection, onSelect: (TxnDirection) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(Palette.Fill, RoundedCornerShape(12.dp))
            .border(1.dp, Palette.Hairline, RoundedCornerShape(12.dp))
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(4.dp),
    ) {
        listOf(TxnDirection.DEBIT to "DEBIT", TxnDirection.CREDIT to "CREDIT").forEach { (value, label) ->
            val on = value == selected
            Box(
                modifier = Modifier
                    .weight(1f)
                    .background(
                        if (on) Palette.Ink.copy(alpha = 0.12f) else Color.Transparent,
                        RoundedCornerShape(9.dp),
                    )
                    .clickable { onSelect(value) }
                    .padding(vertical = 8.dp),
                contentAlignment = Alignment.Center,
            ) {
                Text(label, style = Type.Micro, color = if (on) Palette.Ink else Palette.Dim)
            }
        }
    }
}

@Composable
private fun PermissionBar(onGrant: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp)
            .border(1.dp, Palette.HairlineLoud, RoundedCornerShape(13.dp))
            .clickable(onClick = onGrant)
            .padding(horizontal = 13.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(Icons.Outlined.MarkEmailUnread, contentDescription = null, tint = Palette.Dim, modifier = Modifier.size(14.dp))
        Text("SMS ACCESS OFF · TAP TO GRANT", style = Type.MicroSmall, color = Palette.Dim)
    }
}

@Composable
private fun AccountChipView(chip: AccountChip, locked: Boolean) {
    Column(
        modifier = Modifier
            .background(Palette.Fill, Small)
            .border(1.dp, Palette.Hairline, Small)
            .padding(horizontal = 11.dp, vertical = 9.dp),
    ) {
        Text(hide(locked, chip.label).uppercase(Locale.getDefault()), style = Type.MicroSmall, color = Palette.Dim, maxLines = 1)
        Text(
            hide(locked, inrPaise(chip.totalPaise)),
            style = Type.mono(11.0),
            color = Palette.Ink,
            modifier = Modifier.padding(top = 5.dp),
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WealthTimeline(
    pagingFlow: kotlinx.coroutines.flow.Flow<androidx.paging.PagingData<WealthListItem>>,
    direction: TxnDirection,
    backfilling: Boolean,
    locked: Boolean,
    expandedId: Long?,
    onToggle: (Long) -> Unit,
    onRecategorize: (Long, String, String) -> Unit,
) {
    val items = pagingFlow.collectAsLazyPagingItems()
    val listState = rememberLazyListState()

    val empty = items.itemCount == 0 &&
        items.loadState.refresh is LoadState.NotLoading &&
        items.loadState.append.endOfPaginationReached

    if (empty && !backfilling) {
        Text(
            text = if (direction == TxnDirection.DEBIT) "No debits yet." else "No credits yet.",
            style = Type.Prose,
            color = Palette.Dim,
            modifier = Modifier.padding(top = 20.dp),
        )
        return
    }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            state = listState,
            modifier = Modifier.fillMaxSize().padding(top = 4.dp),
        ) {
            items(count = items.itemCount, key = items.itemKey { keyOf(it) }) { index ->
                when (val item = items[index]) {
                    is WealthListItem.Row -> WealthRow(
                        row = item,
                        expanded = expandedId == item.item.txn.id,
                        locked = locked,
                        onToggle = { onToggle(item.item.txn.id) },
                        onRecategorize = { category ->
                            onRecategorize(item.item.txn.id, item.item.txn.merchantDisplay, category)
                        },
                    )
                    is WealthListItem.DayHeader -> StickyBar(item.label, item.totalPaise, locked, loud = false)
                    is WealthListItem.MonthHeader -> StickyBar(item.label, item.totalPaise, locked, loud = true)
                    null -> Spacer(Modifier.height(1.dp))
                }
            }
        }

        val currentMonth by remember { derivedStateOf { stickyItem<WealthListItem.MonthHeader>(items, listState) } }
        val currentDay by remember { derivedStateOf { stickyItem<WealthListItem.DayHeader>(items, listState) } }

        Column(modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()) {
            currentMonth?.let { StickyBar(it.label, it.totalPaise, locked, loud = true) }
            currentDay?.let { StickyBar(it.label, it.totalPaise, locked, loud = false) }
        }
    }
}

private fun keyOf(item: WealthListItem): Any = when (item) {
    is WealthListItem.Row -> "row-${item.item.txn.id}"
    is WealthListItem.DayHeader -> "day-${item.date}"
    is WealthListItem.MonthHeader -> "month-${item.label}"
}

/** Scans loaded items above the current scroll position for the nearest header of type T. */
private inline fun <reified T : WealthListItem> stickyItem(
    items: LazyPagingItems<WealthListItem>,
    listState: androidx.compose.foundation.lazy.LazyListState,
): T? {
    if (items.itemCount == 0) return null
    val visible = listState.layoutInfo.visibleItemsInfo.firstOrNull()?.index ?: return null
    for (i in visible downTo 0) {
        val peeked = items.peek(i)
        if (peeked is T) return peeked
    }
    return null
}

@Composable
private fun StickyBar(label: String, totalPaise: Long, locked: Boolean, loud: Boolean) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .background(if (loud) Color(0xFF14161F) else Palette.Fill)
            .padding(horizontal = 4.dp, vertical = 7.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            label.uppercase(Locale.getDefault()),
            style = if (loud) Type.Section else Type.MicroSmall,
            color = if (loud) Palette.Ink else Palette.Dim,
        )
        Text(hide(locked, inrPaise(totalPaise)), style = Type.mono(9.5), color = Palette.Dim)
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun WealthRow(
    row: WealthListItem.Row,
    expanded: Boolean,
    locked: Boolean,
    onToggle: () -> Unit,
    onRecategorize: (String) -> Unit,
) {
    val txn = row.item.txn
    val account = row.item.account
    var showPicker by remember { mutableStateOf(false) }

    Column {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .combinedClickable(onClick = onToggle, onLongClick = { showPicker = !showPicker })
                .padding(vertical = 11.dp, horizontal = 2.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(11.dp),
        ) {
            Icon(
                imageVector = categoryIcon(txn.category),
                contentDescription = null,
                tint = Palette.Dim,
                modifier = Modifier.size(16.dp),
            )
            Column(modifier = Modifier.weight(1f)) {
                Text(hide(locked, txn.merchantDisplay), style = Type.body(14.0), color = Palette.Ink, maxLines = 1)
                Text(
                    text = hide(
                        locked,
                        listOfNotNull(
                            account?.userLabel ?: account?.displayName,
                            timeLabel(txn.timestampMillis),
                        ).joinToString(" · ").uppercase(Locale.getDefault()),
                    ),
                    style = Type.mono(8.5),
                    color = Palette.Dim,
                    modifier = Modifier.padding(top = 3.dp),
                )
            }
            Text(
                text = hide(
                    locked,
                    (if (txn.direction == TxnDirection.CREDIT) "+" else "−") + inrPaise(txn.amountPaise),
                ),
                style = Type.mono(12.5),
                color = if (!locked && txn.direction == TxnDirection.CREDIT) Palette.Up else Palette.Ink,
            )
        }

        if (expanded) {
            Column(modifier = Modifier.padding(start = 27.dp, end = 2.dp, bottom = 11.dp)) {
                Text(
                    text = hide(
                        locked,
                        listOfNotNull(
                            txn.mode.name,
                            txn.balanceAfterPaise?.let { "BAL " + inrPaise(it) },
                        ).joinToString(" · "),
                    ),
                    style = Type.mono(8.5),
                    color = Palette.Dim,
                )
                Text(
                    text = hide(locked, txn.rawSmsBody),
                    style = Type.body(11.0),
                    color = Palette.Dim,
                    modifier = Modifier.padding(top = 6.dp),
                )
            }
        }

        if (showPicker) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(start = 27.dp, end = 2.dp, bottom = 10.dp)
                    .horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Categorizer.ALL.forEach { category ->
                    Box(
                        modifier = Modifier
                            .background(
                                if (category == txn.category) Palette.Ink.copy(alpha = 0.14f) else Palette.Fill,
                                RoundedCornerShape(9.dp),
                            )
                            .border(1.dp, Palette.Hairline, RoundedCornerShape(9.dp))
                            .clickable {
                                onRecategorize(category)
                                showPicker = false
                            }
                            .padding(horizontal = 9.dp, vertical = 6.dp),
                    ) {
                        Text(category, style = Type.MicroSmall, color = Palette.Ink)
                    }
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Ink.copy(alpha = 0.06f)),
        )
    }
}

private val TIME_FORMAT = DateTimeFormatter.ofPattern("h:mm a", Locale.getDefault())

private fun timeLabel(millis: Long): String =
    Instant.ofEpochMilli(millis).atZone(ZoneId.systemDefault()).format(TIME_FORMAT)

private fun categoryIcon(category: String): ImageVector = when (category) {
    Categorizer.FOOD -> Icons.Outlined.Restaurant
    Categorizer.GROCERIES -> Icons.Outlined.ShoppingBasket
    Categorizer.TRANSPORT -> Icons.Outlined.DirectionsCar
    Categorizer.SHOPPING -> Icons.Outlined.ShoppingBag
    Categorizer.BILLS -> Icons.Outlined.ReceiptLong
    Categorizer.ENTERTAINMENT -> Icons.Outlined.Movie
    Categorizer.HEALTH -> Icons.Outlined.LocalHospital
    Categorizer.TRANSFERS -> Icons.Outlined.SwapHoriz
    Categorizer.CASH -> Icons.Outlined.LocalAtm
    Categorizer.INCOME -> Icons.Outlined.TrendingUp
    else -> Icons.Outlined.MoreHoriz
}

/* ============================= Stocks (unchanged) ============================= */

@Composable
private fun Stocks(
    model: MoneyViewModel,
    accent: Color,
    locked: Boolean,
    onLeaveForResult: () -> Unit,
) {
    val holdings by model.holdings.collectAsState()
    val spark by model.spark.collectAsState()
    val importResult by model.importResult.collectAsState()
    val portfolio = remember(holdings) { model.portfolio() }

    val picker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri -> if (uri != null) model.importHoldings(uri) }

    // The outcome is a line on the bar, not a dialog to dismiss.
    LaunchedEffect(importResult) {
        if (importResult != null) {
            delay(4000)
            model.clearImportResult()
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        if (holdings.isEmpty()) {
            Text(
                text = "Nothing imported yet.",
                style = Type.Prose,
                color = Palette.Dim,
                modifier = Modifier.padding(bottom = 4.dp),
            )
        }

        if (holdings.isNotEmpty()) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text("PORTFOLIO", style = Type.MicroSmall, color = Palette.Dim)
            Text(
                text = hide(locked, inr(portfolio.value)),
                style = Type.figure(42),
                color = Palette.Ink,
                modifier = Modifier.padding(top = 7.dp),
            )
            Row(
                modifier = Modifier.padding(top = 9.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = if (portfolio.profit >= 0) {
                        Icons.Outlined.TrendingUp
                    } else {
                        Icons.Outlined.TrendingDown
                    },
                    contentDescription = null,
                    tint = if (portfolio.profit >= 0) Palette.Up else Palette.Down,
                    modifier = Modifier.size(13.dp),
                )
                Text(
                    text = hide(
                        locked,
                        signed(portfolio.profit) +
                            String.format(Locale.US, "  (%.2f%%)", portfolio.profitPct),
                    ),
                    style = Type.mono(11.0),
                    color = if (locked || portfolio.profit >= 0) Palette.Up else Palette.Down,
                )
            }
            if (!locked && spark.size > 2) Sparkline(spark, accent)
        }
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .height(1.dp)
                .background(Palette.Ink.copy(alpha = 0.09f)),
        )

        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Mini("INVESTED", hide(locked, inr(portfolio.cost)), Palette.Ink, Modifier.weight(1f))
            Mini(
                "TODAY",
                hide(locked, signed(portfolio.day)),
                if (locked || portfolio.day >= 0) Palette.Up else Palette.Down,
                Modifier.weight(1f),
            )
            Mini("HOLDINGS", hide(locked, holdings.size.toString()), Palette.Ink, Modifier.weight(1f))
        }
        }

        holdings.forEach { holding ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 12.dp, horizontal = 2.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column {
                    Text(hide(locked, holding.symbol), style = Type.mono(12.0), color = Palette.Ink)
                    Text(
                        text = hide(locked, units(holding.quantity) + " · AVG " + inr(holding.average)),
                        style = Type.mono(8.5),
                        color = Palette.Dim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(hide(locked, inr(holding.value)), style = Type.mono(12.0), color = Palette.Ink)
                    Text(
                        text = hide(
                            locked,
                            signed(holding.profit) +
                                String.format(Locale.US, " (%.1f%%)", holding.profitPct),
                        ),
                        style = Type.mono(9.0),
                        color = if (locked || holding.profit >= 0) Palette.Up else Palette.Down,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Ink.copy(alpha = 0.06f)),
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 16.dp)
                .border(1.dp, Palette.HairlineLoud, RoundedCornerShape(13.dp))
                .clickable {
                    onLeaveForResult()
                    picker.launch(arrayOf("*/*"))
                }
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Add,
                contentDescription = null,
                tint = Palette.Dim,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = importResult ?: "IMPORT HOLDINGS · CSV OR XLSX",
                style = Type.MicroSmall,
                color = if (importResult != null) accent else Palette.Dim,
            )
        }

        if (holdings.isNotEmpty()) {
            Text(
                text = if (model.liveQuotes) {
                    "PRICES LIVE"
                } else {
                    "PRICES AS IMPORTED · WIRE A QUOTES API TO GO LIVE"
                },
                style = Type.mono(8.0),
                color = Palette.Dim,
                modifier = Modifier.padding(top = 10.dp),
            )
        }
        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Sparkline(points: List<Double>, accent: Color) {
    Canvas(
        modifier = Modifier
            .fillMaxWidth()
            .height(30.dp)
            .padding(top = 12.dp),
    ) {
        val low = points.min()
        val high = points.max()
        val range = (high - low).takeIf { it > 0.0 } ?: 1.0
        val path = Path()
        points.forEachIndexed { index, value ->
            val x = size.width * (index.toFloat() / (points.size - 1))
            val y = size.height - ((value - low) / range).toFloat() * size.height
            if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
        }
        drawPath(path, color = accent, style = Stroke(width = 1.5f))
    }
}

@Composable
private fun Figure(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.Fill, Card)
            .border(1.dp, Palette.Hairline, Card)
            .padding(horizontal = 14.dp, vertical = 13.dp),
    ) {
        Text(label, style = Type.MicroSmall, color = Palette.Dim)
        Text(value, style = Type.figure(25), color = Palette.Ink, modifier = Modifier.padding(top = 7.dp))
    }
}

@Composable
private fun Mini(label: String, value: String, tint: Color, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.Fill, Small)
            .border(1.dp, Palette.Hairline, Small)
            .padding(horizontal = 11.dp, vertical = 10.dp),
    ) {
        Text(label, style = Type.MicroSmall, color = Palette.Dim)
        Text(
            text = value,
            style = Type.mono(11.5),
            color = tint,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

/** A locked pane keeps its shape and loses its figures. */
private fun hide(locked: Boolean, value: String) = if (locked) "****" else value

@Composable
private fun UnlockBar(accent: Color, onUnlock: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 12.dp)
            .background(Palette.Fill, RoundedCornerShape(13.dp))
            .border(1.dp, Palette.HairlineLoud, RoundedCornerShape(13.dp))
            .clickable(onClick = onUnlock)
            .padding(horizontal = 13.dp, vertical = 11.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = Icons.Outlined.Lock,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(14.dp),
        )
        Text("UNLOCK", style = Type.Micro, color = accent)
    }
}
