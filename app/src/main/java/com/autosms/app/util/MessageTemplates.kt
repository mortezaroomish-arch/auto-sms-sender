package com.autosms.app.util

/**
 * پشتیبانی از «چند متنِ آماده». کاربر می‌تواند چند متنِ متفاوت بنویسد و آن‌ها را با
 * یک خطِ جداکننده که فقط «---» (سه خط‌تیره یا بیشتر) دارد از هم جدا کند.
 * اولین متن، «متنِ پیش‌فرض» است.
 */
object MessageTemplates {

    /** خطِ جداکننده: خطی که فقط از سه خط‌تیره (یا بیشتر) و فاصله تشکیل شده باشد. */
    private val SEPARATOR = Regex("(?m)^[\\s\\u200c]*-{3,}[\\s\\u200c]*$")

    /** متن را به فهرستِ متن‌های جدا (بدونِ موردِ خالی) تبدیل می‌کند. */
    fun variants(raw: String): List<String> =
        raw.split(SEPARATOR)
            .map { it.trim() }
            .filter { it.isNotEmpty() }
}
