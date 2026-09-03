package com.ovi.handoff.mobile.data.di

import com.ovi.handoff.mobile.data.repository.PairingRepositoryImpl
import com.ovi.handoff.mobile.data.repository.RelayRepositoryImpl
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import androidx.room.Room
import com.ovi.handoff.mobile.data.local.HandoffDatabase
import org.koin.android.ext.koin.androidContext
import org.koin.core.module.dsl.singleOf
import org.koin.dsl.bind
import org.koin.dsl.module

public const val DEFAULT_RELAY_HOST: String = "agentapprove-relay.ismamhasanovi.workers.dev"

val dataModule = module {
    single<PairingRepository> { PairingRepositoryImpl(androidContext()) }
    
    single {
        Room.databaseBuilder(
            androidContext(),
            HandoffDatabase::class.java,
            "handoff_db"
        ).build()
    }
    
    single { get<HandoffDatabase>().requestDao() }
    
    single<RelayRepository> { RelayRepositoryImpl(get(), DEFAULT_RELAY_HOST) }
}
