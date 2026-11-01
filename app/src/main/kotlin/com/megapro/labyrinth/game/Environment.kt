package com.megapro.labyrinth.game

import kotlin.math.pow

/** The world a board is set in: it names the music, the ambience and the lamp colours. */
enum class Theme(val title: String) {
    WORKSHOP("Workshop"), FOREST("Forest"), SEA("Seashore"), DESERT("Desert"), CAVE("Cave"), TEMPLE("Temple"),
    STORM("Storm"), SNOW("Snowfield"), NIGHT("Night meadow"), VOLCANO("Volcano"), STARS("Starlight"),
}

/** The voice the board's tune is played on. */
enum class Instrument { MARIMBA, KALIMBA, MUSICBOX, BELL, FLUTE, PLUCK, ORGAN, VIBES }

/**
 * Everything that makes a board's surroundings its own, fixed by the board number so every player
 * gets the same world on the same board. The 100 boards travel through ten themes, ten boards each,
 * and within a theme every board has its own key, tempo, voice, scale, tune and lamp palette:
 * 101 environments, the workshop of the test board included. Nothing here touches the physics.
 */
class Environment private constructor(val level: Int) {
    val theme: Theme = if (level == 0) Theme.WORKSHOP else Theme.values()[1 + (level - 1) / 10]
    /** Which of the theme's ten boards this is, 0..9. */
    val variation: Int = if (level == 0) 0 else (level - 1) % 10
    private val spec = SPECS.getValue(theme)

    /** Root of the key in Hz: ten boards in a row never share a key. */
    val root: Float = 196f * 2f.pow(((level * 5) % 12) / 12f)
    val scale: FloatArray = spec.scales[variation % spec.scales.size]
    val instrument: Instrument = spec.instruments[(variation / spec.scales.size + theme.ordinal) % spec.instruments.size]
    val bpm: Int = spec.bpm + ((variation * 7) % 23) - 11
    /** How busy the tune and how present the ambience are: both grow over the hundred boards. */
    val density: Float = level / Levels.COUNT.toFloat()
    val bass: Boolean = level >= 20 || theme == Theme.TEMPLE || theme == Theme.VOLCANO
    val seed: Int = level * 7919 + 17
    val name: String = if (level == 0) theme.title else "${theme.title} ${variation + 1}"

    private val look = LOOKS.getValue(theme)

    /**
     * The three coloured lamps' colours, ARGB: the world's palette, chosen to match its sound and
     * its animals, each board turning it a few degrees and taking the lamps in its own order.
     */
    val lamps: LongArray = if (theme == Theme.WORKSHOP) look.lamps.copyOf() else LongArray(3) { i -> turn(look.lamps[(i + variation) % 3], (variation - 4.5f) * 4f) }
    /** The sun's colour low on the horizon and high overhead, ARGB. */
    val sunLow: Long = look.sunLow
    val sunHigh: Long = look.sunHigh
    /** The colours the walls' top edges catch, low sun and high: the world's sun, or the practice board's classic soft rims. */
    val rimLow: Long = look.rimLow ?: look.sunLow
    val rimHigh: Long = look.rimHigh ?: look.sunHigh
    /** The colour the light falls away into on the far side of the board, ARGB. */
    val shade: Long = look.shade
    /** The tone the board's wood takes on, ARGB, and how far (0 keeps the plain brown wood). */
    val wood: Long = look.wood
    val woodAmount: Float = if (theme == Theme.WORKSHOP) 0f else 0.5f

    private class Spec(val bpm: Int, val scales: List<FloatArray>, val instruments: List<Instrument>)

    /** A world's colours: three lamps, the sun low and high, the shade, the wood. */
    private class Look(val lamps: LongArray, val sunLow: Long, val sunHigh: Long, val shade: Long, val wood: Long, val rimLow: Long? = null, val rimHigh: Long? = null)

