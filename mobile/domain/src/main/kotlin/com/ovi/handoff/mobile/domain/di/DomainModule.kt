package com.ovi.handoff.mobile.domain.di

import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::PairDeviceUseCase)
    factoryOf(::ObserveRequestsUseCase)
    factoryOf(::SendDecisionUseCase)
}
