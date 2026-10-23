package com.bnx.clipme

import android.app.DownloadManager
import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/**
 * Last downloads, kept in SharedPreferences. [kind] says where the file lives:
 * "dm" = DownloadManager id in [id]; "file" = private file path in [uri]; "tree" = SAF document uri in [uri].
 */
object History {
    data class Item(
        val id: Long, val network: String, val link: String, val time: Long,
        val kind: String = "dm", val uri: String? = null,
    )
    enum class State { RUNNING, DONE, FAILED, GONE }

    private const val MAX = 30
    private fun prefs(c: Context) = c.getSharedPreferences("history", Context.MODE_PRIVATE)

    fun all(c: Context): List<Item> {
        val a = JSONArray(prefs(c).getString("items", "[]"))
        return (0 until a.length()).map {
            a.getJSONObject(it).run {
                Item(getLong("id"), getString("network"), getString("link"), getLong("time"), optString("kind", "dm"), optString("uri").ifEmpty { null })
            }
        }
    }

    fun add(c: Context, item: Item) = save(c, (listOf(item) + all(c)).take(MAX))

    fun clear(c: Context) = save(c, emptyList())

    private fun save(c: Context, items: List<Item>) {
        val a = JSONArray()
        items.forEach {
            a.put(
                JSONObject().put("id", it.id).put("network", it.network).put("link", it.link).put("time", it.time)
                    .put("kind", it.kind).put("uri", it.uri ?: "")
            )
        }
        prefs(c).edit().putString("items", a.toString()).apply()
    }

    fun state(c: Context, item: Item): State = when (item.kind) {
        "file" -> if (File(item.uri!!).exists()) State.DONE else State.GONE
        "tree" -> if (DocumentFile.fromSingleUri(c, Uri.parse(item.uri))?.exists() == true) State.DONE else State.GONE
        else -> dmState(c, item.id)
    }

    private fun dmState(c: Context, id: Long): State {
        val cursor = c.getSystemService(DownloadManager::class.java).query(DownloadManager.Query().setFilterById(id))
        cursor.use {
            if (!it.moveToFirst()) return State.GONE // removed from the device
            return when (it.getInt(it.getColumnIndexOrThrow(DownloadManager.COLUMN_STATUS))) {
                DownloadManager.STATUS_SUCCESSFUL -> State.DONE
                DownloadManager.STATUS_FAILED -> State.FAILED
                else -> State.RUNNING
            }
        }
    }
}