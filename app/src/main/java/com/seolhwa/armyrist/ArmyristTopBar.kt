package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmyristTopBar(
    title: String,
    subtitle: String? = null,
    leadingLabel: String = "홈",
    onLeading: () -> Unit
) {
    TopAppBar(
        title = {
            Column {
                Text(
                    title,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Text(
                        subtitle,
                        style = MaterialTheme.typography.labelSmall,
                        color = ArmyristColors.OnDark.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            OutlinedButton(
                onClick = onLeading,
                shape = ArmyristPanelShape,
                border = BorderStroke(
                    1.dp,
                    ArmyristColors.OnDark.copy(alpha = 0.65f)
                ),
                colors = ButtonDefaults.outlinedButtonColors(
                    contentColor = ArmyristColors.OnDark
                ),
                contentPadding = PaddingValues(horizontal = 10.dp)
            ) {
                Text(leadingLabel)
            }
        },
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ArmyristColors.Header,
            titleContentColor = ArmyristColors.OnDark,
            navigationIconContentColor = ArmyristColors.OnDark
        )
    )
}
