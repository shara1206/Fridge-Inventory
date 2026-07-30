package com.sharawang.fridge.receipt.parser

/**
 * Registry. Order matters: the generic parser must stay last because it matches
 * everything.
 *
 * Adding a store = one new [LineBasedParser] subclass plus one line here.
 */
object Parsers {

    val all: List<ReceiptParser> = listOf(
        HMartParser(),
        TtParser(),
        TraderJoesParser(),
        WholeFoodsParser(),
        GenericParser()
    )

    fun forText(rawText: String): ReceiptParser =
        all.firstOrNull { it.matches(rawText) } ?: GenericParser()
}
