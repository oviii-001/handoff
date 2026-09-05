package com.ovi.handoff.mobile.data.di

import androidx.room.Room
import com.ovi.handoff.mobile.data.local.HandoffDatabase
import com.ovi.handoff.mobile.data.local.HandoffMigrations
import com.ovi.handoff.mobile.data.repository.PairingRepositoryImpl
import com.ovi.handoff.mobile.data.repository.RelayRepositoryImpl
import com.ovi.handoff.mobile.data.security.AndroidDecisionSigner
import com.ovi.handoff.mobile.data.settings.SettingsRepositoryImpl
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import com.ovi.handoff.mobile.domain.security.DecisionSigner
import com.ovi.handoff.mobile.domain.settings.SettingsRepository
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.Module
import org.koin.dsl.module

public const val DEFAULT_RELAY_HOST: String = "agentapprove-relay.ismamhasanovi.workers.dev"

public val dataModule: Module = module {

    single { AndroidDecisionSigner() }
    single<DecisionSigner> { get<AndroidDecisionSigner>() }

    single { PairingRepositoryImpl(androidContext(), get<AndroidDecisionSigner>()) }
    single<PairingRepository> { get<PairingRepositoryImpl>() }

    single<SettingsRepository> { SettingsRepositoryImpl(androidContext()) }

    single {
        Room.databaseBuilder(
            androidContext(),
            HandoffDatabase::class.java,
            "handoff_db"
        )
            // Explicit migrations rather than fallbackToDestructiveMigration(): this table *is* the
            // audit trail, and wiping it on every schema bump is data loss, not a fallback.
            .addMigrations(*HandoffMigrations.ALL)
            .build()
    }

    single { get<HandoffDatabase>().requestDao() }

    single<RelayRepository> {
        RelayRepositoryImpl(
            requestDao = get(),
            pairingRepository = get(),
            signer = get(),
            defaultRelayHost = DEFAULT_RELAY_HOST,
            notificationNotifier = getOrNull()
        )
    }
}
