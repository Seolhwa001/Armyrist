@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.seolhwa.armyrist.home

import android.content.Intent
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.LocalDate
import java.time.format.DateTimeFormatter

/** One viewport, six tools. Larger system text may use multiple lines within each tile. */
@Composable
internal fun CompactHome(revision:Int,config:HomeBannerConfig,onTool:(String)->Unit,onSettings:()->Unit,onData:()->Unit){
    val context=LocalContext.current;val app=context.applicationContext as ArmyristApplication
    val prefs=remember{context.getSharedPreferences("killomris_home_tools_v1",0)}
    var order by remember{mutableStateOf(HomeToolOrder.decode(prefs.getString("order",null)))}
    var arranging by remember{mutableStateOf<String?>(null)}
    val now=rememberLiveTime();val today=now.toLocalDate()
    val plans=remember(revision){app.dateAwareTimePlanRepository.getPlans()};val lists=remember(revision){app.coreSuiteRepository.getChecklists()}
    val entries=CalendarAgenda.entriesOn(today,plans,lists)
    val next=TimePlanLiveStatus.nextAction(plans.filter{config.planId==null||it.id==config.planId},now)
    fun title(id:String)=when(id){"counting"->"실셈";"checklist"->"체크리스트";"timeplan"->"시간계획";"report"->"보고 양식";"handicap"->"핸디캡 상황";else->"달력"}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.height(44.dp),verticalAlignment=Alignment.CenterVertically){Text("낄롬리스",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);ArmyristHelpButton(ArmyristHelpTopic.HOME);TextButton(onClick=onSettings){Text("설정")}}
        if(config.calendarEnabled)Surface(Modifier.fillMaxWidth().combinedClickable(onClick={onTool("calendar")}),shape=ArmyristPanelShape,color=ArmyristColors.SecondaryControl){
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",java.util.Locale.KOREAN)),fontWeight=FontWeight.Bold);Text("오늘 일정 ${entries.size}개",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)};Text("달력 ›",color=ArmyristColors.PrimaryControl,fontSize=12.sp)}
        }
        if(config.mode!=HomeBannerMode.HIDDEN)Surface(Modifier.fillMaxWidth(),shape=ArmyristControlShape,color=ArmyristColors.WorkSurface){
            Text(if(config.mode==HomeBannerMode.NOTICE)"${config.noticeTitle} · ${config.noticeBody}" else next?.let{"다음 · ${it.action.scheduledDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} ${it.action.content}"}?:"예정된 미완료 실시사항이 없습니다",Modifier.padding(10.dp),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Column(Modifier.weight(1f),verticalArrangement=Arrangement.spacedBy(8.dp)){
            order.chunked(2).forEach{pair->Row(Modifier.weight(1f),horizontalArrangement=Arrangement.spacedBy(8.dp)){
                pair.forEachIndexed{index,id->Surface(Modifier.weight(1f).fillMaxHeight().combinedClickable(onClick={onTool(id)},onLongClick={arranging=id}),shape=ArmyristPanelShape,color=ArmyristColors.RaisedSurface,border=BorderStroke(1.dp,ArmyristColors.SoftBorder)){
                    Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(when(id){"counting"->Icons.Outlined.FormatListNumbered;"checklist"->Icons.Outlined.FactCheck;"timeplan"->Icons.Outlined.Schedule;"report"->Icons.Outlined.Description;"handicap"->Icons.Outlined.PhoneInTalk;else->Icons.Outlined.CalendarMonth},null,tint=ArmyristColors.PrimaryControl,modifier=Modifier.size(24.dp))
                        Text(title(id),fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }}
            }}
        }
        Row(Modifier.fillMaxWidth().height(40.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick=onData,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("데이터·백업",fontSize=12.sp)}
            OutlinedButton(onClick=onSettings,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("설정·홈 모드",fontSize=12.sp)}
        }
    }
    arranging?.let{id->ArmyristDialog(onDismissRequest={arranging=null},title={Text("${title(id)} 배치 변경")},text={Text("현재 ${order.indexOf(id)+1}번째 위치입니다.")},confirmButton={TextButton(onClick={val n=HomeToolOrder.moveToIndex(order,id,(order.indexOf(id)+1).coerceAtMost(order.lastIndex));if(prefs.edit().putString("order",HomeToolOrder.encode(n)).commit())order=n}){Text("뒤로 이동")}},dismissButton={TextButton(onClick={val n=HomeToolOrder.moveToIndex(order,id,(order.indexOf(id)-1).coerceAtLeast(0));if(prefs.edit().putString("order",HomeToolOrder.encode(n)).commit())order=n}){Text("앞으로 이동")}})}
}
