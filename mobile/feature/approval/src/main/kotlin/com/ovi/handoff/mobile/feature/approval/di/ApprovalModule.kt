package com.ovi.handoff.mobile.feature.approval.di

import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel
import com.ovi.handoff.mobile.feature.approval.viewmodel.SettingsViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val approvalModule: org.koin.core.module.Module = module {
    viewModel { parameters ->
        ApprovalViewModel(
            initialPairId = parameters.getOrNull(),
            observeRequestsUseCase = get(),
            sendDecisionUseCase = get(),
            getRequestHistoryUseCase = get(),
            clearRequestHistoryUseCase = get(),
            abortSessionUseCase = get(),
            pairDeviceUseCase = get(),
            unpairDeviceUseCase = get(),
            pairingRepository = get()
        )
    }

    viewModel { parameters ->
        SettingsViewModel(
            pairId = parameters.get(),
            unpairDeviceUseCase = get()
        )
    }
}
