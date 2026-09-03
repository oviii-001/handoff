package com.ovi.handoff.mobile.feature.approval.di

import com.ovi.handoff.mobile.feature.approval.viewmodel.ApprovalViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val approvalModule: org.koin.core.module.Module = module {
    viewModel { parameters ->
        ApprovalViewModel(
            pairId = parameters.get(),
            observeRequestsUseCase = get(),
            sendDecisionUseCase = get()
        )
    }
}
