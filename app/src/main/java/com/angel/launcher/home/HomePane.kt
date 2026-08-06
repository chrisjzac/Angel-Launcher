package com.angel.launcher.home

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Add
import androidx.compose.material.icons.outlined.Lightbulb
import androidx.compose.material.icons.outlined.Lock
import androidx.compose.material.icons.outlined.Opacity
import androidx.compose.material.icons.outlined.Power
import androidx.compose.material.icons.outlined.Remove
import androidx.compose.material.icons.outlined.Speaker
import androidx.compose.material.icons.outlined.Toys
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.angel.launcher.ui.Palette
import com.angel.launcher.ui.Type
import java.util.Locale

private val TileShape = RoundedCornerShape(17.dp)

@Composable
fun HomePane(model: HomeViewModel, accent: Color) {
    val configured by model.configured.collectAsState()
    val entities by model.entities.collectAsState()
    val house by model.house.collectAsState()

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

        Thermostat(house, accent, onNudge = model::nudgeTarget)
        Sensors(house)

        LazyVerticalGrid(
            columns = GridCells.Fixed(2),
            modifier = Modifier.padding(top = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(9.dp),
            verticalArrangement = Arrangement.spacedBy(9.dp),
        ) {
            items(entities, key = { it.id }) { entity ->
                Tile(entity, accent) { model.toggle(entity) }
            }
        }
    }
}

@Composable
private fun Thermostat(house: House, accent: Color, onNudge: (Double) -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(bottom = 18.dp),
        verticalAlignment = Alignment.Bottom,
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Column {
            Text("THERMOSTAT", style = Type.MicroSmall, color = Palette.Dim)
            Text(
                text = house.target?.let { String.format(Locale.US, "%.1f°", it) } ?: "—",
                style = Type.figure(46),
                color = Palette.Ink,
                modifier = Modifier.padding(top = 6.dp),
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stepper(Icons.Outlined.Remove, accent) { onNudge(-0.5) }
            Stepper(Icons.Outlined.Add, accent) { onNudge(0.5) }
        }
    }
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .height(1.dp)
            .background(Palette.Ink.copy(alpha = 0.09f)),
    )
}

@Composable
private fun Stepper(icon: ImageVector, accent: Color, onClick: () -> Unit) {
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

@Composable
private fun Sensors(house: House) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(top = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(9.dp),
    ) {
        Cell("INDOOR", house.indoor?.let { String.format(Locale.US, "%.1f°", it) } ?: "—", Modifier.weight(1f))
        Cell("HUMIDITY", house.humidity?.let { "$it%" } ?: "—", Modifier.weight(1f))
        Cell("DRAWING", house.kilowatts?.let { String.format(Locale.US, "%.2f kW", it) } ?: "—", Modifier.weight(1f))
    }
}

@Composable
private fun Cell(label: String, value: String, modifier: Modifier = Modifier) {
    Column(
        modifier = modifier
            .background(Palette.Fill, RoundedCornerShape(15.dp))
            .border(1.dp, Palette.Hairline, RoundedCornerShape(15.dp))
            .padding(horizontal = 12.dp, vertical = 11.dp),
    ) {
        Text(label, style = Type.MicroSmall, color = Palette.Dim)
        Text(value, style = Type.figure(19), color = Palette.Ink, modifier = Modifier.padding(top = 6.dp))
    }
}

@Composable
private fun Tile(entity: HaEntity, accent: Color, onClick: () -> Unit) {
    val icon = when (entity.domain) {
        "light" -> Icons.Outlined.Lightbulb
        "lock" -> Icons.Outlined.Lock
        "fan" -> Icons.Outlined.Toys
        "media_player" -> Icons.Outlined.Speaker
        "humidifier" -> Icons.Outlined.Opacity
        else -> Icons.Outlined.Power
    }
    Column(
        modifier = Modifier
            .background(
                if (entity.on) Palette.FillActive else Palette.Fill,
                TileShape,
            )
            .border(
                1.dp,
                if (entity.on) Palette.HairlineLoud else Palette.Hairline,
                TileShape,
            )
            .clickable(onClick = onClick)
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
