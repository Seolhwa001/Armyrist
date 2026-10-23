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
import androidx.compose.ui.unit.sp

object ArmyristColors {
    // Light work surfaces
    val AppBackground = Color(0xFFF3F5F9)
    val WorkSurface = Color(0xFFFFFFFF)
    val RaisedSurface = Color(0xFFFFFFFF)
    val InputSurface = Color(0xFFFFFFFF)

    // Calm blue control surfaces
    val Header = Color(0xFF18243A)
    val HeaderActionSurface = Color(0xFF253650)
    val HeaderActionBorder = Color(0xFF61738E)
    val HeaderRaised = Color(0xFF2D4261)
    val PrimaryControl = Color(0xFF2E55CC)
    val SecondaryControl = Color(0xFFEAF0FF)

    // Text
    val PrimaryText = Color(0xFF142038)
    val SecondaryText = Color(0xFF47566D)
    val MutedText = Color(0xFF5D6B82)
    val OnDark = Color(0xFFFFFFFF)

    // Structure
    val Border = Color(0xFFBCC8D8)
    val Divider = Color(0xFFDFE5EF)
    val Accent = Color(0xFF3157C8)
    val Danger = Color(0xFFB52C43)
    val DangerMuted = Color(0xFFA33B4C)
    val SoftBorder = Color(0xFFDCE3ED)
    val LiveGreen = Color(0xFF16845D)
    val LiveSurface = Color(0xFFE6F6EE)
    val LiveBorder = Color(0xFF83CDA9)
    val InfoSurface = Color(0xFFFFFFFF)
}

private val ArmyristLightScheme = lightColorScheme(
    primary = ArmyristColors.PrimaryControl,
    onPrimary = ArmyristColors.OnDark,
    primaryContainer = ArmyristColors.SecondaryControl,
    onPrimaryContainer = ArmyristColors.PrimaryText,
    surfaceContainerHigh = ArmyristColors.RaisedSurface,
    surfaceContainerHighest = Color(0xFFEBEFF5),
    surfaceContainer = Color(0xFFF1F4F8),
    surfaceContainerLow = ArmyristColors.RaisedSurface,
    surfaceContainerLowest = ArmyristColors.RaisedSurface,
    surfaceTint = Color.Transparent,
    outlineVariant = ArmyristColors.Divider,
    secondary = ArmyristColors.SecondaryControl,
    onSecondary = ArmyristColors.PrimaryText,
    secondaryContainer = ArmyristColors.SecondaryControl,
    onSecondaryContainer = ArmyristColors.PrimaryControl,
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
        typography = Typography(
            titleLarge=androidx.compose.ui.text.TextStyle(fontSize=22.sp,lineHeight=28.sp,fontWeight=FontWeight.Bold),
            titleMedium=androidx.compose.ui.text.TextStyle(fontSize=17.sp,lineHeight=24.sp,fontWeight=FontWeight.SemiBold),
            bodyLarge=androidx.compose.ui.text.TextStyle(fontSize=16.sp,lineHeight=24.sp),
            bodyMedium=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=21.sp),
            bodySmall=androidx.compose.ui.text.TextStyle(fontSize=13.sp,lineHeight=18.sp),
            labelLarge=androidx.compose.ui.text.TextStyle(fontSize=14.sp,lineHeight=20.sp,fontWeight=FontWeight.SemiBold),
            labelMedium=androidx.compose.ui.text.TextStyle(fontSize=12.sp,lineHeight=17.sp,fontWeight=FontWeight.Medium),
            labelSmall=androidx.compose.ui.text.TextStyle(fontSize=11.sp,lineHeight=16.sp,fontWeight=FontWeight.Medium)
        ),
        shapes = Shapes(extraSmall=RoundedCornerShape(8.dp),small=RoundedCornerShape(12.dp),medium=RoundedCornerShape(16.dp),large=RoundedCornerShape(20.dp),extraLarge=RoundedCornerShape(24.dp)),
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
