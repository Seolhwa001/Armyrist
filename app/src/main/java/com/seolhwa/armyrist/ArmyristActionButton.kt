package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.heightIn
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@Composable
fun ArmyristActionButton(
    text: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
    primary: Boolean = false
) {
    val sized = modifier.heightIn(min = 52.dp)
    if (primary) {
        Button(
            onClick = onClick,
            modifier = sized,
            shape = ArmyristControlShape,
            colors = ButtonDefaults.buttonColors(
                containerColor = ArmyristColors.PrimaryControl,
                contentColor = ArmyristColors.OnDark
            )
        ) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    } else {
        OutlinedButton(
            onClick = onClick,
            modifier = sized,
            shape = ArmyristControlShape,
            border = BorderStroke(1.dp, ArmyristColors.PrimaryControl),
            colors = ButtonDefaults.outlinedButtonColors(
                containerColor = ArmyristColors.WorkSurface,
                contentColor = ArmyristColors.PrimaryText
            )
        ) {
            Text(text, fontWeight = FontWeight.Bold)
        }
    }
}
