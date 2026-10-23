package com.seolhwa.armyrist
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.DialogProperties

@Composable
fun ArmyristDialog(onDismissRequest: () -> Unit, confirmButton: @Composable () -> Unit,
    modifier: Modifier = Modifier, dismissButton: (@Composable () -> Unit)? = null,
    icon: (@Composable () -> Unit)? = null, title: (@Composable () -> Unit)? = null, text: (@Composable () -> Unit)? = null,
    shape: Shape = ArmyristPanelShape, containerColor: Color = ArmyristColors.RaisedSurface,
    iconContentColor: Color = ArmyristColors.PrimaryControl, titleContentColor: Color = ArmyristColors.PrimaryText,
    textContentColor: Color = ArmyristColors.SecondaryText, tonalElevation: Dp = 0.dp,
    properties: DialogProperties = DialogProperties()) {
    androidx.compose.material3.AlertDialog(onDismissRequest=onDismissRequest,confirmButton=confirmButton,modifier=modifier,
        dismissButton=dismissButton,icon=icon,title=title?.let { heading -> { ProvideTextStyle(MaterialTheme.typography.titleLarge.copy(fontWeight=FontWeight.Bold)) { heading() } } },
        text=text,shape=shape,containerColor=containerColor,iconContentColor=iconContentColor,titleContentColor=titleContentColor,
        textContentColor=textContentColor,tonalElevation=tonalElevation,properties=properties)
}
