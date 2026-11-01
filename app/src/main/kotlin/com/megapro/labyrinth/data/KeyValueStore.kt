package com.megapro.labyrinth.data

import android.content.Context
import androidx.core.content.edit

/** Where the lists live: SharedPreferences in the app, a map in tests. */
interface KeyValueStore {
  fun get(key: String): String?
  fun put(key: String, value: String)
  fun keys(): Set<String>
}

class PrefsStore(context: Context, name: String) : KeyValueStore {
  private val prefs = context.getSharedPreferences(name, Context.MODE_PRIVATE)
  override fun get(key: String): String? = prefs.getString(key, null)
  override fun put(key: String, value: String) = prefs.edit { putString(key, value) }
  override fun keys(): Set<String> = prefs.all.keys
}

class MemoryStore : KeyValueStore {
  private val map = HashMap<String, String>()
  override fun get(key: String): String? = map[key]
  override fun put(key: String, value: String) { map[key] = value }
  override fun keys(): Set<String> = map.keys.toSet()
}
