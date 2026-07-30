package com.sharawang.fridge

import android.app.Application
import com.sharawang.fridge.receipt.ReceiptImageStore
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

class FridgeApplication : Application() {

    lateinit var container: AppContainer
        private set

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    override fun onCreate() {
        super.onCreate()
        container = AppContainer(this)

        // Housekeeping, off the main thread: old receipt photos are dead weight once the
        // OCR text has been stored on the Purchase row.
        appScope.launch {
            runCatching { ReceiptImageStore.pruneOlderThan(this@FridgeApplication) }
        }
    }
}
