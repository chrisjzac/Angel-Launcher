package com.angel.launcher.launcher

import androidx.activity.compose.BackHandler
import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.AcUnit
import androidx.compose.material.icons.outlined.BlurOn
import androidx.compose.material.icons.outlined.Cloud
import androidx.compose.material.icons.outlined.FlashOn
import androidx.compose.material.icons.outlined.Grain
import androidx.compose.material.icons.outlined.Refresh
import androidx.compose.material.icons.outlined.WbSunny
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.zIndex
import com.angel.launcher.ui.Metrics
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Sky
import com.angel.launcher.ui.Type
import com.angel.launcher.weather.SkyMode
import kotlinx.coroutines.delay
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.roundToInt

private val SIDE = Metrics.SIDE_PADDING_DP.dp

@Composable
fun LauncherPane(
    apps: List<LaunchableApp>,
    pinned: List<LaunchableApp>,
    icons: Map<String, ImageBitmap>,
    letter: Char?,
    sky: Sky,
    skyMode: SkyMode,
    temperature: Int?,
    now: Date,
    calm: Boolean,
    torchOn: Boolean,
    pageIndex: Int,
    onLetter: (Char?) -> Unit,
    onSkyTap: () -> Unit,
    onLaunch: (LaunchableApp) -> Unit,
    onPin: (LaunchableApp) -> Unit,
    onMove: (Int, Int) -> Unit,
    onAppInfo: (LaunchableApp) -> Unit,
    onUninstall: (LaunchableApp) -> Unit,
) {
    val byLetter = remember(apps) { apps.groupBy { it.initial } }
    val shown = if (letter == null) pinned else byLetter[letter].orEmpty()
    val pinnedKeys = remember(pinned) { pinned.map { it.key }.toSet() }

    var reordering by remember { mutableStateOf(false) }
    // Reorder is a mode, so it needs a way out that is not a labelled button.
    BackHandler(enabled = reordering) { reordering = false }
    LaunchedEffect(letter, pageIndex) { if (letter != null || pageIndex != 1) reordering = false }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(top = 34.dp, bottom = 22.dp),
    ) {
        Clock(
            now = now,
            sky = sky,
            mode = skyMode,
            temperature = temperature,
            onClear = {
                onLetter(null)
                reordering = false
            },
            onSkyTap = onSkyTap,
        )

        Row(
            modifier = Modifier
                .weight(1f)
                .padding(top = 24.dp),
        ) {
            Box(modifier = Modifier.weight(1f)) {
                if (letter != null) GhostLetter(letter)

                Column(modifier = Modifier.fillMaxSize()) {
                    SectionLabel(letter, shown.size, reordering, sky.accent)

                    if (shown.isEmpty()) {
                        Text(
                            text = if (letter == null) {
                                "Nothing pinned yet. Hold an app to keep it here."
                            } else {
                                "Nothing filed under $letter. Slide to a brighter letter."
                            },
                            style = Type.Prose,
                            color = Palette.Dim,
                            modifier = Modifier
                                .padding(start = SIDE, end = SIDE, top = 6.dp)
                                .widthIn(max = 210.dp),
                        )
                    } else if (letter == null) {
                        PinnedList(
                            apps = shown,
                            icons = icons,
                            reordering = reordering,
                            calm = calm,
                            modifier = Modifier.weight(1f),
                            onLaunch = onLaunch,
                            onPin = onPin,
                            onReorder = { reordering = true },
                            onAppInfo = onAppInfo,
                            onUninstall = onUninstall,
                            onMove = onMove,
                        )
                    } else {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(start = 14.dp, end = 8.dp),
                        ) {
                            itemsIndexed(shown, key = { _, app -> app.key }) { index, app ->
                                AppRow(
                                    app = app,
                                    icon = icons[app.key],
                                    index = index,
                                    filterKey = letter.toString(),
                                    calm = calm,
                                    isPinned = app.key in pinnedKeys,
                                    canReorder = false,
                                    onLaunch = onLaunch,
                                    onPin = onPin,
                                    onReorder = {},
                                    onAppInfo = onAppInfo,
                                    onUninstall = onUninstall,
                                )
                            }
                        }
                    }
                }
            }

            AlphabetRail(
                active = letter,
                hasApps = { byLetter[it]?.isNotEmpty() == true },
                accent = sky.accent,
                calm = calm,
                onLetter = onLetter,
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(start = SIDE, end = 56.dp, top = 14.dp),
            verticalAlignment = Alignment.Bottom,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Box(modifier = Modifier.heightIn(min = 19.dp)) {
                    if (letter != null) {
                        Text(
                            text = "TAP THE CLOCK TO RESET",
                            style = Type.Micro,
                            color = Palette.Dim.copy(alpha = 0.55f),
                        )
                    }
                }
                PageDots(pageIndex, sky.accent, modifier = Modifier.padding(top = 12.dp))
            }
            QuickAction(accent = sky.accent, torchOn = torchOn, onTorch = onTorch)
        }
    }
}

