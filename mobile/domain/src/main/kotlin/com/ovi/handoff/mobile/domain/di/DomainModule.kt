package com.ovi.handoff.mobile.domain.di

import com.ovi.handoff.mobile.domain.usecase.AbortSessionUseCase
import com.ovi.handoff.mobile.domain.usecase.ClearRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ExpireOverdueRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.GetRequestHistoryUseCase
import com.ovi.handoff.mobile.domain.usecase.ObserveRequestsUseCase
import com.ovi.handoff.mobile.domain.usecase.PairDeviceUseCase
import com.ovi.handoff.mobile.domain.usecase.SubmitDecisionUseCase
import com.ovi.handoff.mobile.domain.usecase.UnpairDeviceUseCase
import org.koin.core.module.dsl.factoryOf
import org.koin.dsl.module

public val domainModule: org.koin.core.module.Module = module {
    factoryOf(::PairDeviceUseCase)
    factoryOf(::UnpairDeviceUseCase)
    factoryOf(::ObserveRequestsUseCase)
    factoryOf(::SubmitDecisionUseCase)
    factoryOf(::GetRequestHistoryUseCase)
    factoryOf(::ClearRequestHistoryUseCase)
    factoryOf(::AbortSessionUseCase)
    factoryOf(::ExpireOverdueRequestsUseCase)
}
