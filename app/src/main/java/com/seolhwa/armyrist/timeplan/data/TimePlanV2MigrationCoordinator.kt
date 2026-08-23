package com.seolhwa.armyrist.timeplan.data

import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.migration.LocalTimePlanV1Migrator

/**
 * One-way compatibility bridge from the retired CoreSuite(v1) TimePlan store
 * into the v2 compatibility repository.
 *
 * IMPORTANT:
 * The old CoreSuite TimePlan UI is no longer active. Keeping a successfully
 * migrated v1 source record causes it to be copied back into v2 whenever
 * repositories are reloaded (for example after Portable Import), which makes
 * deleted legacy plans appear to "resurrect".
 *
 * Contract:
 * 1) Never delete the v1 source until a valid v2 copy is known to exist.
 * 2) If v2 already contains the same/newer record, remove only the stale v1 source.
 * 3) If migration to v2 succeeds, remove the v1 source immediately afterwards.
 * 4) If migration/persistence fails, retain the v1 source so data is not lost.
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
                // A valid v2 copy already owns this plan. The v1 record is only
                // a stale migration source now; keeping it would resurrect the
                // plan on the next repository reload after the v2 copy is deleted.
                legacyRepository.deleteTimePlan(legacy.id)
                unchanged++
                return@forEach
            }

            when (val result = LocalTimePlanV1Migrator.migrate(legacy)) {
                is LocalTimePlanV1Migrator.Result.Success -> {
                    if (v2Repository.upsertMigrated(result.value)) {
                        // Commit destination first, then retire the source.
                        // This ordering preserves the no-data-loss contract.
                        legacyRepository.deleteTimePlan(legacy.id)
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
