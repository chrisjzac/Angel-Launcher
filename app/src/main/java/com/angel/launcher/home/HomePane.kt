package com.angel.launcher.home

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.ColumnScope
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Bolt
import androidx.compose.material.icons.outlined.Check
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.PlayArrow
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.LayoutCoordinates
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.toSize
import androidx.compose.ui.zIndex
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Type
import java.util.Locale

private val TileShape = RoundedCornerShape(17.dp)
private val GAP = 9.dp

@Composable
fun HomePane(model: HomeViewModel, accent: Color) {
    val configured by model.configured.collectAsState()
    val cards by model.cards.collectAsState()
    val all by model.all.collectAsState()

    var arranging by remember { mutableStateOf(false) }
    var picking by remember { mutableStateOf(false) }

    BackHandler(enabled = arranging || picking) {
        if (picking) picking = false else arranging = false
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0x8C08090D))
            .padding(start = 22.dp, end = 22.dp, top = 34.dp, bottom = 24.dp),
    ) {
        if (!configured) {
            Setup(accent, onSave = model::configure)
            return@Column
        }

        if (arranging) {
            ArrangeBar(
                accent = accent,
                picking = picking,
                onAdd = { picking = !picking },
                onDone = {
                    picking = false
                    arranging = false
                },
            )
        }

        if (picking) {
            Picker(
                entities = all.filter { entity -> cards.none { it.id == entity.id } },
                accent = accent,
                onAdd = model::addWidget,
                onReset = {
                    model.resetLayout()
                    picking = false
                },
            )
            return@Column
        }

        Board(
            cards = cards,
            accent = accent,
            arranging = arranging,
            loaded = all.isNotEmpty(),
            onArrange = { arranging = true },
            onAdd = {
                arranging = true
                picking = true
            },
            onToggle = model::toggle,
            onNudge = model::nudgeTarget,
            onMove = model::moveWidget,
            onWide = model::setWide,
            onRemove = model::removeWidget,
        )
    }
}

/**
 * The arrangement, packed two to a row unless a widget asked for the whole
 * width. In arrange mode the widgets are dragged rather than driven, and the
 * others part around the one in hand.
 */
@Composable
private fun Board(
    cards: List<HomeCard>,
    accent: Color,
    arranging: Boolean,
    loaded: Boolean,
    onArrange: () -> Unit,
    onAdd: () -> Unit,
    onToggle: (HaEntity) -> Unit,
    onNudge: (HaEntity, Double) -> Unit,
    onMove: (Int, Int) -> Unit,
    onWide: (String, Boolean) -> Unit,
    onRemove: (String) -> Unit,
) {
    var container by remember { mutableStateOf<LayoutCoordinates?>(null) }
    val bounds = remember { mutableStateMapOf<String, Rect>() }
    var dragging by remember { mutableStateOf<String?>(null) }
    var offset by remember { mutableStateOf(Offset.Zero) }

    /** Where the dragged widget would land: whichever slot its centre is over. */
    fun landing(from: Int): Int {
        val id = dragging ?: return from
        val start = bounds[id] ?: return from
        val centre = start.center + offset
        val over = cards.indexOfFirst { it.id != id && bounds[it.id]?.contains(centre) == true }
        return if (over >= 0) over else from
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .onGloballyPositioned { container = it },
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        for (row in pack(cards)) {
            Row(horizontalArrangement = Arrangement.spacedBy(GAP)) {
                for (card in row) {
                    val index = cards.indexOfFirst { it.id == card.id }
                    val isDragged = card.id == dragging

                    Slot(
                        card = card,
                        accent = accent,
                        arranging = arranging,
                        onArrange = onArrange,
                        onToggle = { onToggle(card.entity) },
                        onNudge = { delta -> onNudge(card.entity, delta) },
                        onWide = { wide -> onWide(card.id, wide) },
                        onRemove = { onRemove(card.id) },
                        modifier = Modifier
                            .weight(1f)
                            .onGloballyPositioned { coordinates ->
                                // Only at rest: a dragged widget's own transform
                                // would otherwise feed back into the hit test.
                                val parent = container
                                if (dragging == null && parent != null) {
                                    bounds[card.id] = Rect(
                                        parent.localPositionOf(coordinates, Offset.Zero),
                                        coordinates.size.toSize(),
                                    )
                                }
                            }
                            .zIndex(if (isDragged) 1f else 0f)
                            .graphicsLayer {
                                if (isDragged) {
                                    translationX = offset.x
                                    translationY = offset.y
                                    alpha = 0.9f
                                }
                            }
                            .then(
                                if (!arranging) {
                                    Modifier
                                } else {
                                    Modifier.pointerInput(card.id, cards.size) {
                                        detectDragGestures(
                                            onDragStart = {
                                                dragging = card.id
                                                offset = Offset.Zero
                                            },
                                            onDrag = { change, delta ->
                                                change.consume()
                                                offset += delta
                                            },
                                            onDragEnd = {
                                                val to = landing(index)
                                                if (to != index) onMove(index, to)
                                                dragging = null
                                                offset = Offset.Zero
                                            },
                                            onDragCancel = {
                                                dragging = null
                                                offset = Offset.Zero
                                            },
                                        )
                                    }
                                },
                            ),
                    )
                }
                // A lone narrow widget keeps to its half of the row.
                if (row.size == 1 && !row[0].widget.wide) Spacer(Modifier.weight(1f))
            }
        }

        // Without this an emptied pane would have no way back. Waiting on
        // `loaded` keeps it from flashing over a layout that is still resolving.
        if (arranging || (loaded && cards.isEmpty())) AddSlot(accent, onAdd)
    }
}

