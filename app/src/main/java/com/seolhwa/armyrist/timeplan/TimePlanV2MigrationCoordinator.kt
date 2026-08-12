package com.seolhwa.armyrist.timeplan.data

import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.migration.LocalTimePlanV1Migrator

/**
 * Non-destructive synchronization bridge while the legacy v1 UI is still active.
 *
 * v1 remains readable/writable by CoreSuiteRepository.
 * v2 receives an explicit migrated copy when missing, or when legacy updatedAt
 * is newer than the current v2 copy. A newer v2 record is never overwritten.
 */
object TimePlanV2MigrationCoordinator {

    data class Report(
        val migrated: Int,
        val unchanged: Int,
        val failures: List<Failure>
    )

    data class Failure(
        val planId: String,
        val reason: String
    )

    fun sync(
        legacyRepository: CoreSuiteRepository,
        v2Repository: TimePlanV2Repository
    ): Report {
        var migrated = 0
        var unchanged = 0
        val failures = mutableListOf<Failure>()

        legacyRepository.getTimePlans().forEach { legacy ->
            val current = v2Repository.getPlan(legacy.id)
            val currentUpdatedAt =
                current?.updatedAt?.toLongOrNull() ?: Long.MIN_VALUE

            if (current != null && currentUpdatedAt >= legacy.updatedAt) {
                unchanged++
                return@forEach
            }

            when (val result = LocalTimePlanV1Migrator.migrate(legacy)) {
                is LocalTimePlanV1Migrator.Result.Success -> {
                    if (v2Repository.upsertMigrated(result.value)) {
                        migrated++
                    } else {
                        failures += Failure(
                            legacy.id,
                            "v2 persistence commit failed."
                        )
                    }
                }

                is LocalTimePlanV1Migrator.Result.Failure -> {
                    failures += Failure(legacy.id, result.reason)
                }
            }
        }

        return Report(
            migrated = migrated,
            unchanged = unchanged,
            failures = failures
        )
    }
}
