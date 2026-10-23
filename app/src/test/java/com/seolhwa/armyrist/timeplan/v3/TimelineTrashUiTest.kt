package com.seolhwa.armyrist.timeplan.v3
import androidx.test.core.app.ApplicationProvider
import com.seolhwa.armyrist.ArmyristApplication
import com.seolhwa.armyrist.timeplan.v3.data.*
import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.trash.*
import java.time.LocalDateTime
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
@RunWith(RobolectricTestRunner::class)
@Config(sdk=[35],application=ArmyristApplication::class)
class TimelineTrashUiTest {
    @Test fun restoringSelectionDoesNotRevertLaterParentEdits(){
        val app=ApplicationProvider.getApplicationContext<ArmyristApplication>();val repo=app.dateAwareTimePlanRepository;val trash=app.commonTrashRepository
        val base=repo.createPlan(LocalDateTime.of(2030,1,2,8,0))!!
        val event=DateTimeEvent("event",TimeEventKind.MIDWAY,0,"오전",EventDateTimeSpec.Single(DateTimeValue.explicit(base.start.value.value!!.plusMinutes(20))))
        val action=TimePlanActionItem(id="a",parentPointId=event.id,content="점검",scheduledDateTime=base.start.value.value!!.plusMinutes(20),markerColor="#16845D")
        assertTrue(repo.commit(DateTimePlanRules.normalizeTopology(base.copy(midwayEvents=listOf(event),actions=listOf(action)))))
        assertTrue(TimelineTrash.move(repo,trash,base.id,setOf("N:event")))
        assertTrue(repo.getPlan(base.id)!!.actions.isEmpty())
        val after=repo.getPlan(base.id)!!;assertTrue(repo.commit(after.copy(title="수정한 계획 제목",memo="삭제 후 작성한 메모")))
        val entry=trash.getItems(TrashToolType.TIME_PLAN).single();assertEquals(TimelineTrash.VERSION,entry.payloadVersion)
        assertTrue(TimelineTrash.restore(repo,entry.payload));val restored=repo.getPlan(base.id)!!
        assertEquals("수정한 계획 제목",restored.title);assertEquals("삭제 후 작성한 메모",restored.memo);assertEquals("#16845D",restored.actions.single().markerColor)
        assertTrue(TimelineTrash.restore(repo,entry.payload));assertEquals(1,repo.getPlan(base.id)!!.actions.size)
        val loaded=DateAwareTimePlanRepository(app).getPlan(base.id)!!;assertEquals("#16845D",loaded.actions.single().markerColor)
    }
}
