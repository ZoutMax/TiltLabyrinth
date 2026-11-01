package com.megapro.labyrinth.game

interface GameSounds {
    /** Continuous rolling noise; [level] 0 = silent, 1 = full speed. */
    fun roll(level: Float)
    /** A knock: [strength] by how hard; [size] the wall's length in cells, or 0 for a knock that is not on a wall. */
    fun knock(strength: Float, size: Float = 0f)
    fun finish()
    fun fall()
    /** Continuous sound of the active traps of one kind; [level] 0 = silent (none nearby), 1 = the ball is on one. */
    /** [pan] places a sound across the board, -1 left to 1 right. */
    fun trap(kind: TrapKind, level: Float, pan: Float = 0f)
    /** A trap lid snapping open or shut, [strength] by how near the ball is. */
    fun lid(strength: Float, pan: Float = 0f)
    /** A trap switching on, [strength] by how near the ball is. */
    fun activate(kind: TrapKind, strength: Float, pan: Float = 0f)
    /** The ball takes a speed charge. */
    fun boost()
    /** A trapdoor slamming open, [strength] by how near the ball is. */
    fun slam(strength: Float, pan: Float = 0f)
    /** The ball blowing up when a board's time runs out. */
    fun explode()
    /** A firework bursting in the celebration, [strength] by its size, [pan] where it bursts. */
    fun firework(strength: Float, pan: Float) {}
    /** The ball vaporised by a laser beam. */
    fun disintegrate()
    /** The seconds reel stepping: a very light mechanical tick. */
    fun reelTick()

    object None : GameSounds {
        override fun roll(level: Float) = Unit
        override fun knock(strength: Float, size: Float) = Unit
        override fun finish() = Unit
        override fun fall() = Unit
        override fun trap(kind: TrapKind, level: Float, pan: Float) = Unit
        override fun lid(strength: Float, pan: Float) = Unit
        override fun activate(kind: TrapKind, strength: Float, pan: Float) = Unit
        override fun boost() = Unit
        override fun slam(strength: Float, pan: Float) = Unit
        override fun explode() = Unit
        override fun disintegrate() = Unit
        override fun reelTick() = Unit
    }

    companion object {
        /**
         * The playback rate of the knock: a short stub ticks high and bright, a long wall answers
         * deeper and rings longer, the frame deepest; a soft touch is a shade higher than a hard hit.
         */
        fun knockRate(strength: Float, size: Float): Float {
            val s = strength.coerceIn(0f, 1f)
            if (size <= 0f) return 0.9f + 0.3f * (1 - s) // not a wall (a magnet's catch, the hammer's kick): as it always was
            return (1.15f * Math.pow(size.coerceAtLeast(1f).toDouble(), -0.17).toFloat() * (0.95f + 0.15f * (1f - s))).coerceIn(0.5f, 2f)
        }
    }
}
