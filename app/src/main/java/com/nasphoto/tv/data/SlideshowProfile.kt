package com.nasphoto.tv.data

import com.nasphoto.tv.data.model.ScreensaverSettings
import com.nasphoto.tv.data.model.ScreensaverSettings.SlideshowTransition
import org.json.JSONObject

data class SlideshowProfile(
    val intervalSec: Int,
    val transition: SlideshowTransition,
    val musicEnabled: Boolean,
    val trackName: String? = null,
    val frame: Frame = Frame.NONE,
) {
    enum class Frame(val label: String) {
        NONE("无相框"),
        WHITE("细白边"),
    }

    internal fun stepInterval(reverse: Boolean): SlideshowProfile {
        val steps = listOf(3, 5, 10, 15, 30)
        val index = steps.indexOf(intervalSec).coerceAtLeast(0)
        return copy(intervalSec = steps[(index + if (reverse) -1 else 1).mod(steps.size)])
    }

    internal fun stepTransition(reverse: Boolean): SlideshowProfile {
        val options = SlideshowTransition.entries
        return copy(transition = options[(transition.ordinal + if (reverse) -1 else 1).mod(options.size)])
    }

    internal fun encode(): String = JSONObject()
        .put("interval", intervalSec)
        .put("transition", transition.name)
        .put("music", musicEnabled)
        .put("track", trackName)
        .put("frame", frame.name)
        .toString()

    companion object {
        internal fun from(settings: ScreensaverSettings): SlideshowProfile =
            SlideshowProfile(settings.slideshowIntervalSec, settings.slideshowTransition, settings.musicEnabled)

        internal fun decode(raw: String): SlideshowProfile? = runCatching {
            val entry = JSONObject(raw)
            SlideshowProfile(
                intervalSec = entry.getInt("interval").coerceIn(3, 30),
                transition = SlideshowTransition.valueOf(entry.getString("transition")),
                musicEnabled = entry.getBoolean("music"),
                trackName = entry.optString("track").takeIf { !entry.isNull("track") && it.isNotBlank() },
                frame = Frame.entries.firstOrNull { it.name == entry.optString("frame") } ?: Frame.NONE,
            )
        }.getOrNull()
    }
}
