package dev.screenbuddy.core

/** Things a companion does by itself, each of which the user can switch off. */
enum class Behavior {
    /** Walk to another spot along the floor. */
    WANDER,

    /** Lick a paw, then wash the face with it. */
    GROOM,

    /** A long, slow stretch. */
    STRETCH,

    YAWN,

    /** Scratch behind an ear with a hind leg. */
    SCRATCH,

    /** Curl up and sleep after a while without attention. */
    NAP,

    /** Every hour or two a ball of wool rolls in; she plays for a minute or two, then bats it away. */
    BALL,

    /** Perk up now and then when the screen is touched. */
    NOTICE,

    /** Jump onto the on-screen keyboard while it is open. Handled by the app, which owns the floor. */
    KEYBOARD;

    companion object {
        /** What she picks from while sitting around; each comes up once before any repeats. */
        val ACTIVITIES = listOf(WANDER, GROOM, STRETCH, YAWN, SCRATCH)
    }
}