/**
 * The pinned list. Ordinary until you pick Reorder from a row's menu, at which
 * point rows are dragged rather than opened and the others part around them.
 */
@Composable
private fun PinnedList(
    apps: List<LaunchableApp>,
    icons: Map<String, ImageBitmap>,
    reordering: Boolean,
    calm: Boolean,
    modifier: Modifier = Modifier,
    onLaunch: (LaunchableApp) -> Unit,
    onPin: (LaunchableApp) -> Unit,
    onReorder: () -> Unit,
    onAppInfo: (LaunchableApp) -> Unit,
    onUninstall: (LaunchableApp) -> Unit,
    onMove: (Int, Int) -> Unit,
) {
    var dragging by remember { mutableIntStateOf(-1) }
    var offsetY by remember { mutableFloatStateOf(0f) }
    var rowPx by remember { mutableIntStateOf(0) }

    fun landing(from: Int): Int =
        if (rowPx <= 0) from
        else (from + (offsetY / rowPx).roundToInt()).coerceIn(0, apps.lastIndex)

    Column(
        modifier = modifier
            .verticalScroll(rememberScrollState())
            .padding(start = 14.dp, end = 8.dp),
    ) {
        apps.forEachIndexed { index, app ->
            val isDragged = index == dragging
            val target = if (dragging >= 0) landing(dragging) else -1
            // Rows between the source and the landing slot step aside by one row.
            val shift = when {
                dragging < 0 || isDragged -> 0f
                dragging < target && index in (dragging + 1)..target -> -rowPx.toFloat()
                dragging > target && index in target until dragging -> rowPx.toFloat()
                else -> 0f
            }

            Box(
                modifier = Modifier
                    .onSizeChanged { if (rowPx == 0) rowPx = it.height }
                    .zIndex(if (isDragged) 1f else 0f)
                    .graphicsLayer {
                        translationY = if (isDragged) offsetY else shift
                        alpha = if (isDragged) 0.9f else 1f
                    }
                    .then(
                        if (!reordering) {
                            Modifier
                        } else {
                            Modifier.pointerInput(app.key, apps.size) {
                                detectDragGestures(
                                    onDragStart = {
                                        dragging = index
                                        offsetY = 0f
                                    },
                                    onDrag = { change, delta ->
                                        change.consume()
                                        offsetY += delta.y
                                    },
                                    onDragEnd = {
                                        val to = landing(index)
                                        if (to != index) onMove(index, to)
                                        dragging = -1
                                        offsetY = 0f
                                    },
                                    onDragCancel = {
                                        dragging = -1
                                        offsetY = 0f
                                    },
                                )
                            }
                        },
                    ),
            ) {
                AppRow(
                    app = app,
                    icon = icons[app.key],
                    index = index,
                    filterKey = "pinned",
                    calm = calm,
                    isPinned = true,
                    canReorder = apps.size > 1,
                    interactive = !reordering,
                    onLaunch = onLaunch,
                    onPin = onPin,
                    onReorder = onReorder,
                    onAppInfo = onAppInfo,
                    onUninstall = onUninstall,
                )
            }
        }
    }
}

@Composable
private fun Clock(
    now: Date,
    sky: Sky,
    mode: SkyMode,
    temperature: Int?,
    onClear: () -> Unit,
    onSkyTap: () -> Unit,
) {
    val time = remember(now) { SimpleDateFormat("h:mm", Locale.getDefault()).format(now) }
    val date = remember(now) {
        SimpleDateFormat("EEEE, MMM d", Locale.getDefault()).format(now).uppercase(Locale.getDefault())
    }

    Column(modifier = Modifier.padding(horizontal = SIDE)) {
        Text(
            text = time,
            style = Type.Clock,
            color = Palette.Ink,
            modifier = Modifier.clickable(
                interactionSource = remember { MutableInteractionSource() },
                indication = null,
                onClick = onClear,
            ),
        )
        Text(
            text = date,
            style = Type.Date,
            color = Palette.Dim,
            modifier = Modifier.padding(top = 13.dp),
        )
        WeatherPill(sky, mode, temperature, onSkyTap, modifier = Modifier.padding(top = 14.dp))
    }
}