/** Wide widgets take a row to themselves; the rest pair up in order. */
private fun pack(cards: List<HomeCard>): List<List<HomeCard>> {
    val rows = mutableListOf<List<HomeCard>>()
    var held: HomeCard? = null
    for (card in cards) {
        val waiting = held
        if (card.widget.wide) {
            if (waiting != null) rows += listOf(waiting)
            held = null
            rows += listOf(card)
        } else if (waiting == null) {
            held = card
        } else {
            rows += listOf(waiting, card)
            held = null
        }
    }
    held?.let { rows += listOf(it) }
    return rows
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun Slot(
    card: HomeCard,
    accent: Color,
    arranging: Boolean,
    onArrange: () -> Unit,
    onToggle: () -> Unit,
    onNudge: (Double) -> Unit,
    onWide: (Boolean) -> Unit,
    onRemove: () -> Unit,
    modifier: Modifier = Modifier,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val entity = card.entity

    Box(
        modifier = modifier.combinedClickable(
            onClick = {
                if (arranging) menuOpen = true else if (entity.kind == WidgetKind.TILE) onToggle()
            },
            onLongClick = { if (!arranging) onArrange() },
        ),
    ) {
        when (entity.kind) {
            WidgetKind.THERMOSTAT -> Thermostat(entity, accent, arranging, onNudge)
            WidgetKind.SENSOR -> SensorCell(entity, arranging)
            WidgetKind.TILE -> Tile(entity, accent, arranging)
        }

        DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
            MenuLine(if (card.widget.wide) "Narrow" else "Wide") {
                onWide(!card.widget.wide)
                menuOpen = false
            }
            MenuLine("Remove") {
                onRemove()
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

/** Only ever on screen while arranging, so the pane itself stays wordless. */
@Composable
private fun ArrangeBar(
    accent: Color,
    picking: Boolean,
    onAdd: () -> Unit,
    onDone: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(if (picking) "ADD" else "ARRANGE", style = Type.Section, color = accent)
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Round(if (picking) Icons.Outlined.Remove else Icons.Outlined.Add, accent, onAdd)
            Round(Icons.Outlined.Check, accent, onDone)
        }
    }
}

@Composable
private fun Round(icon: ImageVector, accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Palette.Fill, CircleShape)
            .border(1.dp, Palette.Ink.copy(alpha = 0.10f), CircleShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
    }
}

/** Everything the house exposes that is not already placed. */
@Composable
private fun ColumnScope.Picker(
    entities: List<HaEntity>,
    accent: Color,
    onAdd: (String) -> Unit,
    onReset: () -> Unit,
) {
    LazyColumn(
        modifier = Modifier.weight(1f, fill = true),
        verticalArrangement = Arrangement.spacedBy(GAP),
    ) {
        items(entities, key = { it.id }) { entity ->
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(Palette.Fill, RoundedCornerShape(13.dp))
                    .border(1.dp, Palette.Hairline, RoundedCornerShape(13.dp))
                    .clickable { onAdd(entity.id) }
                    .padding(horizontal = 14.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(entity.name, style = Type.body(14.5), color = Palette.Ink, maxLines = 1)
                Text(
                    text = entity.domain.uppercase(Locale.getDefault()),
                    style = Type.mono(8.5),
                    color = Palette.Dim,
                )
            }
        }
    }

    Text(
        text = "RESET TO DEFAULT",
        style = Type.MicroSmall,
        color = Palette.Dim,
        modifier = Modifier
            .padding(top = 14.dp)
            .clickable(onClick = onReset),
    )
}

@Composable
private fun AddSlot(accent: Color, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(58.dp)
            .background(Palette.Fill, TileShape)
            .border(1.dp, Palette.Hairline, TileShape)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Outlined.Add,
            contentDescription = null,
            tint = accent,
            modifier = Modifier.size(18.dp),
        )
    }
}

/* ---------------- the widgets ---------------- */

@Composable
private fun Thermostat(
    entity: HaEntity,
    accent: Color,
    arranging: Boolean,
    onNudge: (Double) -> Unit,
) {
    Column(modifier = Modifier.card(arranging).padding(14.dp)) {
        Text(
            text = entity.name.uppercase(Locale.getDefault()),
            style = Type.MicroSmall,
            color = Palette.Dim,
            maxLines = 1,
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.Bottom,
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                text = entity.target?.let { String.format(Locale.US, "%.1f°", it) } ?: "—",
                style = Type.figure(46),
                color = Palette.Ink,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Stepper(Icons.Outlined.Remove, accent, arranging) { onNudge(-0.5) }
                Stepper(Icons.Outlined.Add, accent, arranging) { onNudge(0.5) }
            }
        }
        entity.current?.let {
            Text(
                text = String.format(Locale.US, "NOW %.1f°", it),
                style = Type.mono(8.5),
                color = Palette.Dim,
                modifier = Modifier.padding(top = 4.dp),
            )
        }
    }
}

