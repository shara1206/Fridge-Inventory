package com.sharawang.fridge.data.repo

import androidx.room.RoomDatabase
import androidx.room.withTransaction

/**
 * Runs a block of DAO calls as one database transaction.
 *
 * An interface rather than a direct dependency on [RoomDatabase] so the repository's
 * multi-step writes stay atomic without the repository having to know which database it is
 * talking to. There is deliberately no no-op implementation: one would let a call site lose
 * atomicity without anyone noticing.
 */
interface TransactionRunner {
    suspend fun <R> run(block: suspend () -> R): R
}

/** Room's coroutine transaction. Nested calls join the outer transaction. */
class RoomTransactionRunner(private val database: RoomDatabase) : TransactionRunner {
    override suspend fun <R> run(block: suspend () -> R): R = database.withTransaction(block)
}
