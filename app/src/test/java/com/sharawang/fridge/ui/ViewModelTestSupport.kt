package com.sharawang.fridge.ui

import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import com.sharawang.fridge.data.local.AppDatabase
import com.sharawang.fridge.data.repo.InventoryRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.test.UnconfinedTestDispatcher
import kotlinx.coroutines.test.resetMain
import kotlinx.coroutines.test.setMain
import kotlinx.coroutines.withTimeout
import org.junit.rules.TestWatcher
import org.junit.runner.Description

/**
 * Points `viewModelScope` (Dispatchers.Main) at an unconfined dispatcher, so a ViewModel's
 * coroutines start on the calling thread and resume wherever Room finishes its work.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class MainDispatcherRule : TestWatcher() {
    override fun starting(description: Description) {
        Dispatchers.setMain(UnconfinedTestDispatcher())
    }

    override fun finished(description: Description) {
        Dispatchers.resetMain()
    }
}

/** A real in-memory Room database, with the repository production code uses. */
class TestDatabase {
    val database: AppDatabase = Room.inMemoryDatabaseBuilder(
        ApplicationProvider.getApplicationContext(),
        AppDatabase::class.java
    ).allowMainThreadQueries().build()

    val repository = InventoryRepository(database)

    fun close() = database.close()
}

/**
 * Waits, in real time, for [predicate] to hold.
 *
 * ViewModels launch their writes and return immediately, and Room runs them on its own
 * threads, so a test has to wait for the result to show up rather than assume it already
 * has. `runBlocking` rather than `runTest`: virtual time would make the timeout meaningless.
 */
fun <T> Flow<T>.await(predicate: (T) -> Boolean): T = runBlocking {
    withTimeout(5_000) { first(predicate) }
}
