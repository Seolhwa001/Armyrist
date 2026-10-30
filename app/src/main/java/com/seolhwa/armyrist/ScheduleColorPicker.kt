package com.seolhwa.armyrist

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.dp

internal fun scheduleColor(hex:String):Color = runCatching { Color(android.graphics.Color.parseColor(hex)) }.getOrDefault(Color(0xFF7543A3))

@Composable
internal fun ScheduleColorPicker(value:String,onChange:(String)->Unit) {
    var custom by remember(value){mutableStateOf(value)}
    var editing by remember { mutableStateOf(false) }
    if(editing)ArmyristDialog(onDismissRequest={editing=false},title={Text("일정 색상")},text={OutlinedTextField(custom,{custom=it},label={Text("#RRGGBB")},singleLine=true)},
        confirmButton={TextButton(enabled=custom.matches(Regex("#[0-9a-fA-F]{6}")),onClick={onChange(custom.uppercase());editing=false}){Text("적용")}},
        dismissButton={TextButton(onClick={editing=false}){Text("취소")}})
    Column(verticalArrangement=Arrangement.spacedBy(6.dp)) {
        Row(Modifier.fillMaxWidth(),verticalAlignment=androidx.compose.ui.Alignment.CenterVertically) {
            Text("표시 색상",Modifier.weight(1f),style=MaterialTheme.typography.labelMedium)
            TextButton(onClick={editing=true}){Text("직접 입력")}
        }
        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)) {
            listOf("#2E55CC","#7543A3","#147850","#975B0F","#B52C43","#47566D").forEach { hex ->
                Box(Modifier.size(36.dp).background(scheduleColor(hex),CircleShape)
                    .border(if(value.equals(hex,true))3.dp else 0.dp,ArmyristColors.PrimaryText,CircleShape)
                    .semantics{contentDescription="실시사항 색상 $hex"}.clickable{onChange(hex)})
            }
        }

    }
}
