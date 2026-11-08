package com.example.mviapp.core.ui.display

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class EInkDeviceDetectorTest {

    @Test
    fun `detects onyx boox color eink tablet`() {
        assertTrue(
            EInkDeviceDetector.isLikelyEInk(
                "ONYX", "TabUltraC", "onyx", "boox", "TabUltraC", "mtk",
            ),
        )
    }

    @Test
    fun `detects other mainstream eink vendors`() {
        listOf(
            "iReader Color 7", "Hisense A9", "Bigme B751C", "BOOX NovaAirC",
            "Supernote A5X", "PocketBook InkPad", "Kobo Libra Colour",
            "墨案 MIX7", "Dasung Paperlike", "Hanvon N10",
        ).forEach { model ->
            assertTrue("应识别为墨水屏: $model", EInkDeviceDetector.isLikelyEInk("brand", model))
        }
    }

    @Test
    fun `detects color eink technology keywords`() {
        assertTrue(EInkDeviceDetector.isLikelyEInk(null, "Kaleido 3 Panel"))
        assertTrue(EInkDeviceDetector.isLikelyEInk("generic", "eInk_Color"))
        assertTrue(EInkDeviceDetector.isLikelyEInk("vendor", "ebook_reader"))
    }

    @Test
    fun `does not flag ordinary phone`() {
        assertFalse(
            EInkDeviceDetector.isLikelyEInk(
                "Xiaomi", "M2011K2C", "Xiaomi", "venus", "venus", "qcom",
            ),
        )
        assertFalse(EInkDeviceDetector.isLikelyEInk("samsung", "SM-S9180"))
    }

    @Test
    fun `empty tokens are not eink`() {
        assertFalse(EInkDeviceDetector.isLikelyEInk())
        assertFalse(EInkDeviceDetector.isLikelyEInk(null, "", null))
    }

    @Test
    fun `description reflects detection result`() {
        assertTrue(EInkDeviceDetector.description(true).contains("墨水屏"))
        assertFalse(EInkDeviceDetector.description(false).contains("已识别"))
    }
}
