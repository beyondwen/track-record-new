package com.wenhao.record.ui.map

import com.mapbox.maps.plugin.gestures.OnMoveListener
import kotlin.test.Test
import kotlin.test.assertEquals

class MoveGestureListenerBindingTest {

    @Test
    fun `bind does not register duplicate listener when callback instance changes`() {
        val host = FakeMoveGestureListenerHost()
        val binding = MoveGestureListenerBinding()

        binding.bind(host, interactive = true) {}
        binding.bind(host, interactive = true) {}

        assertEquals(1, host.addedCount)
        assertEquals(0, host.removedCount)
        assertEquals(1, host.activeListeners.size)
    }

    @Test
    fun `bind removes listener when interaction is disabled`() {
        val host = FakeMoveGestureListenerHost()
        val binding = MoveGestureListenerBinding()

        binding.bind(host, interactive = true) {}
        binding.bind(host, interactive = false) {}

        assertEquals(1, host.addedCount)
        assertEquals(1, host.removedCount)
        assertEquals(0, host.activeListeners.size)
    }

    @Test
    fun `clear removes registered listener`() {
        val host = FakeMoveGestureListenerHost()
        val binding = MoveGestureListenerBinding()

        binding.bind(host, interactive = true) {}
        binding.clear()

        assertEquals(1, host.addedCount)
        assertEquals(1, host.removedCount)
        assertEquals(0, host.activeListeners.size)
    }

    private class FakeMoveGestureListenerHost : MoveGestureListenerHost {
        val activeListeners = linkedSetOf<OnMoveListener>()
        var addedCount = 0
            private set
        var removedCount = 0
            private set

        override fun addOnMoveListener(listener: OnMoveListener) {
            activeListeners += listener
            addedCount += 1
        }

        override fun removeOnMoveListener(listener: OnMoveListener) {
            if (activeListeners.remove(listener)) {
                removedCount += 1
            }
        }
    }
}
