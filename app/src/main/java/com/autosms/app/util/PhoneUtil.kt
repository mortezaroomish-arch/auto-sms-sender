package com.autosms.app.util

/** کمک‌تابع‌های مشترک برای نرمال‌سازی شماره تلفن. */
object PhoneUtil {

    /** فقط ارقام (اعداد فارسی/عربی به انگلیسی) و علامتِ + ابتدایی. */
    fun normalizeDigits(raw: String): String {
        val sb = StringBuilder()
        for (c in raw.trim()) {
            when {
                c == '+' && sb.isEmpty() -> sb.append('+')
                Character.isDigit(c) -> sb.append(Character.digit(c, 10))
            }
        }
        return sb.toString()
    }

    /** شماره را به شکلِ محلیِ «0…» درمی‌آورد تا مقایسه‌ها درست باشد. */
    fun toLocal(raw: String): String {
        var s = normalizeDigits(raw)
        s = when {
            s.startsWith("+98") -> "0" + s.substring(3)
            s.startsWith("0098") -> "0" + s.substring(4)
            s.startsWith("98") && s.length == 12 -> "0" + s.substring(2)
            else -> s
        }
        return s
    }
}
