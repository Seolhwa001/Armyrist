@file:OptIn(androidx.compose.material3.ExperimentalMaterial3Api::class)
package com.seolhwa.armyrist.calendar
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.seolhwa.armyrist.*
import java.time.*
import java.time.format.DateTimeFormatter

@Composable
fun ScheduleDateTimeFields(value: LocalDateTime, onChange: (LocalDateTime) -> Unit, compact: Boolean = false) {
    var dateOpen by remember { mutableStateOf(false) };var timeOpen by remember { mutableStateOf(false) }
    @Composable fun Field(label: String, text: String, modifier: Modifier, action: () -> Unit) {
        Surface(modifier.clickable(onClickLabel=label,onClick=action),shape=ArmyristControlShape,color=ArmyristColors.SecondaryControl) {
            Column(Modifier.padding(12.dp),verticalArrangement=Arrangement.spacedBy(4.dp)) {
                Text(label,style=MaterialTheme.typography.labelSmall,color=ArmyristColors.PrimaryControl)
                Text(text,fontWeight=FontWeight.Bold)
            }
        }
    }
    if(compact) Column(verticalArrangement=Arrangement.spacedBy(8.dp)) {
        Field("날짜 선택",value.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),Modifier.fillMaxWidth()) { dateOpen=true }
        Field("시간 선택",value.format(DateTimeFormatter.ofPattern("HH:mm")),Modifier.fillMaxWidth()) { timeOpen=true }
    } else Row(Modifier.fillMaxWidth(),horizontalArrangement=Arrangement.spacedBy(8.dp)) {
        Field("날짜 선택",value.format(DateTimeFormatter.ofPattern("yyyy.MM.dd")),Modifier.weight(1.3f)) { dateOpen=true }
        Field("시간 선택",value.format(DateTimeFormatter.ofPattern("HH:mm")),Modifier.weight(1f)) { timeOpen=true }
    }
    if(dateOpen) {
        val state=rememberDatePickerState(initialSelectedDateMillis=value.toLocalDate().atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli())
        DatePickerDialog(onDismissRequest={dateOpen=false},shape=ArmyristPanelShape,confirmButton={TextButton(enabled=state.selectedDateMillis!=null,onClick={
            state.selectedDateMillis?.let { onChange(LocalDateTime.of(Instant.ofEpochMilli(it).atZone(ZoneOffset.UTC).toLocalDate(),value.toLocalTime())) };dateOpen=false
        }) { Text("적용") }},dismissButton={TextButton(onClick={dateOpen=false}){Text("취소")}}) { DatePicker(state=state) }
    }
    if(timeOpen) {
        val state=rememberTimePickerState(value.hour,value.minute,true);var dial by remember { mutableStateOf(true) }
        val small=LocalConfiguration.current.screenHeightDp<620
        ArmyristDialog(onDismissRequest={timeOpen=false},title={Text("시간 선택")},text={
            Column(Modifier.verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally) {
                TextButton(onClick={dial=!dial}) { Text(if(dial) "숫자로 입력" else "시계로 선택") }
                if(!dial) TimeInput(state) else if(small) Box(Modifier.fillMaxWidth().height(280.dp),contentAlignment=Alignment.Center) {
                    TimePicker(state,modifier=Modifier.requiredSize(256.dp,380.dp).graphicsLayer { scaleX=.72f;scaleY=.72f })
                } else TimePicker(state)
            }
        },confirmButton={Button(onClick={onChange(value.withHour(state.hour).withMinute(state.minute).withSecond(0).withNano(0));timeOpen=false}){Text("적용")}},dismissButton={TextButton(onClick={timeOpen=false}){Text("취소")}})
    }
}