@Composable
private fun WeatherPill(
    sky: Sky,
    mode: SkyMode,
    temperature: Int?,
    onTap: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val label = when (mode) {
        SkyMode.LOCATING -> "Reading the sky"
        SkyMode.OFF -> "Location off — tap to set"
        SkyMode.MANUAL -> sky.label + " · set by hand"
        SkyMode.LIVE -> sky.label + (temperature?.let { " · $it°" } ?: "")
    }.uppercase(Locale.getDefault())

    val spin = rememberInfiniteTransition(label = "spin")
    val angle by spin.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(tween(1100, easing = LinearEasing), RepeatMode.Restart),
        label = "angle",
    )

    Row(
        modifier = modifier
            .background(Palette.Fill, CircleShape)
            .border(1.dp, Palette.Ink.copy(alpha = 0.07f), CircleShape)
            .clickable { onTap() }
            .padding(start = 8.dp, end = 10.dp, top = 5.dp, bottom = 5.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(
            imageVector = when {
                mode == SkyMode.LOCATING -> Icons.Outlined.Refresh
                sky.key == "sunny" -> Icons.Outlined.WbSunny
                sky.key == "clouds" -> Icons.Outlined.Cloud
                sky.key == "rain" -> Icons.Outlined.Grain
                sky.key == "storm" -> Icons.Outlined.FlashOn
                sky.key == "snow" -> Icons.Outlined.AcUnit
                else -> Icons.Outlined.BlurOn
            },
            contentDescription = null,
            tint = sky.accent,
            modifier = Modifier
                .size(12.dp)
                .graphicsLayer { if (mode == SkyMode.LOCATING) rotationZ = angle },
        )
        Text(text = label, style = Type.Micro, color = Palette.Dim)
    }
}

@Composable
private fun SectionLabel(letter: Char?, count: Int, reordering: Boolean, accent: Color) {
    val text: AnnotatedString = when {
        reordering -> AnnotatedString("REORDER")
        letter == null -> AnnotatedString("PINNED")
        else -> buildAnnotatedString {
            withStyle(SpanStyle(color = accent)) { append(letter.toString()) }
            append(" — $count ${if (count == 1) "APP" else "APPS"}")
        }
    }
    Text(
        text = text,
        style = Type.Section,
        color = if (reordering) accent else Palette.Dim,
        modifier = Modifier.padding(start = SIDE, end = SIDE, bottom = 14.dp),
    )
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun AppRow(
    app: LaunchableApp,
    icon: ImageBitmap?,
    index: Int,
    filterKey: String,
    calm: Boolean,
    isPinned: Boolean,
    canReorder: Boolean,
    interactive: Boolean = true,
    onLaunch: (LaunchableApp) -> Unit,
    onPin: (LaunchableApp) -> Unit,
    onReorder: () -> Unit,
    onAppInfo: (LaunchableApp) -> Unit,
    onUninstall: (LaunchableApp) -> Unit,
) {
    val entry = remember(filterKey, app.key) { Animatable(if (calm) 1f else 0f) }
    LaunchedEffect(filterKey, app.key) {
        if (calm) return@LaunchedEffect
        delay(index * 26L)
        entry.animateTo(1f, tween(260))
    }
    var menuOpen by remember { mutableStateOf(false) }

    Box {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .graphicsLayer {
                    alpha = entry.value
                    translationY = (1f - entry.value) * 7.dp.toPx()
                }
                .clip(RowShape)
                .combinedClickable(
                    enabled = interactive,
                    onClick = { onLaunch(app) },
                    onLongClick = { menuOpen = true },
                )
                .padding(horizontal = 12.dp, vertical = 11.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(15.dp),
        ) {
            if (icon != null) {
                Image(
                    bitmap = icon,
                    contentDescription = null,
                    contentScale = ContentScale.Fit,
                    modifier = Modifier
                        .size(19.dp)
                        .alpha(0.9f),
                )
            } else {
                Spacer(modifier = Modifier.size(19.dp))
            }
            Text(text = app.label, style = Type.AppRow, color = Palette.Ink)
        }

        DropdownMenu(
            expanded = menuOpen,
            onDismissRequest = { menuOpen = false },
        ) {
            MenuLine(if (isPinned) "Unpin" else "Pin") {
                onPin(app)
                menuOpen = false
            }
            if (isPinned && canReorder) {
                MenuLine("Reorder") {
                    onReorder()
                    menuOpen = false
                }
            }
            MenuLine("App info") {
                onAppInfo(app)
                menuOpen = false
            }
            MenuLine("Uninstall") {
                onUninstall(app)
                menuOpen = false
            }
        }
    }
}

@Composable
private fun MenuLine(label: String, onClick: () -> Unit) {
    DropdownMenuItem(
        text = { Text(label, style = Type.body(14.5), color = Palette.Ink) },
        onClick = onClick,
    )
}

@Composable
private fun GhostLetter(letter: Char) {
    Box(
        modifier = Modifier.fillMaxSize(),
        contentAlignment = Alignment.CenterEnd,
    ) {
        Text(
            text = letter.toString(),
            style = Type.Ghost,
            color = Palette.Ink.copy(alpha = 0.06f),
            modifier = Modifier.graphicsLayer { translationX = 30.dp.toPx() },
        )
    }
}

@Composable
fun PageDots(page: Int, accent: Color, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        repeat(3) { index ->
            val on = index == page
            Box(
                modifier = Modifier
                    .size(5.dp)
                    .background(
                        color = if (on) accent else Palette.Ink.copy(alpha = 0.22f),
                        shape = CircleShape,
                    ),
            )
        }
    }
}

/** Rounded corner used by every tappable row on the launcher page. */
val RowShape = RoundedCornerShape(13.dp)
