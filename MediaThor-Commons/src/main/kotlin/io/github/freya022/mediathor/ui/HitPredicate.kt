package io.github.freya022.mediathor.ui

fun interface HitPredicate {
    fun isTitleBar(x: Double, y: Double): Boolean
}
