package com.seolhwa.armyrist.timeplan.v3

import com.seolhwa.armyrist.timeplan.v3.domain.*
import com.seolhwa.armyrist.timeplan.v3.portable.TimePlanPortableV3Codec
import org.junit.Assert.*
import org.junit.Test
import java.time.LocalDateTime

class TimePlanPortableV3CodecTest {
    @Test fun roundTripPreservesDatesAndLongDuration() {
        val start=LocalDateTime.of(2026,8,14,9,0)
        val end=LocalDateTime.of(2026,8,16,9,0)
        val plan=DateTimePlanRules.normalizeTopology(DateAwareTimePlan(
            id="p",title="multi",
            start=DateTimeAnchor(DateTimeValue.explicit(start)),
            end=DateTimeAnchor(DateTimeValue.explicit(end)),
            createdAt="0",updatedAt="0"
        ))
        val decoded=TimePlanPortableV3Codec.decode(TimePlanPortableV3Codec.encode(plan))
        assertEquals(start,decoded.start.value.value)
        assertEquals(end,decoded.end.value.value)
        assertEquals(2880L,decoded.links.first().durationMinutes)
    }

    @Test fun regenerateIdsPreservesMeaning() {
        val p=DateAwareTimePlan("p","x",start=DateTimeAnchor(DateTimeValue.explicit(LocalDateTime.of(2026,8,14,9,0))),end=DateTimeAnchor(DateTimeValue.explicit(LocalDateTime.of(2026,8,14,10,0))),createdAt="0",updatedAt="0")
        val fresh=TimePlanPortableV3Codec.regenerateIds(p)
        assertNotEquals(p.id,fresh.id)
        assertEquals(p.start.value.value,fresh.start.value.value)
        assertEquals(p.end.value.value,fresh.end.value.value)
    }
}
