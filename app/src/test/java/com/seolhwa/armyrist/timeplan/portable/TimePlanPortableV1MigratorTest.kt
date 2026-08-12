package com.seolhwa.armyrist.timeplan.portable

import com.seolhwa.armyrist.timeplan.domain.EventTimeSpec
import com.seolhwa.armyrist.timeplan.domain.TimeEventKind
import com.seolhwa.armyrist.timeplan.domain.ValueOrigin
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class TimePlanPortableV1MigratorTest {

    @Test
    fun knownV1MigratesExplicitlyWithoutInventingFinal() {
        val v1 = document(
            points = arrayOf(
                point("start", 0, "시작", 9 * 60),
                point("mid", 1, "집결", 9 * 60 + 40),
                point("end", 2, "종료", 13 * 60)
            )
        )

        val result = TimePlanPortableV1Migrator.migrate(v1)
        assertTrue(result is TimePlanPortableV1Migrator.Result.Success)

        val plan =
            (result as TimePlanPortableV1Migrator.Result.Success).value

        assertEquals("legacy-plan", plan.id)
        assertEquals("기존 계획", plan.title)
        assertEquals(1, plan.midwayEvents.size)
        assertEquals(TimeEventKind.MIDWAY, plan.midwayEvents.single().kind)
        assertEquals("집결", plan.midwayEvents.single().name)
        assertNull(plan.finalPoint)

        assertEquals(
            ValueOrigin.EXPLICIT,
            plan.start.value.origin
        )
        assertEquals(
            ValueOrigin.EXPLICIT,
            (plan.midwayEvents.single().timeSpec as EventTimeSpec.Single)
                .value.origin
        )
        assertEquals(40, plan.links[0].duration?.minutes)
        assertEquals(200, plan.links[1].duration?.minutes)
        assertEquals(ValueOrigin.DERIVED, plan.links[0].origin)
    }

    @Test
    fun midnightV1MigratesWithNextDayInterval() {
        val v1 = document(
            points = arrayOf(
                point("start", 0, "시작", 23 * 60 + 40),
                point("end", 1, "종료", 20)
            )
        )

        val result = TimePlanPortableV1Migrator.migrate(v1)
        val plan =
            (result as TimePlanPortableV1Migrator.Result.Success).value

        assertEquals(40, plan.links.single().duration?.minutes)
    }

    @Test
    fun partialV1PreservesUnsetInsteadOfGuessing() {
        val v1 = document(
            points = arrayOf(
                point("start", 0, "시작", null),
                point("mid", 1, "집결", null),
                point("end", 2, "종료", 16 * 60)
            )
        )

        val result = TimePlanPortableV1Migrator.migrate(v1)
        val plan =
            (result as TimePlanPortableV1Migrator.Result.Success).value

        assertNull(plan.start.value.time)
        assertEquals(ValueOrigin.UNSET, plan.start.value.origin)
        assertEquals(EventTimeSpec.Unspecified, plan.midwayEvents.single().timeSpec)
        assertNull(plan.links[0].duration)
    }

    @Test
    fun malformedOrUnknownShapeFailsWithoutGuessing() {
        val malformed = JSONObject()
            .put("id", "legacy-plan")
            .put("title", "기존 계획")
            .put("createdAt", 1L)
            .put("updatedAt", 2L)
            .put("points", JSONArray())

        val result = TimePlanPortableV1Migrator.migrate(malformed)

        assertTrue(result is TimePlanPortableV1Migrator.Result.Failure)
    }

    private fun document(
        points: Array<JSONObject>
    ): JSONObject =
        JSONObject()
            .put("id", "legacy-plan")
            .put("title", "기존 계획")
            .put("memo", "메모")
            .put("createdAt", 1L)
            .put("updatedAt", 2L)
            .put(
                "points",
                JSONArray().apply {
                    points.forEach { put(it) }
                }
            )

    private fun point(
        id: String,
        order: Int,
        name: String,
        minutes: Int?
    ): JSONObject =
        JSONObject()
            .put("id", id)
            .put("planId", "legacy-plan")
            .put("order", order)
            .put("name", name)
            .put("timeMinutes", minutes ?: JSONObject.NULL)
}
