package com.angel.launcher.money

import android.content.Intent
import android.provider.Settings
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ErrorOutline
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.TrendingDown
import androidx.compose.material.icons.outlined.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Type
import kotlinx.coroutines.delay
import java.util.Locale

private val Card = RoundedCornerShape(16.dp)
private val Small = RoundedCornerShape(14.dp)

@Composable
fun MoneyPane(model: MoneyViewModel, accent: Color) {
    var tab by remember { mutableStateOf(0) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C08090D))
            .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 24.dp),
    ) {
        Tabs(tab) { tab = it }
        if (tab == 0) Payments(model, accent) else Stocks(model, accent)
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

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Payments(model: MoneyViewModel, accent: Color) {
    val context = LocalContext.current
    val ledger by model.ledger.collectAsState()
    val scanning by model.scanning.collectAsState()
    var showSkipped by remember { mutableStateOf(false) }
    var importing by remember { mutableStateOf(false) }
    var draft by remember { mutableStateOf("") }
    // Re-read on resume: the grant happens in Settings, outside this process,
    // and a remembered value would still say "off" when you came back.
    var listenerOn by remember { mutableStateOf(PaymentNotificationListener.granted(context)) }
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                listenerOn = PaymentNotificationListener.granted(context)
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
            Figure("OUT", inr(ledger.out), Modifier.weight(1f))
            Figure("IN", inr(ledger.inn), Modifier.weight(1f))
        }

        Row(
            modifier = Modifier.padding(top = 10.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Mini("NET", signed(ledger.net), if (ledger.net >= 0) Palette.Up else Palette.Down, Modifier.weight(1f))
            Mini("ENTRIES", ledger.entries.toString(), Palette.Ink, Modifier.weight(1f))
            Mini("LARGEST", inr(ledger.largest), Palette.Ink, Modifier.weight(1f))
        }

        // The skipped bucket is deliberately visible; so is a listener that is off.
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 14.dp, bottom = 4.dp)
                .border(1.dp, Palette.HairlineLoud, RoundedCornerShape(13.dp))
                .combinedClickable(
                    onClick = {
                        if (!listenerOn) {
                            runCatching {
                                context.startActivity(
                                    Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
                                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                                )
                            }
                        } else {
                            model.rescan()
                        }
                    },
                    onLongClick = { importing = !importing },
                )
                .padding(horizontal = 13.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            Icon(
                imageVector = Icons.Outlined.Refresh,
                contentDescription = null,
                tint = Palette.Dim,
                modifier = Modifier.size(14.dp),
            )
            Text(
                text = when {
                    !listenerOn -> "NOTIFICATION ACCESS OFF · TAP TO GRANT"
                    scanning -> "READING MESSAGES"
                    else -> "${ledger.messages} MESSAGES · ${ledger.entries} MATCHED · " +
                        "${ledger.skipped.size} SKIPPED"
                },
                style = Type.MicroSmall,
                color = Palette.Dim,
            )
        }

        if (importing) {
            BasicTextField(
                value = draft,
                onValueChange = { draft = it },
                textStyle = Type.mono(11.5).copy(color = Palette.Ink),
                cursorBrush = SolidColor(Palette.Ink),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp)
                    .background(Palette.Fill, Small)
                    .border(1.dp, Palette.Hairline, Small)
                    .padding(12.dp),
            )
            Text(
                text = "IMPORT",
                style = Type.Section,
                color = accent,
                modifier = Modifier
                    .padding(top = 8.dp)
                    .clickable {
                        model.importText(draft)
                        draft = ""
                        importing = false
                    },
            )
        }

        val top = ledger.categories.take(5)
        val largest = top.firstOrNull()?.total ?: 0.0
        Column(modifier = Modifier.padding(top = 16.dp, bottom = 6.dp)) {
            top.forEach { category ->
                Column(modifier = Modifier.padding(bottom = 11.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        Text(category.key, style = Type.body(12.5), color = Palette.Ink)
                        Text(inr(category.total), style = Type.mono(10.0), color = Palette.Dim)
                    }
                    Box(
                        modifier = Modifier
                            .padding(top = 5.dp)
                            .fillMaxWidth()
                            .height(3.dp)
                            .background(Palette.Hairline, RoundedCornerShape(2.dp)),
                    ) {
                        Box(
                            modifier = Modifier
                                .fillMaxWidth(
                                    if (largest > 0) (category.total / largest).toFloat() else 0f,
                                )
                                .height(3.dp)
                                .background(Color(category.tint), RoundedCornerShape(2.dp)),
                        )
                    }
                }
            }
        }

        ledger.parsed.forEach { txn ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 11.dp, horizontal = 2.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(11.dp),
            ) {
                Box(
                    modifier = Modifier
                        .size(7.dp)
                        .background(Color(txn.category.tint), RoundedCornerShape(2.dp)),
                )
                Column(modifier = Modifier.weight(1f)) {
                    Text(txn.merchant, style = Type.body(14.0), color = Palette.Ink, maxLines = 1)
                    Text(
                        text = listOfNotNull(txn.date, txn.channel, txn.account?.let { "··$it" })
                            .joinToString(" · ")
                            .uppercase(Locale.getDefault()),
                        style = Type.mono(8.5),
                        color = Palette.Dim,
                        modifier = Modifier.padding(top = 3.dp),
                    )
                }
                Text(
                    text = (if (txn.direction == Direction.IN) "+" else "−") + inr(txn.amount),
                    style = Type.mono(12.5),
                    color = if (txn.direction == Direction.IN) Palette.Up else Palette.Ink,
                )
            }
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(1.dp)
                    .background(Palette.Ink.copy(alpha = 0.06f)),
            )
        }

        if (ledger.skipped.isNotEmpty()) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 14.dp)
                    .background(Palette.Down.copy(alpha = 0.07f), RoundedCornerShape(13.dp))
                    .border(1.dp, Palette.Down.copy(alpha = 0.2f), RoundedCornerShape(13.dp))
                    .clickable { showSkipped = !showSkipped }
                    .padding(horizontal = 13.dp, vertical = 11.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(9.dp),
            ) {
                Icon(
                    imageVector = Icons.Outlined.ErrorOutline,
                    contentDescription = null,
                    tint = Palette.Down,
                    modifier = Modifier.size(14.dp),
                )
                Text(
                    text = "${ledger.skipped.size} MESSAGES THE PARSER COULD NOT READ",
                    style = Type.MicroSmall,
                    color = Palette.Down,
                )
            }
            if (showSkipped) {
                ledger.skipped.forEach { skip ->
                    Column(modifier = Modifier.padding(vertical = 9.dp, horizontal = 2.dp)) {
                        Text(skip.raw, style = Type.body(11.5), color = Palette.Dim)
                        Text(
                            text = skip.why.uppercase(Locale.getDefault()),
                            style = Type.mono(8.0),
                            color = Palette.Down,
                            modifier = Modifier.padding(top = 4.dp),
                        )
                    }
                }
            }
        }

        Spacer(Modifier.height(24.dp))
    }
}