@Composable
private fun Stepper(icon: ImageVector, accent: Color, arranging: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .size(34.dp)
            .background(Palette.Fill, CircleShape)
            .border(1.dp, Palette.Ink.copy(alpha = 0.10f), CircleShape)
            .clickable(enabled = !arranging, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, contentDescription = null, tint = accent, modifier = Modifier.size(15.dp))
    }
}

@Composable
private fun SensorCell(entity: HaEntity, arranging: Boolean) {
    Column(modifier = Modifier.card(arranging).padding(horizontal = 12.dp, vertical = 11.dp)) {
        Text(
            text = entity.name.uppercase(Locale.getDefault()),
            style = Type.MicroSmall,
            color = Palette.Dim,
            maxLines = 1,
        )
        Text(
            text = entity.reading,
            style = Type.figure(19),
            color = Palette.Ink,
            maxLines = 1,
            modifier = Modifier.padding(top = 6.dp),
        )
    }
}

@Composable
private fun Tile(entity: HaEntity, accent: Color, arranging: Boolean) {
    val icon = when (entity.domain) {
        "light" -> Icons.Outlined.Lightbulb
        "lock" -> Icons.Outlined.Lock
        "fan" -> Icons.Outlined.Toys
        "media_player" -> Icons.Outlined.Speaker
        "humidifier" -> Icons.Outlined.Opacity
        "automation" -> Icons.Outlined.Bolt
        "script", "scene" -> Icons.Outlined.PlayArrow
        else -> Icons.Outlined.Power
    }
    Column(
        modifier = Modifier
            .background(if (entity.on) Palette.FillActive else Palette.Fill, TileShape)
            .border(
                1.dp,
                when {
                    arranging -> Palette.HairlineLoud
                    entity.on -> Palette.HairlineLoud
                    else -> Palette.Hairline
                },
                TileShape,
            )
            .padding(14.dp),
        verticalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Icon(
            imageVector = icon,
            contentDescription = null,
            tint = if (entity.on) accent else Palette.Dim,
            modifier = Modifier.size(20.dp),
        )
        Column {
            Text(entity.name, style = Type.body(14.5), color = Palette.Ink, maxLines = 1)
            Text(
                text = entity.label.uppercase(Locale.getDefault()),
                style = Type.mono(8.5),
                color = Palette.Dim,
                modifier = Modifier.padding(top = 3.dp),
            )
        }
    }
}

/** The surface every widget shares. Arranging lifts the edge so it reads as loose. */
private fun Modifier.card(arranging: Boolean): Modifier = this
    .background(Palette.Fill, TileShape)
    .border(1.dp, if (arranging) Palette.HairlineLoud else Palette.Hairline, TileShape)

/* ---------------- setup ---------------- */

/** Shown once, when there is nowhere to talk to yet. */
@Composable
private fun Setup(accent: Color, onSave: (String, String) -> Unit) {
    var url by remember { mutableStateOf("http://homeassistant.local:8123") }
    var token by remember { mutableStateOf("") }

    Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("HOME ASSISTANT", style = Type.Section, color = Palette.Dim)
        Field("URL", url, KeyboardType.Uri) { url = it }
        Field("LONG-LIVED TOKEN", token, KeyboardType.Password) { token = it }
        Spacer(Modifier.height(4.dp))
        Text(
            text = "CONNECT",
            style = Type.Section,
            color = accent,
            modifier = Modifier
                .background(Palette.Fill, RoundedCornerShape(13.dp))
                .border(1.dp, Palette.Hairline, RoundedCornerShape(13.dp))
                .clickable { onSave(url, token) }
                .padding(horizontal = 16.dp, vertical = 12.dp),
        )
    }
}

@Composable
private fun Field(
    label: String,
    value: String,
    keyboard: KeyboardType,
    onChange: (String) -> Unit,
) {
    Column {
        Text(label, style = Type.MicroSmall, color = Palette.Dim)
        BasicTextField(
            value = value,
            onValueChange = onChange,
            singleLine = true,
            textStyle = Type.mono(11.5).copy(color = Palette.Ink),
            cursorBrush = SolidColor(Palette.Ink),
            keyboardOptions = androidx.compose.foundation.text.KeyboardOptions(
                keyboardType = keyboard,
                imeAction = ImeAction.Done,
            ),
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp)
                .background(Palette.Fill, RoundedCornerShape(11.dp))
                .border(1.dp, Palette.Hairline, RoundedCornerShape(11.dp))
                .padding(horizontal = 12.dp, vertical = 10.dp),
        )
    }
}
