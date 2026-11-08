package com.example.mviapp.core.ui.display

/**
 * 电子墨水屏（E-Ink / EPD）设备识别，纯 Kotlin，便于 JVM 单测。
 *
 * 只用来给「自动模式」一个默认值，不做强判断：识别不出来时用户仍可在
 * 抽屉里手动打开「电子墨水屏模式」，识别错误也能手动关掉。
 *
 * 判断依据分两类：
 * 1. 厂商/品牌关键字：文石 BOOX/ONYX、掌阅 iReader、海信、墨案、大上、
 *    Supernote、Bigme、汉王、讯飞办公本、Kobo、PocketBook、tolino 等。
 * 2. 通用型号关键字：eink / epd / ereader / ebook 等。
 *
 * 彩色水墨屏（Kaleido / Gallery / 彩墨）同样命中，例如文石 Tab Ultra C、
 * Nova Air C、Bigme 彩墨系列、掌阅 Color 系列。是否把图片转灰度由用户单独
 * 选择，识别结果只决定「自动模式」是否开启高对比排版。
 *
 * 刻意不把 `xiaomi`、`lenovo` 这类同时生产手机与阅读器的品牌单独列为关键字，
 * 避免把普通手机误判成墨水屏（只有型号里带 eink/ebook 等字样才命中）。
 */
object EInkDeviceDetector {

    private val VENDOR_KEYWORDS = listOf(
        // 国产 / 国际主流墨水屏阅读器
        "onyx", "boox", "ireader", "zhangyue", "hisense",
        "meebook", "likebook", "boyue", "bigme", "hanvon", "hanwang",
        "iflytek", "dasung", "supernote", "ratta", "mobiscribe",
        "pocketbook", "tolino", "kobo", "nook", "remarkable", "inkbook",
        // 彩墨屏 / 彩色电子纸技术名
        "kaleido", "gallery3", "acep", "colorink", "coloreink", "cpaper",
        "文石", "掌阅", "墨案", "汉王", "大我",
    )

    private val GENERIC_KEYWORDS = listOf(
        "eink", "e-ink", "e_ink", "e ink", "epd",
        "ereader", "e-reader", "ebook", "e-book", "inkscreen",
        "electronic paper", "electrophoretic",
    )

    /**
     * [tokens] 通常是 Build.BRAND / MODEL / MANUFACTURER / DEVICE / PRODUCT / HARDWARE。
     * 任一命中即认为是墨水屏设备；全部为空时返回 false（宁可漏判，不可误判）。
     */
    fun isLikelyEInk(vararg tokens: String?): Boolean {
        val joined = tokens.filterNotNull().joinToString(" ").lowercase()
        if (joined.isBlank()) return false
        return VENDOR_KEYWORDS.any { joined.contains(it) } ||
            GENERIC_KEYWORDS.any { joined.contains(it) }
    }

    /** 便于 UI 展示的一句说明 */
    fun description(isEInk: Boolean): String =
        if (isEInk) "已识别为墨水屏设备" else "本机为普通屏幕"
}
