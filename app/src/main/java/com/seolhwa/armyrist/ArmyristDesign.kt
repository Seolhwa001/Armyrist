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
    val AppBackground = Color(0xFFE7E9E1)
    val WorkSurface = Color(0xFFF2F3ED)
    val RaisedSurface = Color(0xFFF8F8F4)
    val InputSurface = Color(0xFFFCFCF8)

    // Dark military control frame
    val Header = Color(0xFF343B2C)
    val HeaderRaised = Color(0xFF424A37)
    val PrimaryControl = Color(0xFF4F5A3C)
    val SecondaryControl = Color(0xFFD8DDCC)

    // Text
    val PrimaryText = Color(0xFF171A15)
    val SecondaryText = Color(0xFF52584D)
    val MutedText = Color(0xFF777D72)
    val OnDark = Color(0xFFF4F5EF)

    // Structure
    val Border = Color(0xFF8A9181)
    val Divider = Color(0xFFB9BEB2)
    val Accent = Color(0xFF687447)
    val Danger = Color(0xFF9A3F36)
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

val ArmyristPanelShape: Shape = RoundedCornerShape(3.dp)

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
        color = if (onDark) Color(0xFFC8CEBD) else ArmyristColors.MutedText
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
        border = BorderStroke(1.dp, Color(0xFF727C61))
    ) {
        Text(
            text = "[$number]",
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 5.dp),
            style = MaterialTheme.typography.labelLarge,
            fontWeight = FontWeight.Bold
        )
    }
}
