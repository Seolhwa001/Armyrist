package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp

@Composable
fun ArmyristUtilityActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier
) {
    OutlinedButton(
        onClick = onClick,
        modifier = modifier,
        shape = ArmyristPanelShape,
        border = BorderStroke(
            1.dp,
            ArmyristColors.Border
        ),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ArmyristColors.WorkSurface,
            contentColor = ArmyristColors.PrimaryText
        ),
        contentPadding =
            PaddingValues(horizontal = 10.dp, vertical = 6.dp)
    ) {
        Text(
            text,
            style = MaterialTheme.typography.labelLarge
        )
    }
}
