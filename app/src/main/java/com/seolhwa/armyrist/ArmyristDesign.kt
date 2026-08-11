package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.foundation.shape.RoundedCornerShape

object ArmyristColors {
    val AppBackground = Color(0xFF10120F)
    val Panel = Color(0xFF1A1E17)
    val RaisedPanel = Color(0xFF23291F)
    val Olive = Color(0xFF626B45)
    val OliveMuted = Color(0xFF3E4632)
    val Border = Color(0xFF6F765E)
    val Accent = Color(0xFFB7C47D)
    val PrimaryText = Color(0xFFF1F3EA)
    val SecondaryText = Color(0xFFB8BEAB)
    val DisabledText = Color(0xFF747A6C)
    val Danger = Color(0xFFD9988D)
}

private val ArmyristScheme = darkColorScheme(
    primary = ArmyristColors.Accent,
    onPrimary = Color(0xFF202514),
    secondary = ArmyristColors.Olive,
    onSecondary = ArmyristColors.PrimaryText,
    background = ArmyristColors.AppBackground,
    onBackground = ArmyristColors.PrimaryText,
    surface = ArmyristColors.Panel,
    onSurface = ArmyristColors.PrimaryText,
    surfaceVariant = ArmyristColors.RaisedPanel,
    onSurfaceVariant = ArmyristColors.SecondaryText,
    outline = ArmyristColors.Border,
    error = ArmyristColors.Danger
)

@Composable
fun ArmyristTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArmyristScheme,
        content = content
    )
}

val ArmyristPanelShape: Shape = RoundedCornerShape(4.dp)

@Composable
fun ArmyristPanel(
    modifier: Modifier = Modifier,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = ArmyristPanelShape,
        color = ArmyristColors.Panel,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Column(
            modifier = Modifier.padding(contentPadding),
            content = content
        )
    }
}

@Composable
fun ArmyristSectionLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text.uppercase(),
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = ArmyristColors.SecondaryText
    )
}

@Composable
fun ArmyristSystemLabel(
    text: String,
    modifier: Modifier = Modifier
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = ArmyristColors.DisabledText
    )
}

@Composable
fun ArmyristToolNumber(
    number: String,
    modifier: Modifier = Modifier
) {
    Surface(
        modifier = modifier,
        shape = ArmyristPanelShape,
        color = ArmyristColors.OliveMuted,
        border = BorderStroke(1.dp, ArmyristColors.Border)
    ) {
        Text(
            text = "[$number]",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold,
            color = ArmyristColors.Accent
        )
    }
}
