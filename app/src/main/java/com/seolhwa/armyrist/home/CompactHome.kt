@file:OptIn(androidx.compose.foundation.ExperimentalFoundationApi::class)
package com.seolhwa.armyrist.home

import android.widget.Toast
import androidx.compose.animation.core.animateDpAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectDragGesturesAfterLongPress
import androidx.compose.foundation.layout.*
import androidx.compose.material3.*
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.boundsInRoot
import androidx.compose.ui.layout.onGloballyPositioned
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.semantics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.zIndex
import com.seolhwa.armyrist.*
import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import java.time.format.DateTimeFormatter

@Composable
internal fun CompactHome(revision:Int,config:HomeBannerConfig,onTool:(String)->Unit,onSettings:()->Unit,onData:()->Unit){
    val context=LocalContext.current;val app=context.applicationContext as ArmyristApplication
    val density=LocalDensity.current;val haptic=LocalHapticFeedback.current
    val prefs=remember{context.getSharedPreferences("killomris_home_tools_v1",0)}
    var order by remember{mutableStateOf(HomeToolOrder.decode(prefs.getString("order",null)))}
    var dragging by remember{mutableStateOf<String?>(null)}
    var before by remember{mutableStateOf(order)}
    var finger by remember{mutableStateOf(Offset.Zero)}
    var grab by remember{mutableStateOf(Offset.Zero)}
    var grid by remember{mutableStateOf(Rect.Zero)}
    fun save(next:List<String>){if(prefs.edit().putString("order",HomeToolOrder.encode(next)).commit())order=next else {order=before;Toast.makeText(context,"배치를 저장하지 못했습니다.",Toast.LENGTH_SHORT).show()}}
    val now=rememberLiveTime();val today=now.toLocalDate()
    val plans=remember(revision){app.dateAwareTimePlanRepository.getPlans()};val lists=remember(revision){app.coreSuiteRepository.getChecklists()}
    val entries=CalendarAgenda.entriesOn(today,plans,lists)
    val next=TimePlanLiveStatus.nextAction(plans.filter{config.planId==null||it.id==config.planId},now)
    fun title(id:String)=when(id){"counting"->"실셈";"checklist"->"체크리스트";"timeplan"->"시간계획";"report"->"보고 양식";"handicap"->"핸디캡 상황";else->"달력"}
    Column(Modifier.fillMaxSize().statusBarsPadding().navigationBarsPadding().padding(12.dp),verticalArrangement=Arrangement.spacedBy(8.dp)){
        Row(Modifier.height(44.dp),verticalAlignment=Alignment.CenterVertically){Text("낄롬리스",Modifier.weight(1f),style=MaterialTheme.typography.titleLarge,fontWeight=FontWeight.Bold);ArmyristHelpButton(ArmyristHelpTopic.HOME);TextButton(onClick=onSettings){Text("설정")}}
        if(config.calendarEnabled)Surface(Modifier.fillMaxWidth().clickable{onTool("calendar")},shape=ArmyristPanelShape,color=ArmyristColors.SecondaryControl){
            Row(Modifier.padding(12.dp),verticalAlignment=Alignment.CenterVertically){Column(Modifier.weight(1f)){Text(today.format(DateTimeFormatter.ofPattern("M월 d일 EEEE",java.util.Locale.KOREAN)),fontWeight=FontWeight.Bold);Text("오늘 일정 ${entries.size}개",style=MaterialTheme.typography.bodySmall,color=ArmyristColors.SecondaryText)};Text("달력 ›",color=ArmyristColors.PrimaryControl,fontSize=12.sp)}
        }
        if(config.mode!=HomeBannerMode.HIDDEN)Surface(Modifier.fillMaxWidth(),shape=ArmyristControlShape,color=ArmyristColors.WorkSurface){
            Text(if(config.mode==HomeBannerMode.NOTICE)"${config.noticeTitle} · ${config.noticeBody}" else next?.let{"다음 · ${it.action.scheduledDateTime.format(DateTimeFormatter.ofPattern("HH:mm"))} ${it.action.content}"}?:"예정된 미완료 실시사항이 없습니다",Modifier.padding(10.dp),style=MaterialTheme.typography.bodySmall,maxLines=1,overflow=TextOverflow.Ellipsis)
        }
        Text(if(dragging==null)"도구를 길게 누른 채 밀어 배치 변경"else"원하는 위치로 옮긴 뒤 손을 떼세요",fontSize=11.sp,color=ArmyristColors.SecondaryText)
        BoxWithConstraints(Modifier.weight(1f).fillMaxWidth().onGloballyPositioned{grid=it.boundsInRoot()}){
            val cellWidth=(maxWidth-8.dp)/2;val cellHeight=(maxHeight-16.dp)/3
            order.forEachIndexed{index,id->key(id){
                val x by animateDpAsState((cellWidth+8.dp)*(index%2),tween(160),label="도구 가로 이동")
                val y by animateDpAsState((cellHeight+8.dp)*(index/2),tween(160),label="도구 세로 이동")
                val active=dragging==id
                var bounds by remember{mutableStateOf(Rect.Zero)}
                Surface(Modifier.offset(x,y).width(cellWidth).height(cellHeight).zIndex(if(active)2f else 0f)
                    .graphicsLayer{
                        if(active){translationX=finger.x-grab.x-grid.left-with(density){x.toPx()};translationY=finger.y-grab.y-grid.top-with(density){y.toPx()}}
                        scaleX=if(active)1.03f else 1f;scaleY=scaleX
                    }.onGloballyPositioned{bounds=it.boundsInRoot()}
                    .semantics{contentDescription="${title(id)} 도구";customActions=listOf(
                        CustomAccessibilityAction("앞으로 이동"){before=order;save(HomeToolOrder.moveToIndex(order,id,(order.indexOf(id)-1).coerceAtLeast(0)));true},
                        CustomAccessibilityAction("뒤로 이동"){before=order;save(HomeToolOrder.moveToIndex(order,id,(order.indexOf(id)+1).coerceAtMost(order.lastIndex)));true})}
                    .clickable(enabled=dragging==null){onTool(id)}
                    .pointerInput(id){detectDragGesturesAfterLongPress(
                        onDragStart={p->before=order;grab=p;finger=bounds.topLeft+p;dragging=id;haptic.performHapticFeedback(HapticFeedbackType.LongPress)},
                        onDrag={change,delta->change.consume();finger+=delta
                            if(grid.width>0 && grid.height>0){val col=((finger.x-grid.left)/(grid.width/2)).toInt().coerceIn(0,1);val row=((finger.y-grid.top)/(grid.height/3)).toInt().coerceIn(0,2)
                                val target=row*2+col
                                if(order.indexOf(id)!=target){order=HomeToolOrder.moveToIndex(order,id,target);haptic.performHapticFeedback(HapticFeedbackType.TextHandleMove)}}},
                        onDragEnd={save(order);dragging=null},onDragCancel={order=before;dragging=null}
                    )},shape=ArmyristPanelShape,color=if(active)ArmyristColors.SecondaryControl else ArmyristColors.RaisedSurface,border=BorderStroke(if(active)2.dp else 1.dp,if(active)ArmyristColors.PrimaryControl else ArmyristColors.SoftBorder)){
                    Column(Modifier.fillMaxSize().padding(8.dp),verticalArrangement=Arrangement.Center,horizontalAlignment=Alignment.CenterHorizontally){
                        Icon(when(id){"counting"->Icons.Outlined.FormatListNumbered;"checklist"->Icons.Outlined.FactCheck;"timeplan"->Icons.Outlined.Schedule;"report"->Icons.Outlined.Description;"handicap"->Icons.Outlined.PhoneInTalk;else->Icons.Outlined.CalendarMonth},null,tint=ArmyristColors.PrimaryControl,modifier=Modifier.size(24.dp))
                        Text(title(id),fontWeight=FontWeight.SemiBold,fontSize=14.sp,maxLines=2,overflow=TextOverflow.Ellipsis)
                    }
                }
            }}
        }
        Row(Modifier.fillMaxWidth().height(40.dp),horizontalArrangement=Arrangement.spacedBy(8.dp)){
            OutlinedButton(onClick=onData,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("데이터·백업",fontSize=12.sp)}
            OutlinedButton(onClick=onSettings,modifier=Modifier.weight(1f),contentPadding=PaddingValues(4.dp)){Text("설정·홈 모드",fontSize=12.sp)}
        }
    }
}
