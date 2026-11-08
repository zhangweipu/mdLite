package com.example.mviapp.core.ui.display

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MdReaderDisplayTest {

    @Test
    fun `normal mode keeps all original visual behaviour`() {
        val p = MdReaderDisplay.resolve(eInkMode = false, textScale = 1.36f)
        assertFalse(p.highContrast)
        assertEquals(1f, p.textScale, 0f)
        assertEquals(1f, p.headingScale, 0f)
        // 普通模式不能因为抽屉里残留的字号设置而变形
        assertEquals(400, p.bodyFontWeight)
        assertTrue(p.zebraStripes)
        assertTrue(p.shadowsEnabled)
        assertTrue(p.flingEnabled)
        assertTrue(p.overscrollEnabled)
        assertFalse(p.inlineCodeInverted)
        assertFalse(p.outlineHighlight)
    }

    @Test
    fun `eink mode uses pure black and white structure instead of grey shades`() {
        val p = MdReaderDisplay.resolve(eInkMode = true)
        assertTrue(p.highContrast)
        assertFalse(p.zebraStripes)
        assertFalse(p.shadowsEnabled)
        assertTrue(p.inlineCodeInverted)
        assertTrue(p.italicAsBold)
        assertTrue(p.outlineHighlight)
        assertEquals(0f, p.cornerRadiusDp, 0f)
        assertEquals(1.5f, p.borderWidthDp, 0f)
        assertEquals(500, p.bodyFontWeight)
    }

    @Test
    fun `eink mode disables every animation that leaves ghosting`() {
        val p = MdReaderDisplay.resolve(eInkMode = true)
        assertFalse(p.progressBarBlink)
        assertFalse(p.infiniteLoadingAnimation)
        assertFalse(p.overscrollEnabled)
        assertFalse(p.flingEnabled)
    }

    @Test
    fun `text scale is applied and clamped`() {
        assertEquals(1.18f, MdReaderDisplay.resolve(true, 1.18f).textScale, 0.0001f)
        assertEquals(0.8f, MdReaderDisplay.resolve(true, 0.1f).textScale, 0.0001f)
        assertEquals(1.6f, MdReaderDisplay.resolve(true, 9f).textScale, 0.0001f)
    }

    @Test
    fun `headings scale more gently than body text`() {
        val p = MdReaderDisplay.resolve(true, 1.5f)
        assertTrue(p.headingScale > 1f)
        assertTrue(p.headingScale < p.textScale)
        assertEquals(1.3f, p.headingScale, 0.0001f)
    }

    @Test
    fun `font sizes grow monotonically with the selected scale`() {
        val small = MdReaderDisplay.resolve(true, 0.9f)
        val large = MdReaderDisplay.resolve(true, 1.36f)
        assertTrue(
            MdReaderDisplay.bodySp(large.textScale) > MdReaderDisplay.bodySp(small.textScale),
        )
        assertTrue(
            MdReaderDisplay.bodyLineSp(large.textScale) > MdReaderDisplay.bodyLineSp(small.textScale),
        )
        assertTrue(
            MdReaderDisplay.smallSp(large.textScale) > MdReaderDisplay.smallSp(small.textScale),
        )
        // 行高始终大于字号，避免放大后行与行挤在一起
        assertTrue(MdReaderDisplay.bodyLineSp(1f) > MdReaderDisplay.bodySp(1f))
        assertTrue(MdReaderDisplay.smallLineSp(1f) > MdReaderDisplay.smallSp(1f))
        assertEquals(18f, MdReaderDisplay.bodySp(1f), 0f)
        assertEquals(15f, MdReaderDisplay.smallSp(1f), 0f)
    }
}
