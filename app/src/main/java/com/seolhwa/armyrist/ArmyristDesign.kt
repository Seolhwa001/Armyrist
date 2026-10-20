package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

object ArmyristColors {
    // Light work surfaces
    val AppBackground = Color(0xFFF5F7FB)
    val WorkSurface = Color(0xFFF0F4FA)
    val RaisedSurface = Color(0xFFFFFFFF)
    val InputSurface = Color(0xFFFFFFFF)

    // Calm blue control surfaces
    val Header = Color(0xFF172842)
    val HeaderActionSurface = Color(0xFF243D60)
    val HeaderActionBorder = Color(0xFF7189AC)
    val HeaderRaised = Color(0xFF2D4D76)
    val PrimaryControl = Color(0xFF4263D8)
    val SecondaryControl = Color(0xFFE8EEFF)

    // Text
    val PrimaryText = Color(0xFF17233B)
    val SecondaryText = Color(0xFF59677E)
    val MutedText = Color(0xFF7F8BA0)
    val OnDark = Color(0xFFFFFFFF)

    // Structure
    val Border = Color(0xFFA7B5CA)
    val Divider = Color(0xFFD8E0ED)
    val Accent = Color(0xFF5474D9)
    val Danger = Color(0xFFC44857)
    val DangerMuted = Color(0xFFB9636E)
    val SoftBorder = Color(0xFFE0E6F0)
    val LiveGreen = Color(0xFF16845D)
    val LiveSurface = Color(0xFFE6F6EE)
    val LiveBorder = Color(0xFF83CDA9)
    val InfoSurface = Color(0xFFF0F4FA)
}

private val ArmyristLightScheme = lightColorScheme(
    primary = ArmyristColors.PrimaryControl,
    onPrimary = ArmyristColors.OnDark,
    secondary = ArmyristColors.SecondaryControl,
    onSecondary = ArmyristColors.PrimaryText,
    background = ArmyristColors.AppBackground,
    onBackground = ArmyristColors.PrimaryText,
    surface = ArmyristColors.WorkSurface,
    onSurface = ArmyristColors.PrimaryText,
    surfaceVariant = ArmyristColors.RaisedSurface,
    onSurfaceVariant = ArmyristColors.SecondaryText,
    outline = ArmyristColors.Border,
    error = ArmyristColors.Danger
)

@Composable
fun ArmyristTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = ArmyristLightScheme,
        content = content
    )
}

// Armyrist common UI language: soft work cards, compact controls, functional icons.
val ArmyristPanelShape: Shape = RoundedCornerShape(18.dp)
val ArmyristControlShape: Shape = RoundedCornerShape(12.dp)

@Composable
fun ArmyristPanel(
    modifier: Modifier = Modifier,
    dark: Boolean = false,
    contentPadding: PaddingValues = PaddingValues(14.dp),
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier = modifier,
        shape = ArmyristPanelShape,
        color = if (dark) ArmyristColors.Header else ArmyristColors.WorkSurface,
        contentColor = if (dark) ArmyristColors.OnDark else ArmyristColors.PrimaryText,
        border = BorderStroke(
            1.dp,
            if (dark) ArmyristColors.HeaderRaised else ArmyristColors.Border
        )
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
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelMedium,
        fontWeight = FontWeight.Bold,
        color = ArmyristColors.SecondaryText
    )
}

@Composable
fun ArmyristSystemLabel(
    text: String,
    modifier: Modifier = Modifier,
    onDark: Boolean = false
) {
    Text(
        text = text,
        modifier = modifier,
        style = MaterialTheme.typography.labelSmall,
        color = if (onDark) Color(0xFFCBD9EF) else ArmyristColors.MutedText
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
        color = ArmyristColors.HeaderRaised,
        contentColor = ArmyristColors.OnDark,
        border = BorderStroke(1.dp, Color(0xFF5E779D))
    ) {
        Text(
            text = "[$number]",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
