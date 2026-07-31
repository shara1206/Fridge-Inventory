package com.sharawang.fridge.data.labels

import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import android.graphics.pdf.PdfDocument
import com.sharawang.fridge.data.local.StorageArea
import java.io.OutputStream
import kotlin.math.ceil
import kotlin.math.max
import kotlin.math.min

/**
 * Draws a [LabelSheet] as an A4 PDF, ten cards to a page, sized to be cut apart and taped
 * inside the fridge door.
 *
 * The card artwork is drawn rather than stamped onto a scanned template: vector output stays
 * sharp at any print size, the app ships no extra asset, and a heading can change without
 * anyone opening a design tool.
 *
 * Everything is measured in PostScript points and the page is declared at 72 dpi, so a text
 * size of 9.6 here really is 9.6pt on paper.
 */
object LabelPdf {

    /**
     * Headings come from resources, so the renderer never touches a Context. The index is
     * passed alongside the zone because the ten printed headings are positional — slot four
     * is slot four whatever ends up in it.
     */
    interface Strings {
        fun heading(index: Int, zone: LabelZone): String
        fun area(area: StorageArea): String
    }

    private const val PAGE_W = 595
    private const val PAGE_H = 842

    private const val CARD_W = 242.7f
    private const val CARD_H = 129.3f
    private const val CARD_RADIUS = 12f
    private val COLUMN_X = floatArrayOf(37.4f, 315.2f)
    private const val FIRST_ROW_Y = 47.3f
    private const val ROW_STEP = 154.5f

    private const val INSET = 11.4f
    private const val PANEL_TOP = 51.1f
    private const val PANEL_W = 219.9f
    private const val PANEL_H = 69.7f

    private const val HEADING_SIZE = 18f
    private const val CHIP_SIZE = 9f

    // One column reads better, so it is used until the list simply will not fit.
    private const val ONE_COLUMN_MAX = 5
    private const val SMALLEST_TEXT = 5.5f

    fun render(sheet: LabelSheet, strings: Strings, out: OutputStream) {
        val document = PdfDocument()
        try {
            val slots = ArrayList<LabelZone?>(sheet.zones)
            // Pad the last page out with blank cards: a spare with ruled lines is useful,
            // a half-empty sheet of white space is not.
            while (slots.size % LabelSheet.CARDS_PER_PAGE != 0) slots.add(null)

            slots.chunked(LabelSheet.CARDS_PER_PAGE).forEachIndexed { pageIndex, pageSlots ->
                val info = PdfDocument.PageInfo
                    .Builder(PAGE_W, PAGE_H, pageIndex + 1)
                    .create()
                val page = document.startPage(info)
                pageSlots.forEachIndexed { slot, zone ->
                    val zoneIndex = pageIndex * LabelSheet.CARDS_PER_PAGE + slot
                    drawCard(
                        canvas = page.canvas,
                        x = COLUMN_X[slot % 2],
                        y = FIRST_ROW_Y + (slot / 2) * ROW_STEP,
                        zone = zone,
                        index = zoneIndex,
                        strings = strings
                    )
                }
                document.finishPage(page)
            }
            document.writeTo(out)
        } finally {
            document.close()
        }
    }

    private fun drawCard(
        canvas: Canvas,
        x: Float,
        y: Float,
        zone: LabelZone?,
        index: Int,
        strings: Strings
    ) {
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)

        // Dashed guide showing where to cut, outside the card so scissors never cross artwork.
        fill.style = Paint.Style.STROKE
        fill.strokeWidth = 0.5f
        fill.color = LabelPalette.CUT_GUIDE
        fill.pathEffect = DashPathEffect(floatArrayOf(2.5f, 2.5f), 0f)
        canvas.drawRect(x - 6.2f, y - 6.2f, x + CARD_W + 6.2f, y + CARD_H + 6.2f, fill)
        fill.pathEffect = null

        fill.style = Paint.Style.FILL
        fill.color = LabelPalette.cardFill(index, zone?.printed == true)
        canvas.drawRoundRect(
            RectF(x, y, x + CARD_W, y + CARD_H),
            CARD_RADIUS,
            CARD_RADIUS,
            fill
        )

