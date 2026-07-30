package com.sharawang.fridge.receipt.parser

import com.sharawang.fridge.data.local.Store

/** Fallback for any other store. Same line shape, no store-specific tweaks. */
class GenericParser : LineBasedParser() {
    override val store = Store.OTHER
    override fun matches(rawText: String): Boolean = true
}
