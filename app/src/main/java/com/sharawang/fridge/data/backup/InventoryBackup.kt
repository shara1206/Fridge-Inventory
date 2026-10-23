package com.sharawang.fridge.data.backup

import com.sharawang.fridge.data.local.ConsumptionEvent
import com.sharawang.fridge.data.local.FinishReason
import com.sharawang.fridge.data.local.FoodCategory
import com.sharawang.fridge.data.local.FoodItem
import com.sharawang.fridge.data.local.StorageArea
import com.sharawang.fridge.data.local.Store
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

/** Raised when a file the user picked is not a backup this app can read. */
class BackupFormatException(message: String) : Exception(message)

/**
 * Reads and writes the inventory as a plain JSON document.
 *
 * Hand-rolled with `org.json` rather than a serialization library on purpose: the format is
 * a file the user keeps and may edit by hand, so it should stay boring, flat and readable,
 * and it should not silently change shape when a dependency is upgraded.
 *
 * Unknown enum values decay to their neutral member instead of failing the whole import —
 * losing a category label is a much smaller loss than losing the file.
 */
object InventoryBackup {

    const val FORMAT = "fridge-inventory"

    /**
     * 2 added `events`, the consumption log. A version 1 file still reads: it simply has no
     * log, which is exactly true of the app that wrote it.
     */
    const val VERSION = 2

    fun encode(
        items: List<FoodItem>,
        exportedOn: LocalDate = LocalDate.now(),
        events: List<ConsumptionEvent> = emptyList()
    ): String {
        val array = JSONArray()
        items.forEach { item ->
            array.put(
                JSONObject().apply {
                    put("name", item.name)
                    put("category", item.category.name)
                    put("storageArea", item.storageArea.name)
                    put("quantity", item.quantity)
                    put("unit", item.unit)
                    put("purchasedOn", item.purchasedOn.toString())
                    put("expiresOn", item.expiresOn?.toString() ?: JSONObject.NULL)
                    put("store", item.store.name)
                    put("priceCents", item.priceCents ?: JSONObject.NULL)
                    put("notes", item.notes)
                    put("finishedOn", item.finishedOn?.toString() ?: JSONObject.NULL)
                    put("finishedReason", item.finishedReason?.name ?: JSONObject.NULL)
                }
            )
        }
        val eventArray = JSONArray()
        events.forEach { event ->
            eventArray.put(
                JSONObject().apply {
                    put("name", event.name)
                    put("category", event.category.name)
                    put("storageArea", event.storageArea.name)
                    put("store", event.store.name)
                    put("quantity", event.quantity)
                    put("reason", event.reason?.name ?: JSONObject.NULL)
                    put("occurredOn", event.occurredOn.toString())
                    put("recordedAt", event.recordedAt)
                    put("valueCents", event.valueCents ?: JSONObject.NULL)
                }
            )
        }

        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedOn", exportedOn.toString())
            put("items", array)
            put("events", eventArray)
        }.toString(2)
    }

    /**
     * The consumption log out of a backup file. Empty for a version 1 file, and for any
     * file written before the log existed.
     *
     * `itemId` is deliberately dropped: it pointed at a row id in the database that wrote
     * the file, and carrying it across would make undo reach for someone else's item. The
     * entries keep their own snapshot of what the food was, which is what history reads.
     */
    fun decodeEvents(text: String): List<ConsumptionEvent> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupFormatException("Not a JSON document: ${e.message}")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("Not a $FORMAT backup")
        }
        val array = root.optJSONArray("events") ?: return emptyList()

        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val name = json.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            ConsumptionEvent(
                itemId = null,
                name = name,
                category = json.enum("category", FoodCategory.entries, FoodCategory.OTHER),
                storageArea = json.enum("storageArea", StorageArea.entries, StorageArea.FRIDGE),
                store = json.enum("store", Store.entries, Store.OTHER),
                quantity = json.optDouble("quantity", 1.0).takeIf { it > 0 } ?: 1.0,
                reason = if (json.isNull("reason")) {
                    null
                } else {
                    json.enum("reason", FinishReason.entries, FinishReason.USED)
                },
                occurredOn = json.date("occurredOn") ?: LocalDate.now(),
                recordedAt = json.optLong("recordedAt", 0L),
                valueCents = if (json.isNull("valueCents")) null else json.optInt("valueCents")
            )
        }
    }

    /**
     * Ids are not carried across: the rows are re-inserted into whatever database is
     * receiving them, and reusing an id from another install would collide.
     */
    fun decode(text: String): List<FoodItem> {
        val root = try {
            JSONObject(text)
        } catch (e: Exception) {
            throw BackupFormatException("Not a JSON document: ${e.message}")
        }
        if (root.optString("format") != FORMAT) {
            throw BackupFormatException("Not a $FORMAT backup")
        }
        if (root.optInt("version", 0) > VERSION) {
            throw BackupFormatException("Backup was written by a newer version of the app")
        }
        val array = root.optJSONArray("items")
            ?: throw BackupFormatException("Backup has no items")

        return (0 until array.length()).mapNotNull { index ->
            val json = array.optJSONObject(index) ?: return@mapNotNull null
            val name = json.optString("name").trim()
            if (name.isEmpty()) return@mapNotNull null
            FoodItem(
                name = name,
                category = json.enum(
                    "category",
                    FoodCategory.entries,
                    FoodCategory.OTHER
                ),
                storageArea = json.enum(
                    "storageArea",
                    StorageArea.entries,
                    StorageArea.FRIDGE
                ),
                quantity = json.optDouble("quantity", 1.0).takeIf { it > 0 } ?: 1.0,
                unit = json.optString("unit").ifBlank { "ea" },
                purchasedOn = json.date("purchasedOn") ?: LocalDate.now(),
                expiresOn = json.date("expiresOn"),
                store = json.enum("store", Store.entries, Store.OTHER),
                priceCents = if (json.isNull("priceCents")) null else json.optInt("priceCents"),
                notes = json.optString("notes"),
                finishedOn = json.date("finishedOn"),
                finishedReason = if (json.isNull("finishedReason")) {
                    null
                } else {
                    json.enum("finishedReason", FinishReason.entries, FinishReason.USED)
                }
            )
        }
    }

    private fun <T : Enum<T>> JSONObject.enum(key: String, values: List<T>, fallback: T): T {
        val raw = optString(key)
        return values.firstOrNull { it.name.equals(raw, ignoreCase = true) } ?: fallback
    }

    private fun JSONObject.date(key: String): LocalDate? {
        if (isNull(key)) return null
        val raw = optString(key).trim()
        if (raw.isEmpty()) return null
        return try {
            LocalDate.parse(raw)
        } catch (e: Exception) {
            null
        }
    }
}
