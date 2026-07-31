package com.sharawang.fridge.data.labels

import android.content.Context
import androidx.annotation.StringRes
import com.sharawang.fridge.R
import com.sharawang.fridge.data.local.StorageArea

/**
 * The ten printed cards carry headings of their own — "冰淇淋 · 甜点" says more on a freezer
 * drawer than the category name "零食" does. They are separate resources rather than reused
 * category labels so the sheet can read like a sign while the app keeps reading like an app.
 *
 * Extra cards fall back to the category label: they have no printed heading to match.
 */
@StringRes
fun LabelZone.headingRes(index: Int): Int =
    if (printed && index in PRINTED_HEADINGS.indices) PRINTED_HEADINGS[index] else category.labelRes

private val PRINTED_HEADINGS = intArrayOf(
    R.string.zone_dairy_eggs,
    R.string.zone_vegetables,
    R.string.zone_fruit,
    R.string.zone_tofu_soy,
    R.string.zone_prepared,
    R.string.zone_prepared,
    R.string.zone_meat,
    R.string.zone_seafood,
    R.string.zone_snacks,
    R.string.zone_frozen_staples
)

/** Resolves headings for the PDF renderer, which has no Context of its own. */
fun labelStrings(context: Context): LabelPdf.Strings = object : LabelPdf.Strings {
    override fun heading(index: Int, zone: LabelZone): String =
        context.getString(zone.headingRes(index))

    override fun area(area: StorageArea): String = context.getString(area.labelRes)
}
