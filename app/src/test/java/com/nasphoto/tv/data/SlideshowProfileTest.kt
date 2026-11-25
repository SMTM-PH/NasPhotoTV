package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.NasAccount
import com.nasphoto.tv.data.model.NasConnection
import com.nasphoto.tv.data.model.ScreensaverSettings.SlideshowTransition
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Test

class SlideshowProfileTest {
    @Test
    fun restoresNamedTrackAndMuteAfterSerialization() {
        val profile = SlideshowProfile(15, SlideshowTransition.CUT, false, "旅行 · 现场.wav", SlideshowProfile.Frame.WHITE)
        assertEquals(profile, SlideshowProfile.decode(profile.encode()))
    }

    @Test
    fun restoresLatestTrackModeAndIgnoresUnreadableProfiles() {
        val profile = SlideshowProfile(5, SlideshowTransition.SHOWCASE, true)
        assertEquals(profile, SlideshowProfile.decode(profile.encode()))
        assertNull(SlideshowProfile.decode("""{"transition":"UNKNOWN"}"""))
    }

    @Test
    fun legacyProfilesUseNoFrameAndKeepExistingSettings() {
        val legacy = """{"interval":10,"transition":"DISSOLVE","music":false,"track":"旅行.wav"}"""
        assertEquals(
            SlideshowProfile(10, SlideshowTransition.DISSOLVE, false, "旅行.wav"),
            SlideshowProfile.decode(legacy),
        )
    }

    @Test
    fun ownerSeparatesNasAndAccountButSurvivesCredentialChanges() {
        val account = NasAccount(id = "saved-account", username = "viewer", password = "first")
        val nas = NasConnection(host = "example.test", accounts = listOf(account), activeAccountId = account.id)
        val owner = nas.albumSlideshowOwnerKey()

        assertEquals(owner, nas.withAccount(account.copy(password = "second", accessCode = "changed")).albumSlideshowOwnerKey())
        assertNotEquals(owner, nas.withAccount(account.copy(username = "another")).albumSlideshowOwnerKey())
        assertNotEquals(owner, nas.copy(host = "other.test").albumSlideshowOwnerKey())
        assertNotEquals(owner, nas.copy(basePath = "/other").albumSlideshowOwnerKey())
    }
}
