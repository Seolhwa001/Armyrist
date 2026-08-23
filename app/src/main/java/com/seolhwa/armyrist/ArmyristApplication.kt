package com.seolhwa.armyrist

import android.app.Application
import com.seolhwa.armyrist.data.CountingRepository
import com.seolhwa.armyrist.notification.ChecklistNotificationManager
import com.seolhwa.armyrist.notification.TimePlanActionNotificationManager
import com.seolhwa.armyrist.stage2.data.CoreSuiteRepository
import com.seolhwa.armyrist.timeplan.data.TimePlanV2MigrationCoordinator
import com.seolhwa.armyrist.timeplan.data.TimePlanV2Repository
import com.seolhwa.armyrist.timeplan.v3.data.DateAwareTimePlanRepository
import com.seolhwa.armyrist.trash.CommonTrashRepository

class ArmyristApplication : Application() {
    lateinit var repository: CountingRepository
        private set

    lateinit var coreSuiteRepository: CoreSuiteRepository
        private set

    lateinit var timePlanV2Repository: TimePlanV2Repository
        private set

    lateinit var dateAwareTimePlanRepository: DateAwareTimePlanRepository
        private set

    lateinit var commonTrashRepository: CommonTrashRepository
        private set

    override fun onCreate() {
        super.onCreate()

        // Restore journal recovery MUST happen before repository snapshots load.
        ArmyristPortableDataManager.recoverInterruptedRestore(this)

        reloadRepositories()

        timePlanV2Repository = TimePlanV2Repository(this)
        TimePlanV2MigrationCoordinator.sync(
            legacyRepository = coreSuiteRepository,
            v2Repository = timePlanV2Repository
        )
        dateAwareTimePlanRepository = DateAwareTimePlanRepository(this)
        commonTrashRepository = CommonTrashRepository(this).also {
            it.purgeExpired()
        }

        ChecklistNotificationManager.reconcile(
            this,
            coreSuiteRepository
        )
        TimePlanActionNotificationManager.reconcile(
            this,
            dateAwareTimePlanRepository
        )
    }

    fun reloadAfterPortableDataChange() {
        // Keep repository object identity stable so Activities that already
        // hold these instances immediately see imported/restored data.
        repository.reloadFromPersistence()
        coreSuiteRepository.reloadFromPersistence()
        timePlanV2Repository.reloadFromPersistence()
        dateAwareTimePlanRepository.reloadFromPersistence()
        TimePlanV2MigrationCoordinator.sync(
            legacyRepository = coreSuiteRepository,
            v2Repository = timePlanV2Repository
        )
        dateAwareTimePlanRepository = DateAwareTimePlanRepository(this)

        ChecklistNotificationManager.reconcile(
            this,
            coreSuiteRepository
        )
        TimePlanActionNotificationManager.reconcile(
            this,
            dateAwareTimePlanRepository
        )
    }

    private fun reloadRepositories() {
        repository = CountingRepository(this)
        coreSuiteRepository = CoreSuiteRepository(this)
    }
}
