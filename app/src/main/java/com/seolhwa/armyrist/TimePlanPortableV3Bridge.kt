package com.seolhwa.armyrist

import android.content.Context
import com.seolhwa.armyrist.timeplan.data.TimePlanV2Repository
import com.seolhwa.armyrist.timeplan.portable.TimePlanPortableV2Codec
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.timeplan.v3.portable.TimePlanPortableV3Codec
import org.json.JSONArray
import org.json.JSONObject

/**
 * Isolates Date-aware TimePlan portable/persistence behavior from the Stage 3 container.
 * Container encryption/integrity semantics remain in ArmyristPortableDataManager.
 */
object TimePlanPortableV3Bridge {
    const val SCHEMA = 5
    const val V3_PREFS = DateAwareTimePlanRepository.PREFS
    const val V3_KEY = DateAwareTimePlanRepository.KEY
    const val V2_PREFS = "armyrist_timeplan_v2"
    const val V2_KEY = "snapshot_v2"

    fun v3Snapshot(context: Context): JSONObject =
        JSONObject(DateAwareTimePlanRepository(context).exportSnapshot())

    fun v2Snapshot(context: Context): JSONObject =
        JSONObject(TimePlanV2Repository(context).exportPortableSnapshot())

    fun unmigratedV2Snapshot(context: Context): JSONObject {
        val v3Ids = DateAwareTimePlanRepository(context).getPlans().map { it.id }.toSet()
        val plans = JSONArray()
        TimePlanV2Repository(context).getPlans().filterNot { it.id in v3Ids }.forEach {
            plans.put(TimePlanPortableV2Codec.encode(it))
        }
        return JSONObject().put("schemaVersion", 2).put("plans", plans)
    }

    fun validateV3(root: JSONObject) {
        val schema = root.getInt("schemaVersion")
        require(schema in setOf(3, 4, SCHEMA))
        val plans = root.optJSONArray("plans") ?: JSONArray()
        for (i in 0 until plans.length()) TimePlanPortableV3Codec.decode(plans.getJSONObject(i))
    }

    fun validateV2(root: JSONObject) {
        require(root.getInt("schemaVersion") == 2)
        val plans = root.optJSONArray("plans") ?: JSONArray()
        for (i in 0 until plans.length()) TimePlanPortableV2Codec.decode(plans.getJSONObject(i))
    }

    fun v3Count(root: JSONObject): Int { validateV3(root); return root.optJSONArray("plans")?.length() ?: 0 }
    fun v2Count(root: JSONObject): Int { validateV2(root); return root.optJSONArray("plans")?.length() ?: 0 }

    fun writeV3(context: Context, root: JSONObject): Boolean {
        validateV3(root)
        return context.getSharedPreferences(V3_PREFS, Context.MODE_PRIVATE)
            .edit().putString(V3_KEY, root.toString()).commit()
    }

    fun writeV2(context: Context, root: JSONObject): Boolean {
        validateV2(root)
        return context.getSharedPreferences(V2_PREFS, Context.MODE_PRIVATE)
            .edit().putString(V2_KEY, root.toString()).commit()
    }
}
