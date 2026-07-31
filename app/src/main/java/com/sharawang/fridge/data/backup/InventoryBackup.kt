package com.sharawang.fridge.data.backup

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
    const val VERSION = 1

    fun encode(items: List<FoodItem>, exportedOn: LocalDate = LocalDate.now()): String {
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
        return JSONObject().apply {
            put("format", FORMAT)
            put("version", VERSION)
            put("exportedOn", exportedOn.toString())
            put("items", array)
        }.toString(2)
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
