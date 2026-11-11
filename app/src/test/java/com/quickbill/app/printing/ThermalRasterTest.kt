package com.quickbill.app.printing

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ThermalRasterTest {
    @Test
    fun `packs black dots most significant bit first in each row`() {
        val pixels = IntArray(16 * 2) { WHITE }.apply {
            this[0] = BLACK
            this[7] = BLACK
            this[8] = BLACK
            this[16 + 15] = BLACK
        }

        assertArrayEquals(
            byteArrayOf(0x1d, 0x76, 0x30, 0, 2, 0, 2, 0, 0x81.toByte(), 0x80.toByte(), 0, 1),
            encodeEscPosRaster(16, 2, pixels),
        )
    }

    @Test
    fun `encodes a 384-dot strip and uses only the requested rows`() {
        val pixels = IntArray(384 * 64) { BLACK }
        pixels.fill(WHITE, fromIndex = 0, toIndex = 384 * 3)

        val raster = encodeEscPosRaster(384, 3, pixels)

        assertArrayEquals(byteArrayOf(0x1d, 0x76, 0x30, 0, 48, 0, 3, 0), raster.copyOfRange(0, 8))
        assertEquals(8 + 48 * 3, raster.size)
        assertArrayEquals(ByteArray(48 * 3), raster.copyOfRange(8, raster.size))
    }

    private companion object {
        const val BLACK = 0xff000000.toInt()
        const val WHITE = 0xffffffff.toInt()
    }
}
