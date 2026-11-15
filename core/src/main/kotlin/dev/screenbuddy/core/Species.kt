package dev.screenbuddy.core

/**
 * The companions the app knows about, each with the name it comes with (people can rename it). Only the cat has sprites and behaviour so far; the others
 * are listed so the app can show them as coming soon. A new species needs its own sprite set
 * and, where it behaves differently, its own brain.
 */
enum class Species(val id: String, val label: String, val defaultName: String, val available: Boolean) {
    CAT("cat", "Cat", "Billu", true),
    DOG("dog", "Dog", "Biscuit", false),
    BUNNY("bunny", "Bunny", "Clover", false);

    companion object {
        fun byId(id: String?): Species = entries.firstOrNull { it.id == id && it.available } ?: CAT
    }
}
