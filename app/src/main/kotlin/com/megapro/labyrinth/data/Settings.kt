package com.megapro.labyrinth.data

import android.content.Context
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

/** The player's one setting: whether the music plays. Kept in preferences. The game itself has no
 *  options, so every player's result comes from the same game. */
class Settings(private val store: KeyValueStore) {
  constructor(context: Context) : this(PrefsStore(context, "settings"))

  var musicMuted by mutableStateOf(store.get("musicMuted") == "1")
    private set

  fun muteMusic(muted: Boolean) { musicMuted = muted; store.put("musicMuted", if (muted) "1" else "0") }
}
