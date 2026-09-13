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

    /**
     * آیا این فرستنده یک «شماره‌ی موبایلِ شخصیِ واقعی» است؟
     *
     * برای «پاسخِ خودکار» فقط باید به آدم‌های واقعی جواب بدهیم، نه به:
     *  - نام‌های حرفی (Sender ID) که اپراتورها/بانک‌ها می‌فرستند (مثلِ BANK، Iancell) → دارای حرف‌اند.
     *  - کدهای کوتاهِ خدماتی/تبلیغاتی/رمزِ یک‌بارمصرف (مثلِ 2000، 10001) → خیلی کوتاه‌اند.
     *
     * پس فقط شماره‌ای که «حرف ندارد» و «حداقل ۱۰ رقم» دارد را شخصی می‌شماریم.
     */
    fun isReplyableSender(raw: String?): Boolean {
        if (raw == null) return false
        // اگر حرفِ الفبا داشته باشد، یک Sender IDِ اپراتوری است، نه شماره‌ی شخصی.
        if (raw.trim().any { it.isLetter() }) return false
        val digits = normalizeDigits(raw).filter { it.isDigit() }
        // شماره‌های موبایلِ ایران ۱۱ رقم‌اند؛ کدهای خدماتی معمولاً کوتاه‌ترند.
        return digits.length >= 10
    }
}
