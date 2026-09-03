package com.ovi.handoff.mobile.feature.pairing.di

import com.ovi.handoff.mobile.feature.pairing.viewmodel.PairingViewModel
import org.koin.core.module.dsl.viewModel
import org.koin.dsl.module

public val pairingModule = module {
    viewModel { PairingViewModel(get()) }
}
