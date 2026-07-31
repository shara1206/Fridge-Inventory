package com.sharawang.fridge.data.labels

/**
 * The colours the sheet was first printed in, kept as plain ARGB ints so the PDF renderer
 * and the on-screen preview can share one source of truth.
 *
 * These are not theme colours and deliberately do not follow dark mode: paper does not have
 * a dark mode, and the preview is a preview of paper.
 */
object LabelPalette {

    /** One per printed zone, in [LabelSheet.PRINTED_ZONES] order. */
    val cardFills = intArrayOf(
        0xFFD9D2BE.toInt(), // dairy & eggs
        0xFFC8D0C1.toInt(), // vegetables
        0xFFD6C4C6.toInt(), // fruit
        0xFFC3CDD3.toInt(), // tofu — inherited from the seasoning card it replaced
        0xFFCEC6D0.toInt(), // ready to eat, fridge
        0xFFCEC6D0.toInt(), // ready to eat, freezer
        0xFFD2BEC1.toInt(), // meat
        0xFFC1CDD2.toInt(), // seafood
        0xFFCDC4CF.toInt(), // ice cream & dessert
        0xFFD6CFB9.toInt() // frozen staples
    )

    /** The pill behind the 冷藏 / 冷冻 chip: the same hue, taken down to hold white text. */
    val chipFills = intArrayOf(
        0xFF7E7658.toInt(),
        0xFF657059.toInt(),
        0xFF7B6062.toInt(),
        0xFF5D6C74.toInt(),
        0xFF6E6674.toInt(),
        0xFF6E6674.toInt(),
        0xFF78595D.toInt(),
        0xFF5B6B72.toInt(),
        0xFF6C6472.toInt(),
        0xFF7C7355.toInt()
    )

    /** Extra cards are neutral on purpose — colour is how the ten fixed zones are told apart. */
    val EXTRA_CARD = 0xFFCFCAC2.toInt()
    val EXTRA_CHIP = 0xFF6E675C.toInt()

    val PANEL = 0xFFFFFFFF.toInt()
    val INK = 0xFF4F4A45.toInt()
    val HEADING = 0xFF5D5750.toInt()
    val RULE = 0xFFCFCBC4.toInt()

    /** For the "nothing here yet" line on screen: the printed rules are too faint to read. */
    val MUTED = 0xFF9A948B.toInt()
    val CUT_GUIDE = 0xFFD8D4CD.toInt()

    fun cardFill(index: Int, printed: Boolean): Int =
        if (printed && index in cardFills.indices) cardFills[index] else EXTRA_CARD

    fun chipFill(index: Int, printed: Boolean): Int =
        if (printed && index in chipFills.indices) chipFills[index] else EXTRA_CHIP
}
