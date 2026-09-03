package com.ovi.handoff.mobile.domain.di

import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SendDecisionUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

val domainModule = module {
    factoryOf(::PairDeviceUseCase)
    factoryOf(::ObserveRequestsUseCase)
    factoryOf(::SendDecisionUseCase)
    factoryOf(::GetRequestHistoryUseCase)
    factoryOf(::AbortSessionUseCase)
}
