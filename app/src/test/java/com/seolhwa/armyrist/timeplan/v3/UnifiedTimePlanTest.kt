package com.seolhwa.armyrist.timeplan.v3
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.calendar.*
import com.seolhwa.armyrist.timeplan.v3.data.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.portable.TimePlanPortableV3Codec
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import java.time.LocalDateTime
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class UnifiedTimePlanTest {
 private val app get()=ApplicationProvider.getApplicationContext<ArmyristApplication>()
 private val t=LocalDateTime.of(2030,11,5,9,0)
 private fun plan():DateAwareTimePlan {
  val e=DateTimeEvent("old",TimeEventKind.MIDWAY,0,"기존 구간",EventDateTimeSpec.Range(DateTimeValue.explicit(t.plusHours(1)),DateTimeValue.explicit(t.plusHours(2))),note="기존 메모",color="#147850")
  return DateTimePlanRules.normalizeTopology(DateAwareTimePlan("p","검증",start=DateTimeAnchor(DateTimeValue.explicit(t)),end=DateTimeAnchor(DateTimeValue.explicit(t.plusHours(3))),midwayEvents=listOf(e),actions=listOf(TimePlanActionItem(id="a",content="기존 점검",scheduledDateTime=t.plusHours(3))),createdAt="1",updatedAt="1"))
 }
 @Test fun endpointPointsHaveTheirOwnHourInsteadOfClampingToEndLine(){
  val p=plan();val axis=UnifiedTimePlan.scale(p)!!
  assertTrue(axis.end>p.end.value.value!!)
  val item=p.actions.single();val row=axis.slice(axis.index(item.scheduledDateTime))
  assertTrue(item.scheduledDateTime>=row.start&&item.scheduledDateTime<row.end)
 }
 @Test fun boundsIncludeEarlierLaterAndOvernightActivities(){
  val p=plan().copy(actions=listOf(TimePlanActionItem(content="이른 점검",scheduledDateTime=t.minusHours(4)),TimePlanActionItem(content="야간",scheduledDateTime=t.plusHours(14),durationMinutes=180)))
  val axis=UnifiedTimePlan.scale(p)!!
  assertEquals(t.minusHours(4),axis.start);assertTrue(axis.end>=t.plusHours(17))
 }
 @Test fun sameMinuteItemsGetDistinctUsableLanes(){
  val p=plan().copy(midwayEvents=emptyList(),actions=(0..3).map{TimePlanActionItem(id="$it",content="항목 $it",scheduledDateTime=t.plusHours(3))})
  assertEquals(4,UnifiedTimePlan.lanes(UnifiedTimePlan.entries(p)).values.toSet().size)
 }
 @Test fun adapterPreservesLegacyRecordsAndDoesNotCreateDuplicateActions(){
  val p=plan();val entries=UnifiedTimePlan.entries(p)
  assertEquals(2,entries.size);assertEquals(60L,entries.first{it.key=="N:old"}.value.durationMinutes)
  assertEquals("기존 메모",entries.first{it.key=="N:old"}.value.note)
  assertEquals(1,p.midwayEvents.size);assertEquals(1,p.actions.size)
 }
 @Test fun sameEditorSavesLegacyRangeAndNewPointOrRange(){
  val r=app.dateAwareTimePlanRepository;assertTrue(r.commit(plan()))
  assertTrue(r.saveUnifiedEntry("p","N:old",UnifiedTimePlanDraft("구간 수정",t.plusHours(3),30,note="유지",color="#7543A3",notificationMode=ActionNotificationMode.SIMPLE)))
  assertTrue(r.saveUnifiedEntry("p",null,UnifiedTimePlanDraft("새 구간",t.plusHours(4),90)))
  assertTrue(r.saveUnifiedEntry("p",null,UnifiedTimePlanDraft("시각만",t.plusHours(5))))
  val p=r.getPlan("p")!!;val event=p.midwayEvents.single()
  assertEquals(t.plusHours(3).plusMinutes(30),DateTimePlanRules.departure(event.timeSpec));assertEquals(ActionNotificationMode.SIMPLE,event.notificationMode)
  assertEquals(3,p.actions.size);assertEquals(90L,p.actions.single{it.content=="새 구간"}.durationMinutes)
  assertNull(p.actions.single{it.content=="시각만"}.durationMinutes)
  assertEquals(t.plusHours(3),p.end.value.value)
 }
 @Test fun bothKindsCompleteFromNotificationsAndStaleEditorsKeepCompletion(){
  val r=app.dateAwareTimePlanRepository;assertTrue(r.commit(plan()))
  val key=UnifiedTimePlan.entries(r.getPlan("p")!!).single{it.key=="N:old"}.value.id
  assertTrue(r.completeActionFromNotification("p",key));assertTrue(r.completeActionFromNotification("p",key))
  assertTrue(r.saveUnifiedEntry("p","N:old",UnifiedTimePlanDraft("확인 완료",t.plusHours(1),60)))
  assertEquals(ActionCompletionState.COMPLETE,r.getPlan("p")!!.midwayEvents.single().completionState)
  assertTrue(r.completeActionFromNotification("p","a"))
  assertTrue(r.saveUnifiedEntry("p","A:a",UnifiedTimePlanDraft("점검 수정",t.plusHours(4),30)))
  assertEquals(ActionCompletionState.COMPLETE,r.getPlan("p")!!.actions.single().completionState)
  assertTrue(CalendarAgenda.entriesOn(t.toLocalDate(),listOf(r.getPlan("p")!!),emptyList()).filter{it.kind!=AgendaKind.PLAN}.all{it.complete})
 }
 @Test fun backupsKeepDurationAlarmsGroupsColorsAndLocks(){
  val group=TimePlanActionGroup("g","점검",0)
  val p=plan().copy(actionGroups=listOf(group),midwayEvents=plan().midwayEvents.map{it.copy(completionState=ActionCompletionState.COMPLETE,notificationMode=ActionNotificationMode.SIMPLE,groupId="g")},actions=plan().actions.map{it.copy(durationMinutes=45,timeLocked=true,groupId="g")})
  val encoded=TimePlanPortableV3Codec.encode(p);assertEquals(p,TimePlanPortableV3Codec.decode(encoded))
  val copy=TimePlanPortableV3Codec.regenerateIds(p)
  assertNotEquals(p.id,copy.id);assertEquals(copy.actionGroups.single().id,copy.midwayEvents.single().groupId)
  assertEquals(45L,copy.actions.single().durationMinutes);assertTrue(copy.actions.single().timeLocked)
  encoded.getJSONArray("actions").getJSONObject(0).remove("durationMinutes");encoded.getJSONArray("actions").getJSONObject(0).remove("timeLocked")
  val legacy=TimePlanPortableV3Codec.decode(encoded);assertNull(legacy.actions.single().durationMinutes);assertFalse(legacy.actions.single().timeLocked)
 }
 @Test fun failedVoiceBatchDoesNotPartiallyCreateItemsAndDeletedItemsCannotReappear(){
  val r=app.dateAwareTimePlanRepository;assertTrue(r.commit(plan()));val before=r.getPlan("p")!!
  assertFalse(r.addUnifiedEntries("p",listOf(UnifiedTimePlanDraft("정상",t),UnifiedTimePlanDraft("",t))))
  assertEquals(before,r.getPlan("p"))
  assertTrue(r.deleteEvent("p","old"));assertFalse(r.saveUnifiedEntry("p","N:old",UnifiedTimePlanDraft("삭제된 항목",t)))
  assertFalse(r.completeActionFromNotification("p",UnifiedTimePlan.EVENT_ALARM_PREFIX+"old"))
 }
 @Test fun rangedActivitiesAppearAcrossDatesAndKeepRemainingTime(){
  val a=TimePlanActionItem(id="range",content="야간",scheduledDateTime=t.withHour(23),durationMinutes=180,markerColor="#147850")
  val p=plan().copy(actions=listOf(a))
  val next=CalendarAgenda.entriesOn(t.toLocalDate().plusDays(1),listOf(p),emptyList()).single{it.itemId=="range"}
  assertEquals(t.plusDays(1).withHour(2),next.end);assertEquals("#147850",next.color)
 }
 @Test fun lockedItemsCannotMoveOrBeDeletedUntilUnlocked(){
  val r=app.dateAwareTimePlanRepository;val p=plan().copy(actions=plan().actions.map{it.copy(timeLocked=true)});assertTrue(r.commit(p))
  assertFalse(r.saveUnifiedEntry("p","A:a",UnifiedTimePlanDraft("고정",t.plusHours(4),locked=true)))
  assertThrows(IllegalArgumentException::class.java){TimelineSelection.remove(p,setOf("A:a"))}
  assertTrue(r.saveUnifiedEntry("p","A:a",UnifiedTimePlanDraft("해제",t.plusHours(4),locked=false)))
 }
 @Test fun homeNextActionIncludesUnfinishedLegacyEntries(){
  val now=LocalDateTime.of(2030,11,5,9,0)
  val base=app.dateAwareTimePlanRepository.createPlan(now)!!
  val event=DateTimeEvent("legacy",TimeEventKind.MIDWAY,0,"통합 다음 항목",EventDateTimeSpec.Single(DateTimeValue.explicit(now.plusMinutes(5))))
  val plan=base.copy(dateDisplayMode=TimePlanDateDisplayMode.ABSOLUTE,midwayEvents=listOf(event),actions=listOf(TimePlanActionItem(id="later",content="이후 항목",scheduledDateTime=now.plusMinutes(10))))
  assertEquals("통합 다음 항목",TimePlanLiveStatus.nextAction(listOf(plan),now)!!.action.content)
  val complete=plan.copy(midwayEvents=listOf(event.copy(completionState=ActionCompletionState.COMPLETE)))
  assertEquals("이후 항목",TimePlanLiveStatus.nextAction(listOf(complete),now)!!.action.content)
 }

}
