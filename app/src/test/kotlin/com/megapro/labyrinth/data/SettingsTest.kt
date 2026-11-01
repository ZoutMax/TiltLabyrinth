package com.megapro.labyrinth.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SettingsTest {
    @Test
    fun theMusicSwitchIsRemembered() {
        val store = MemoryStore()
        val s = Settings(store)
        assertFalse("music plays by default", s.musicMuted)
        s.muteMusic(true)
        assertTrue(Settings(store).musicMuted)
        s.muteMusic(false)
        assertFalse(Settings(store).musicMuted)
    }
}