        val panel = RectF(x + INSET, y + PANEL_TOP, x + INSET + PANEL_W, y + PANEL_TOP + PANEL_H)

        if (zone != null) {
            drawHeading(canvas, x, y, zone, index, strings)
        } else {
            // A blank card still needs its panel where the eye expects it, so the sheet
            // stays a grid rather than looking like a printing fault.
            panel.offsetTo(panel.left, y + 16f)
            panel.bottom = y + CARD_H - 16f
        }

        fill.color = LabelPalette.PANEL
        canvas.drawRoundRect(panel, 8f, 8f, fill)

        val lines = zone?.items.orEmpty().map { it.labelLine() }
        if (lines.isEmpty()) drawRules(canvas, panel) else drawLines(canvas, panel, lines)
    }

    private fun drawHeading(
        canvas: Canvas,
        x: Float,
        y: Float,
        zone: LabelZone,
        index: Int,
        strings: Strings
    ) {
        val text = Paint(Paint.ANTI_ALIAS_FLAG)
        text.textSize = CHIP_SIZE
        text.typeface = Typeface.DEFAULT
        val chip = strings.area(zone.storageArea)
        val chipWidth = text.measureText(chip)

        val pill = RectF(x + INSET, y + 9.1f, x + INSET + chipWidth + 16f, y + 26.1f)
        val fill = Paint(Paint.ANTI_ALIAS_FLAG)
        fill.color = LabelPalette.chipFill(index, zone.printed)
        canvas.drawRoundRect(pill, pill.height() / 2f, pill.height() / 2f, fill)

        text.color = LabelPalette.PANEL
        canvas.drawText(chip, pill.left + 8f, pill.bottom - 5.5f, text)

        text.textSize = HEADING_SIZE
        text.color = LabelPalette.HEADING
        text.typeface = Typeface.create(Typeface.DEFAULT, Typeface.NORMAL)
        canvas.drawText(strings.heading(index, zone), x + INSET, y + 47f, text)
    }

    /** Four faint rules, so an empty card invites a pen instead of looking unfinished. */
    private fun drawRules(canvas: Canvas, panel: RectF) {
        val rule = Paint(Paint.ANTI_ALIAS_FLAG)
        rule.style = Paint.Style.STROKE
        rule.strokeWidth = 0.6f
        rule.color = LabelPalette.RULE
        rule.pathEffect = DashPathEffect(floatArrayOf(1.2f, 2.2f), 0f)

        val step = panel.height() / 5f
        for (line in 1..4) {
            val yy = panel.top + step * line
            canvas.drawLine(panel.left + 10f, yy, panel.right - 10f, yy, rule)
        }
    }

    private fun drawLines(canvas: Canvas, panel: RectF, lines: List<String>) {
        val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        paint.color = LabelPalette.INK
        paint.typeface = Typeface.DEFAULT

        val padX = 9f
        val padY = 7f
        val twoColumn = lines.size > ONE_COLUMN_MAX
        val columns = if (twoColumn) 2 else 1
        val perColumn = ceil(lines.size / columns.toFloat()).toInt()

        var size = if (twoColumn) 8.4f else 9.6f
        var leading = if (twoColumn) 10f else 11.4f

        val columnWidth = (panel.width() - 2 * padX) / columns
        val limit = columnWidth - if (twoColumn) 6f else 2f

        // One long name would otherwise run into the next column. Stepping the whole card
        // down a size keeps the list looking like a list; clipping one line does not.
        paint.textSize = size
        val widest = lines.maxOf { paint.measureText(it) }
        if (widest > limit) size = max(SMALLEST_TEXT, size * limit / widest)
        paint.textSize = size

        leading = min(leading, (panel.height() - 2 * padY) / perColumn)
        val slack = panel.height() - perColumn * leading
        val top = panel.top + max(padY, slack / 2f) + size

        lines.forEachIndexed { i, line ->
            val column = i / perColumn
            val row = i % perColumn
            canvas.drawText(
                line,
                panel.left + padX + column * columnWidth,
                top + row * leading,
                paint
            )
        }
    }
}
