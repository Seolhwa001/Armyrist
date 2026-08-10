package com.seolhwa.armyrist

import android.app.Application
import com.seolhwa.armyrist.data.CountingRepository

class ArmyristApplication : Application() {
    lateinit var repository: CountingRepository
        private set

    override fun onCreate() {
        super.onCreate()
        repository = CountingRepository(this)
    }
}
