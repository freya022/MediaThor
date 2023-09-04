package io.github.freya022.mediathor.ui

import com.sun.javafx.tk.TKStage
import com.sun.jna.Library
import com.sun.jna.Native
import com.sun.jna.Pointer
import com.sun.jna.platform.win32.WinDef
import com.sun.jna.win32.StdCallLibrary
import io.github.freya022.mediathor.ui.UndecoratedStage.HitTestProc
import io.github.freya022.mediathor.ui.UndecoratedStage.SizeTestProc
import javafx.application.Platform
import javafx.scene.Node
import javafx.stage.*

//TODO probably would be better to accept a stage rather than extend it,
// as to take advantage of the given Stage in Application
class UndecoratedStage(private val titleBar: Node) : Stage(StageStyle.UNDECORATED) {
    private val hitTestProc: HitTestProc
    private val sizeTestProc: SizeTestProc
    private val hitPredicates: MutableList<HitPredicate> = arrayListOf()

    init {
        hitPredicates += HitPredicate { x: Double, y: Double -> isTitleBar(x, y) }
    }

    private var hasResized = false
    private var oldCoordsSet = false

    private var oldX = 0.0
    private var oldY = 0.0

    init {
        val primary = Screen.getPrimary()

        addEventFilter(WindowEvent.WINDOW_SHOWN) { _ ->
            //borderless styles and handlers need to be reapplied after the window is closed and reopened
            val hwnd = applyBorderless()
            Platform.requestNextPulse()

            if (!this.isResizable) { //Stage content looks compressed, and borders appear under NOT resizable stages, resize the stage to redraw the window
                val width = width
                val height = height
                if (hasResized) {
                    setWidth(width - 1)
                    setHeight(height - 1)
                } else {
                    setWidth(width + 1)
                    setHeight(height + 1)
                }
                hasResized = !hasResized
            }

            if (oldCoordsSet) {
                x = oldX
                y = oldY
            }

            resizableProperty().addListener { _, _, isResizable: Boolean ->
                iBorderLess.setResizable(hwnd, isResizable)
            }

            iBorderLess.setResizable(hwnd, isResizable)
        }

        addEventFilter(WindowEvent.WINDOW_HIDDEN) {
            oldCoordsSet = true
            oldX = x
            oldY = y
        }

        val xScale = primary.outputScaleX
        val yScale = primary.outputScaleY
        val invertedXScale = 1 / xScale
        val invertedYScale = 1 / yScale

        hitTestProc = HitTestProc { x: Int, y: Int ->
            //Winapi's X / Y are scaled, but stage X / Y seems already scaled ??
            //0.8 = 1/1.25
            val realX = x * invertedXScale
            val realY = y * invertedYScale

            hitPredicates.any { it.isTitleBar(realX, realY) }
        }

        sizeTestProc = SizeTestProc { rect: WinDef.RECT ->
            rect.read()

            val width = rect.right - rect.left
            val height = rect.bottom - rect.top
            val minWidth = scene.root.minWidth(-1.0) * xScale
            val minHeight = scene.root.minHeight(-1.0) * yScale

            if (width < minWidth) {
                rect.left = (x * xScale).toInt()
                rect.right = (x * xScale + minWidth).toInt()
            }

            if (height < minHeight) {
                rect.top = (y * yScale).toInt()
                rect.bottom = (y * yScale + minHeight).toInt()
            }

            rect.write()
        }
    }

    //TODO the issue might be that messages are cut off when receiving resizing messages
    // Might be better to not return and let the JFX window procedure run
    private fun isTitleBar(x: Double, y: Double): Boolean {
        return titleBar.boundsInLocal.contains(x - this@UndecoratedStage.x, y - this@UndecoratedStage.y)
    }

    fun addHitTest(hitPredicate: HitPredicate) {
        hitPredicates.add(hitPredicate)
    }

    private fun applyBorderless(): WinDef.HWND {
        val windowHandle = getHwnd()
        iBorderLess.createWindow(windowHandle, hitTestProc, sizeTestProc)
        return windowHandle
    }

    private fun getHwnd(): WinDef.HWND {
        val peer = Window::class.java.getDeclaredField("peer")
        peer.setAccessible(true)
        val tkStage = peer[this] as TKStage
        peer.setAccessible(false)

        val nativeWindowPointer = Pointer(tkStage.rawHandle)
        return WinDef.HWND(nativeWindowPointer)
    }

    private fun interface HitTestProc : StdCallLibrary.StdCallCallback {
        //Used by JNA
        @Suppress("unused")
        fun callback(x: Int, y: Int): Boolean
    }

    private fun interface SizeTestProc : StdCallLibrary.StdCallCallback {
        //Used by JNA
        @Suppress("unused")
        fun callback(rect: WinDef.RECT)
    }

    private interface IBorderLess : Library {
        fun createWindow(windowHandle: WinDef.HWND, hitTestProc: HitTestProc, sizeTestProc: SizeTestProc)
        fun setResizable(hwnd: WinDef.HWND, resizable: Boolean)

        companion object {
            //TODO include sources and a way to get the native moved into resources
            val INSTANCE: IBorderLess = Native.load("BorderlessWindow.dll", IBorderLess::class.java)
        }
    }

    companion object {
        private val iBorderLess = IBorderLess.INSTANCE
    }
}