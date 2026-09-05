package com.ovi.handoff.androidApp.worker

import android.content.Context
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters
import com.ovi.handoff.mobile.domain.repository.PairingRepository
import com.ovi.handoff.mobile.domain.repository.RelayRepository
import org.koin.core.component.KoinComponent
import org.koin.core.component.inject

class SyncRequestsWorker(
    appContext: Context,
    workerParams: WorkerParameters
) : CoroutineWorker(appContext, workerParams), KoinComponent {

    private val relayRepository: RelayRepository by inject()
    private val pairingRepository: PairingRepository by inject()

    override suspend fun doWork(): Result {
        val pairId = pairingRepository.getPairId()
        if (pairId == null) {
            return Result.failure()
        }

        val result = relayRepository.connect(pairId)
        return if (result.isSuccess) {
            Result.success()
        } else {
            Result.retry()
        }
    }
}