    companion object {
        private val cache = arrayOfNulls<Environment>(Levels.COUNT + 1)
        fun of(level: Int): Environment {
            val n = level.coerceIn(0, Levels.COUNT)
            return cache[n] ?: Environment(n).also { cache[n] = it }
        }

        // Scales as frequency ratios over the root
        private val MAJOR_PENTA = floatArrayOf(1f, 9f / 8, 5f / 4, 3f / 2, 5f / 3)
        private val MINOR_PENTA = floatArrayOf(1f, 6f / 5, 4f / 3, 3f / 2, 9f / 5)
        private val DORIAN = floatArrayOf(1f, 9f / 8, 6f / 5, 4f / 3, 3f / 2, 5f / 3, 9f / 5)
        private val JAPANESE = floatArrayOf(1f, 16f / 15, 4f / 3, 3f / 2, 8f / 5)
        private val LYDIAN = floatArrayOf(1f, 9f / 8, 5f / 4, 45f / 32, 3f / 2, 5f / 3, 15f / 8)
        private val MIXOLYDIAN = floatArrayOf(1f, 9f / 8, 5f / 4, 4f / 3, 3f / 2, 5f / 3, 16f / 9)
        private val AEOLIAN = floatArrayOf(1f, 9f / 8, 6f / 5, 4f / 3, 3f / 2, 8f / 5, 9f / 5)
        private val PHRYGIAN = floatArrayOf(1f, 16f / 15, 6f / 5, 4f / 3, 3f / 2, 8f / 5, 9f / 5)
        private val BLUES = floatArrayOf(1f, 6f / 5, 4f / 3, 45f / 32, 3f / 2, 9f / 5)

        private val SPECS = mapOf(
            Theme.WORKSHOP to Spec(100, listOf(MAJOR_PENTA), listOf(Instrument.MARIMBA)),
            Theme.FOREST to Spec(96, listOf(MAJOR_PENTA, DORIAN), listOf(Instrument.KALIMBA, Instrument.FLUTE, Instrument.MARIMBA)),
            Theme.SEA to Spec(84, listOf(LYDIAN, MAJOR_PENTA), listOf(Instrument.VIBES, Instrument.FLUTE)),
            Theme.DESERT to Spec(92, listOf(PHRYGIAN, BLUES), listOf(Instrument.PLUCK, Instrument.KALIMBA)),
            Theme.CAVE to Spec(72, listOf(MINOR_PENTA, AEOLIAN), listOf(Instrument.BELL, Instrument.VIBES)),
            Theme.TEMPLE to Spec(76, listOf(JAPANESE, DORIAN), listOf(Instrument.ORGAN, Instrument.BELL, Instrument.PLUCK)),
            Theme.STORM to Spec(118, listOf(MINOR_PENTA, PHRYGIAN), listOf(Instrument.MARIMBA, Instrument.PLUCK)),
            Theme.SNOW to Spec(80, listOf(LYDIAN, MAJOR_PENTA), listOf(Instrument.MUSICBOX, Instrument.BELL)),
            Theme.NIGHT to Spec(88, listOf(MAJOR_PENTA, DORIAN), listOf(Instrument.VIBES, Instrument.MUSICBOX, Instrument.KALIMBA)),
            Theme.VOLCANO to Spec(126, listOf(PHRYGIAN, BLUES), listOf(Instrument.ORGAN, Instrument.PLUCK, Instrument.MARIMBA)),
            Theme.STARS to Spec(70, listOf(LYDIAN, MIXOLYDIAN), listOf(Instrument.BELL, Instrument.VIBES, Instrument.FLUTE)),
        )

        private fun look(a: Long, b: Long, c: Long, sunLow: Long, sunHigh: Long, shade: Long, wood: Long, rimLow: Long? = null, rimHigh: Long? = null) = Look(longArrayOf(a, b, c), sunLow, sunHigh, shade, wood, rimLow, rimHigh)

        /** Each world's colours, matched to its sound: what you hear is what you see. */
        private val LOOKS = mapOf(
            Theme.WORKSHOP to look(0xFFFF3B3B, 0xFF3BFF6A, 0xFF3B8CFF, 0xFFFFB066, 0xFFFFFFFF, 0xFF0A1428, 0xFF7A5230, 0xFFFFC78A, 0xFFFFF1DC), // the practice board: plain red, green, blue, its classic soft rims
            Theme.FOREST to look(0xFF6BE36B, 0xFFC8F06A, 0xFF5FD3C6, 0xFFFFC46A, 0xFFFFF6DA, 0xFF0B2414, 0xFF8A6A32),   // leaf green, sunlit lime, sky teal; oak
            Theme.SEA to look(0xFF4FE3E0, 0xFF3C8CFF, 0xFFBFF4FF, 0xFFFFC9A0, 0xFFF4FBFF, 0xFF06162E, 0xFF8C8578),      // aqua, ocean blue, foam; driftwood
            Theme.DESERT to look(0xFFFFB347, 0xFFF2D27A, 0xFF5FD0E8, 0xFFFFA552, 0xFFFFF1D6, 0xFF2A1626, 0xFFB89868),   // amber, sand gold, turquoise sky; sand
            Theme.CAVE to look(0xFF56F0D8, 0xFF9B7BFF, 0xFFFFA040, 0xFF9FB4FF, 0xFFCFDAFF, 0xFF05070F, 0xFF4A4A52),     // glow-worm cyan, crystal violet, torch amber; slate
            Theme.TEMPLE to look(0xFFFFB02E, 0xFF4FD69B, 0xFFFF6F91, 0xFFFFC060, 0xFFFFF0D0, 0xFF2A0C0C, 0xFF8E3A26),   // saffron, jade, rose; red teak
            Theme.STORM to look(0xFF7FA8FF, 0xFFD9D2FF, 0xFF6FC8B0, 0xFFB8C4D8, 0xFFE6ECF5, 0xFF0C1320, 0xFF5C6066),    // steel blue, lightning violet, sea green; wet grey
            Theme.SNOW to look(0xFF9BD8FF, 0xFF7CF5C0, 0xFFC9B8FF, 0xFFFFD2C8, 0xFFF2F8FF, 0xFF101A2A, 0xFFD8CCB4),     // ice blue, aurora green, lilac; birch
            Theme.NIGHT to look(0xFFA9C4FF, 0xFFD8F56A, 0xFF8F7BFF, 0xFFB9C8FF, 0xFFDDE6FF, 0xFF070A1E, 0xFF4A3426),    // moon silver, firefly lime, violet; walnut
            Theme.VOLCANO to look(0xFFFF7A2E, 0xFFFF3D2E, 0xFFFFD23F, 0xFFFF8A50, 0xFFFFD8B0, 0xFF1E0605, 0xFF2E2420),  // lava orange, ember red, sulfur yellow; charred
            Theme.STARS to look(0xFF7DA6FF, 0xFFE07BFF, 0xFF6FF0FF, 0xFFC8B0FF, 0xFFF0EEFF, 0xFF0C0620, 0xFF2A2030),    // star blue, nebula magenta, cosmic cyan; ebony
        )

        /** [argb] with its hue turned by [degrees], keeping its saturation and brightness. */
        private fun turn(argb: Long, degrees: Float): Long {
            val r = ((argb shr 16) and 0xFF) / 255f; val g = ((argb shr 8) and 0xFF) / 255f; val b = (argb and 0xFF) / 255f
            val mx = maxOf(r, g, b); val mn = minOf(r, g, b); val d = mx - mn
            val h = when { d < 1e-6f -> 0f; mx == r -> 60f * (((g - b) / d) % 6f); mx == g -> 60f * ((b - r) / d + 2f); else -> 60f * ((r - g) / d + 4f) }
            return hsv(h + degrees, if (mx < 1e-6f) 0f else d / mx, mx)
        }

        private fun hsv(hue: Float, s: Float, v: Float): Long {
            val h = ((hue % 360f) + 360f) % 360f / 60f
            val i = h.toInt(); val f = h - i
            val p = v * (1 - s); val q = v * (1 - s * f); val t = v * (1 - s * (1 - f))
            val (r, g, b) = when (i) { 0 -> Triple(v, t, p); 1 -> Triple(q, v, p); 2 -> Triple(p, v, t); 3 -> Triple(p, q, v); 4 -> Triple(t, p, v); else -> Triple(v, p, q) }
            return 0xFF000000L or ((r * 255).toLong() shl 16) or ((g * 255).toLong() shl 8) or (b * 255).toLong()
        }
    }
}
