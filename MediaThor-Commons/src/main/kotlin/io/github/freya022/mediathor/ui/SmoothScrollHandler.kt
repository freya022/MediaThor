package io.github.freya022.mediathor.ui

import javafx.animation.Interpolator
import javafx.animation.Transition
import javafx.event.EventHandler
import javafx.scene.control.ScrollPane
import javafx.scene.input.ScrollEvent
import javafx.scene.layout.Region
import javafx.util.Duration
import kotlin.math.sign

private const val TRANSITION_DURATION = 200.0
private const val BASE_MODIFIER = 3.0

//Adapted from https://gist.github.com/Col-E/7d31b6b8684669cf1997831454681b85
class SmoothScrollHandler(private val scrollPane: ScrollPane) : EventHandler<ScrollEvent> {
    private var currentTransition: SmoothTransition? = null

    private abstract class SmoothTransition(old: SmoothTransition?, private val delta: Double) : Transition() {
        protected var mod = 0.0

        init {
            interpolator = Interpolator.LINEAR
            cycleDuration = Duration.millis(TRANSITION_DURATION)
            cycleCount = 0
            // if the last transition was moving in the same direction, and is still playing
            // then increment the modifier. This will boost the distance, thus looking faster
            // and seemingly consecutive.
            mod = when {
                old != null && sameSign(delta, old.delta) && old.isRunning() -> old.mod + 1
                else -> 1.0
            }
        }

        override fun play() {
            super.play()
            // Even with a linear interpolation, startup is visibly slower than the middle.
            // So skip a small bit of the animation to keep up with the speed of prior
            // animation. The value of 10 works and isn't noticeable unless you really pay
            // close attention. This works best on linear but also is decent for others.
            if (mod > 1) {
                jumpTo(cycleDuration.divide(10.0))
            }
        }

        private fun Transition.isRunning() = status == Status.RUNNING

        private fun sameSign(d1: Double, d2: Double) = d1.sign == d2.sign
    }

    override fun handle(e: ScrollEvent) {
        if (e.target != scrollPane) return

        e.consume()

        val content = scrollPane.content as Region

        val contentHeight = content.boundsInLocal.height
        val verticalValue = scrollPane.vvalue
        val deltaY = BASE_MODIFIER * e.deltaY

        val contentWidth = content.boundsInLocal.width
        val horizontalValue = scrollPane.hvalue
        val deltaX = BASE_MODIFIER * e.deltaX
        currentTransition = (object : SmoothTransition(currentTransition, deltaY) {
            override fun interpolate(frac: Double) {
                if (e.deltaX != 0.0) {
                    scrollPane.hvalue = interpolator.interpolate(horizontalValue, horizontalValue + -deltaX * mod / contentWidth, frac)
                } else {
                    scrollPane.vvalue = interpolator.interpolate(verticalValue, verticalValue + -deltaY * mod / contentHeight, frac)
                }
            }
        }).also { it.play() }
    }
}

fun ScrollPane.useSmoothScroll() {
    addEventHandler(ScrollEvent.SCROLL, SmoothScrollHandler(this))
}