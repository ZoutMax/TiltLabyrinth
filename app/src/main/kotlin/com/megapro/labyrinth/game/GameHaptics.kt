package com.megapro.labyrinth.game

/** What the player should feel. The game reports events and continuous levels; the host turns them into vibration. */
interface GameHaptics {
    /** The ball hits a wall; [strength] 0..1 by impact speed. */
    fun knock(strength: Float)
    /** Called every frame while playing: rolling texture, [level] 0 = still, 1 = full speed. */
    fun roll(level: Float)
    /** Called every frame while playing: how hard wind is pushing the ball, 0..1. */
    fun wind(level: Float)
    /** Called every frame while playing: 1 while the ball slides on ice. */
    fun slide(level: Float)
    /** Called every frame while playing: how strongly a spinner's current has the ball, 0..1. */
    fun spin(level: Float)
    /** The magnet grabs the ball. */
    fun magnetCatch()
    /** Called every frame while playing: true while the magnet holds the ball. */
    fun magnetHold(held: Boolean)
    /** The magnet lets go. */
    fun magnetRelease()
    /** The ball gets coated in slime. */
    fun toxic()
    /** The ball takes a speed charge. */
    fun boost()
    /** A lid clacks or a trap switches on near the ball; [strength] by nearness. */
    fun tick(strength: Float)
    /** The ball drops into a trap: run over. */
    fun fall()
    /** The ball is cut by a laser beam. */
    fun zap()
    /** The ball blows up when a board's time runs out. */
    fun explode()
    /** The ball drops into the finish hole. */
    fun finish()
    /** Everything continuous stops (pause, menu). */
    fun stop()

    object None : GameHaptics {
        override fun knock(strength: Float) = Unit
        override fun roll(level: Float) = Unit
        override fun wind(level: Float) = Unit
        override fun slide(level: Float) = Unit
        override fun spin(level: Float) = Unit
        override fun magnetCatch() = Unit
        override fun magnetHold(held: Boolean) = Unit
        override fun magnetRelease() = Unit
        override fun toxic() = Unit
        override fun boost() = Unit
        override fun tick(strength: Float) = Unit
        override fun fall() = Unit
        override fun zap() = Unit
        override fun explode() = Unit
        override fun finish() = Unit
        override fun stop() = Unit
    }
}
