package com.seolhwa.armyrist

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.ArrowBack
import androidx.compose.material.icons.outlined.Home
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

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
        modifier = Modifier.heightIn(min = 76.dp),
        title = {
            Column(
                modifier = (if (onTitleClick != null) Modifier.clickable(onClick = onTitleClick) else Modifier)
                    .padding(start = 2.dp)
            ) {
                Text(
                    text = title,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    maxLines = 1
                )
                if (!subtitle.isNullOrBlank()) {
                    Spacer(Modifier.height(1.dp))
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.labelMedium,
                        color = ArmyristColors.OnDark.copy(alpha = 0.78f),
                        maxLines = 1
                    )
                }
            }
        },
        navigationIcon = {
            Row(
                modifier = Modifier.padding(start = 8.dp, end = 14.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                HeaderAction(
                    icon = leadingIcon,
                    label = leadingLabel,
                    onClick = onLeading
                )

                if (!secondaryLeadingLabel.isNullOrBlank() && onSecondaryLeading != null) {
                    HeaderTextAction(
                        label = secondaryLeadingLabel,
                        onClick = onSecondaryLeading
                    )
                }
            }
        },
        actions = actions,
        colors = TopAppBarDefaults.topAppBarColors(
            containerColor = ArmyristColors.Header,
            titleContentColor = ArmyristColors.OnDark,
            navigationIconContentColor = ArmyristColors.OnDark,
            actionIconContentColor = ArmyristColors.OnDark
        )
    )
}

@Composable
private fun HeaderAction(
    icon: ArmyristTopBarLeadingIcon,
    label: String,
    onClick: () -> Unit
) {
    if (icon == ArmyristTopBarLeadingIcon.HOME || icon == ArmyristTopBarLeadingIcon.BACK) {
        Surface(
            modifier = Modifier.size(54.dp),
            shape = RoundedCornerShape(12.dp),
            color = ArmyristColors.HeaderActionSurface,
            contentColor = ArmyristColors.OnDark,
            border = BorderStroke(1.dp, ArmyristColors.HeaderActionBorder)
        ) {
            IconButton(
                onClick = onClick,
                modifier = Modifier.fillMaxSize()
            ) {
                Icon(
                    imageVector = if (icon == ArmyristTopBarLeadingIcon.HOME)
                        Icons.Outlined.Home else Icons.Outlined.ArrowBack,
                    contentDescription = if (icon == ArmyristTopBarLeadingIcon.HOME) "홈" else "뒤로",
                    modifier = Modifier.size(28.dp)
                )
            }
        }
    } else {
        HeaderTextAction(label = label, onClick = onClick)
    }
}

@Composable
private fun HeaderTextAction(
    label: String,
    onClick: () -> Unit
) {
    OutlinedButton(
        onClick = onClick,
        modifier = Modifier.height(54.dp).widthIn(min = 66.dp),
        shape = RoundedCornerShape(12.dp),
        border = BorderStroke(1.dp, ArmyristColors.HeaderActionBorder),
        colors = ButtonDefaults.outlinedButtonColors(
            containerColor = ArmyristColors.HeaderActionSurface,
            contentColor = ArmyristColors.OnDark
        ),
        contentPadding = PaddingValues(horizontal = 14.dp)
    ) {
        Text(label, fontWeight = FontWeight.Bold)
    }
}
