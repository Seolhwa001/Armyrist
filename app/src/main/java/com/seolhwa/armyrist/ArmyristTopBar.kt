package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.Surface
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.foundation.layout.RowScope
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.Modifier

enum class ArmyristTopBarLeadingIcon { NONE, HOME, BACK }

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ArmyristTopBar(
    title: String,
    subtitle: String? = null,
    leadingLabel: String = "홈",
    onLeading: () -> Unit,
    leadingIcon: ArmyristTopBarLeadingIcon = ArmyristTopBarLeadingIcon.NONE,
    secondaryLeadingLabel: String? = null,
    onSecondaryLeading: (() -> Unit)? = null,
    onTitleClick: (() -> Unit)? = null,
    actions: @Composable RowScope.() -> Unit = {}
) {
    TopAppBar(
        title = {
            Column(
                modifier = if (onTitleClick != null) {
                    androidx.compose.ui.Modifier.clickable(onClick = onTitleClick)
                } else {
                    androidx.compose.ui.Modifier
                }
            ) {
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
            Row {
                if (leadingIcon == ArmyristTopBarLeadingIcon.HOME || leadingIcon == ArmyristTopBarLeadingIcon.BACK) {
                    Surface(
                        modifier = Modifier.size(52.dp),
                        shape = RoundedCornerShape(11.dp),
                        color = ArmyristColors.HeaderActionSurface,
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.HeaderActionBorder
                        )
                    ) {
                        IconButton(
                            onClick = onLeading,
                            modifier = Modifier.fillMaxSize()
                        ) {
                            Icon(
                                if (leadingIcon == ArmyristTopBarLeadingIcon.HOME) Icons.Outlined.Home else Icons.Outlined.ArrowBack,
                                contentDescription = if (leadingIcon == ArmyristTopBarLeadingIcon.HOME) "홈" else "뒤로",
                                tint = ArmyristColors.OnDark,
                                modifier = Modifier.size(27.dp)
                            )
                        }
                    }
                    Spacer(Modifier.width(16.dp))
                } else {
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
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) { Text(leadingLabel) }
                }

                if (!secondaryLeadingLabel.isNullOrBlank() && onSecondaryLeading != null) {
                    Spacer(androidx.compose.ui.Modifier.width(4.dp))
                    OutlinedButton(
                        onClick = onSecondaryLeading,
                        shape = ArmyristPanelShape,
                        border = BorderStroke(
                            1.dp,
                            ArmyristColors.OnDark.copy(alpha = 0.65f)
                        ),
                        colors = ButtonDefaults.outlinedButtonColors(
                            contentColor = ArmyristColors.OnDark
                        ),
                        contentPadding = PaddingValues(horizontal = 9.dp)
                    ) { Text(secondaryLeadingLabel) }
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ArmyristColors.Header,
            titleContentColor = ArmyristColors.OnDark,
            navigationIconContentColor = ArmyristColors.OnDark
        )
    )
}
