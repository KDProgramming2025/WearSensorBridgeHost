package com.example.wearsensorbridgehost.mobile

import android.content.Context
import android.util.Log
import androidx.work.Worker
import androidx.work.WorkerParameters

class SyncWorker(appContext: Context, workerParams: WorkerParameters) :
    Worker(appContext, workerParams) {

    override fun doWork(): Result {
        Log.d("SyncWorker", "Performing background sync...")
        // Implement background sync logic here, e.g. sending cached data to server
        return Result.success()
    }
}
