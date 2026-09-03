package com.ovi.handoff

import android.app.Application
import org.koin.android.ext.koin.androidContext
import org.koin.android.ext.koin.androidLogger
import org.koin.core.context.startKoin
import com.ovi.handoff.mobile.domain.di.domainModule
import com.ovi.handoff.mobile.data.di.dataModule
import com.ovi.handoff.mobile.feature.pairing.di.pairingModule
import com.ovi.handoff.mobile.feature.approval.di.approvalModule
import org.koin.androidx.workmanager.koin.workManagerFactory
import timber.log.Timber

class HandoffApplication : Application() {
    override fun onCreate() {
        super.onCreate()

        Timber.plant(Timber.DebugTree())

        startKoin {
            androidLogger()
            androidContext(this@HandoffApplication)
            workManagerFactory()
            modules(
                domainModule,
                dataModule,
                pairingModule,
                approvalModule
            )
        }
    }
}
