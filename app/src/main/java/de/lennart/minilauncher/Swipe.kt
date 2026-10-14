package de.lennart.minilauncher

import kotlin.math.abs

/** Gestures that navigate between the launcher's pages. */
enum class Swipe { Up, Left, Right }

/**
 * The swipe described by a total finger movement of ([dx], [dy]) pixels, or null if it is too
 * short or too diagonal. Up opens the app list; right and left move between the two home pages.
 */
fun classifySwipe(dx: Float, dy: Float, distance: Float): Swipe? = when {
    abs(dy) > abs(dx) && dy < -distance -> Swipe.Up
    abs(dx) > abs(dy) && dx > distance -> Swipe.Right
    abs(dx) > abs(dy) && dx < -distance -> Swipe.Left
    else -> null
}
