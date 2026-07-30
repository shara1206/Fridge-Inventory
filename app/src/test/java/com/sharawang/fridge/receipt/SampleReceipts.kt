package com.sharawang.fridge.receipt

/**
 * OCR-style text captured from real receipt layouts (values edited). These are the
 * regression fixtures for the parsers: when a parse goes wrong on a real trip, paste the
 * raw text from the Purchase row in here and fix the parser against it.
 */
object SampleReceipts {

    val H_MART = """
        H MART
        1234 Any Street
        Tel: 703-555-0134
        04/12/2026  14:22
        0000012345678 ORG SPNCH 5OZ        4.99
        0000098765432 PORK BELLY SLICE    12.45
        0.86 lb @ 14.47/lb
        NAPA CABBAGE                       2.57
        TOFU FIRM 19OZ                     2.29
        SUBTOTAL                          22.30
        TAX                                0.89
        TOTAL                             23.19
        VISA ****1234                     23.19
        THANK YOU
    """.trimIndent()

    val TT = """
        T&T SUPERMARKET
        大統華
        Store #124
        2026-04-15
        上海青 SHANGHAI BOK CHOY            3.29 T
        豬肉 PORK BELLY 500G                8.99 T
        凍餃子 FRZN DMPLNG                  6.49
        2 @ 3.25
        GST                                 0.65
        SUBTOTAL                           18.77
        TOTAL                              19.42
        INTERAC DEBIT CARD                 19.42
    """.trimIndent()

    val TRADER_JOES = """
        TRADER JOE'S
        Store 546  Cambridge MA
        04/20/26
        ORGANIC BABY SPINACH               2.99
        MANDARIN ORANGE CHICKEN            6.98
        2 @ 3.49
        UNSWT VANILLA ALMOND BEVERAGE      2.79
        ITEMS 4
        SUBTOTAL                          12.76
        TOTAL                             12.76
    """.trimIndent()

    val WHOLE_FOODS = """
        WHOLE FOODS MARKET
        04/22/2026
        365 ORG WHL MILK 1GAL              4.29
        ORG STRWBRY 1LB                    5.99
        AIRCHL CHKN BRST                  11.24
        1.42 lb @ 7.92/lb
        PRIME SAVINGS                     -2.00
        SUBTOTAL                          21.52
        TOTAL                             22.94
    """.trimIndent()

    val UNKNOWN_STORE = """
        CORNER GROCERY
        05/02/2026
        WHOLE MILK 1/2 GAL                 3.49
        BANANAS                            1.18
        TOTAL                              4.67
    """.trimIndent()
}