@Composable
private fun Stocks(model: MoneyViewModel, accent: Color) {
    val holdings by model.holdings.collectAsState()
    val spark by model.spark.collectAsState()
    val portfolio = remember(holdings) { model.portfolio() }

    LaunchedEffect(Unit) {
        while (true) {
            delay(3500)
            model.tick()
        }
    }

    Column(
        modifier = Modifier
            .padding(top = 16.dp)
            .verticalScroll(rememberScrollState()),
    ) {
        Column(modifier = Modifier.padding(bottom = 16.dp)) {
            Text("PORTFOLIO", style = Type.MicroSmall, color = Palette.Dim)
            Text(
                text = inr(portfolio.value),
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
                    text = signed(portfolio.profit) +
                        String.format(Locale.US, "  (%.2f%%)", portfolio.profitPct),
                    style = Type.mono(11.0),
                    color = if (portfolio.profit >= 0) Palette.Up else Palette.Down,
                )
            }
            if (spark.size > 2) Sparkline(spark, accent)
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
            Mini("INVESTED", inr(portfolio.cost), Palette.Ink, Modifier.weight(1f))
            Mini(
                "TODAY",
                signed(portfolio.day),
                if (portfolio.day >= 0) Palette.Up else Palette.Down,
                Modifier.weight(1f),
            )
            Mini("HOLDINGS", holdings.size.toString(), Palette.Ink, Modifier.weight(1f))
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
                    Text(holding.symbol, style = Type.mono(12.0), color = Palette.Ink)
                    Text(
                        text = "${holding.quantity} SH · AVG ${inr(holding.average)}",
                        style = Type.mono(8.5),
                        color = Palette.Dim,
                        modifier = Modifier.padding(top = 4.dp),
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(inr(holding.value), style = Type.mono(12.0), color = Palette.Ink)
                    Text(
                        text = signed(holding.profit) +
                            String.format(Locale.US, " (%.1f%%)", holding.profitPct),
                        style = Type.mono(9.0),
                        color = if (holding.profit >= 0) Palette.Up else Palette.Down,
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

        if (!model.liveQuotes) {
            Text(
                text = "PRICES SIMULATED · WIRE A QUOTES API TO GO LIVE",
                style = Type.mono(8.0),
                color = Palette.Dim,
                modifier = Modifier.padding(top = 16.dp),
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
