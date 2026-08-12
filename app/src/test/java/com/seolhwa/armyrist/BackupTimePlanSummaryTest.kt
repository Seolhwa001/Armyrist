package com.seolhwa.armyrist

import com.seolhwa.armyrist.timeplan.domain.ClockTime
import com.seolhwa.armyrist.timeplan.domain.ClockValue
import com.seolhwa.armyrist.timeplan.domain.RevisedTimePlan
import com.seolhwa.armyrist.timeplan.domain.TimeAnchor
import com.seolhwa.armyrist.timeplan.portable.TimePlanPortableV2Codec
import org.json.JSONArray
import org.json.JSONObject
import org.junit.Assert.assertEquals
import org.junit.Test

class BackupTimePlanSummaryTest {

    private fun plan(id: String): RevisedTimePlan =
        RevisedTimePlan(
            id = id,
            title = id,
            start = TimeAnchor(
                ClockValue.explicit(
                    ClockTime.requireMinuteOfDay(8 * 60)
                )
            ),
            end = TimeAnchor(
                ClockValue.explicit(
                    ClockTime.requireMinuteOfDay(9 * 60)
                )
            ),
            createdAt = "0",
            updatedAt = "0"
        )

    private fun snapshot(count: Int): JSONObject =
        JSONObject()
            .put("schemaVersion", 2)
            .put(
                "plans",
                JSONArray().apply {
                    repeat(count) { index ->
                        put(
                            TimePlanPortableV2Codec.encode(
                                plan("p$index")
                            )
                        )
                    }
                }
            )

    @Test
    fun validatedV2CountThreeReturnsThree() {
        assertEquals(
            3,
            ArmyristPortableDataManager
                .validatedRestorableTimePlanCount(
                    snapshot(3)
                )
        )
    }

    @Test
    fun validatedV2CountZeroReturnsZero() {
        assertEquals(
            0,
            ArmyristPortableDataManager
                .validatedRestorableTimePlanCount(
                    snapshot(0)
                )
        )
    }
}
