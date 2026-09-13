package com.autosms.app.util

/**
 * منطقِ «پاسخِ خودکار» (حالتِ ترکیبی): چند قانونِ کلیدواژه‌ای + یک جوابِ پیش‌فرض.
 *
 * هر قانون یک خط است به شکلِ «کلیدواژه = جواب». اگر کلیدواژه در متنِ پیامِ دریافتی
 * دیده شود، همان جواب انتخاب می‌شود. اگر هیچ قانونی مطابقت نکند، جوابِ پیش‌فرض
 * (در صورتِ خالی‌نبودن) فرستاده می‌شود.
 */
object AutoReplyRules {

    /**
     * متنِ قانون‌ها را به فهرستی از جفت‌های (کلیدواژه، جواب) تبدیل می‌کند.
     * خطوطِ خالی و خطوطی که «=» ندارند نادیده گرفته می‌شوند.
     * برای جدا کردن از اولین «=» استفاده می‌شود تا جوابْ خودش می‌تواند «=» داشته باشد.
     */
    fun parse(rulesRaw: String): List<Pair<String, String>> {
        if (rulesRaw.isBlank()) return emptyList()
        val result = mutableListOf<Pair<String, String>>()
        for (line in rulesRaw.lines()) {
            val trimmed = line.trim()
            if (trimmed.isEmpty()) continue
            val idx = trimmed.indexOf('=')
            if (idx <= 0) continue
            val keyword = trimmed.substring(0, idx).trim()
            val reply = trimmed.substring(idx + 1).trim()
            if (keyword.isNotEmpty() && reply.isNotEmpty()) {
                result.add(keyword to reply)
            }
        }
        return result
    }

    /**
     * متن را برای «مقایسه‌ی منعطف» ساده می‌کند تا تفاوت‌های رایجِ نوشتاری مانع تشخیص نشوند:
     *  - «آ/أ/إ/ٱ» → «ا» (با کلاه و بی‌کلاه یکی می‌شوند)
     *  - «ي/ئ» (عربی) → «ی» (فارسی)
     *  - «ك» (عربی) → «ک» (فارسی)
     *  - «ة» → «ه» ، «ؤ» → «و» ، «ۀ» → «ه»
     *  - حذفِ اعرابِ عربی، کشیده (ـ) و نیم‌فاصله/علائمِ جهت
     *  - کوچک‌کردنِ حروفِ لاتین
     * این‌طور «آدرس» و «ادرس» هر دو یکی حساب می‌شوند.
     */
    fun normalizeForMatch(input: String): String {
        val sb = StringBuilder(input.length)
        for (ch in input) {
            // اعرابِ عربی و علامتِ بالانویسِ الف را حذف کن
            if (ch in '\u064B'..'\u0652' || ch == '\u0670') continue
            when (ch) {
                'ـ', '\u200C', '\u200D', '\u200E', '\u200F' -> {} // کشیده، نیم‌فاصله، علائمِ جهت → حذف
                'آ', 'أ', 'إ', 'ٱ', 'ا' -> sb.append('ا')
                'ي', 'ئ', 'ی' -> sb.append('ی')
                'ك' -> sb.append('ک')
                'ة' -> sb.append('ه')
                'ۀ' -> sb.append('ه')
                'ؤ' -> sb.append('و')
                else -> sb.append(ch)
            }
        }
        return sb.toString().lowercase().trim()
    }

    /**
     * جوابِ مناسب برای یک پیامِ ورودی را برمی‌گرداند:
     * ۱) اولین قانونی که کلیدواژه‌اش (با مقایسه‌ی منعطف) در متن باشد،
     * ۲) وگرنه جوابِ پیش‌فرض،
     * ۳) اگر هیچ‌کدام نبود، null (یعنی چیزی فرستاده نشود).
     */
    fun findReply(body: String, rulesRaw: String, default: String): String? {
        val normalizedBody = normalizeForMatch(body)
        if (normalizedBody.isNotEmpty()) {
            for ((keyword, reply) in parse(rulesRaw)) {
                val normalizedKeyword = normalizeForMatch(keyword)
                if (normalizedKeyword.isNotEmpty() && normalizedBody.contains(normalizedKeyword)) {
                    return reply
                }
            }
        }
        val fallback = default.trim()
        return if (fallback.isEmpty()) null else fallback
    }

    /**
     * آیا این متن، خودش یکی از «جواب‌های خودکارِ ما» است؟
     *
     * وقتی به شماره‌ی خودمان تست می‌کنیم (یا طرفِ مقابل هم پاسخِ خودکار دارد)، جوابِ خودمان
     * دوباره به‌عنوان پیامِ ورودی برمی‌گردد و یک «حلقه‌ی بی‌پایان» می‌سازد. اگر متنِ دریافتی
     * دقیقاً برابرِ جوابِ پیش‌فرض یا یکی از جواب‌های قانون‌ها باشد، آن را جوابِ خودمان می‌شماریم
     * و دیگر جواب نمی‌دهیم.
     */
    fun isOwnReply(body: String, rulesRaw: String, default: String): Boolean {
        val text = body.trim()
        if (text.isEmpty()) return false
        if (default.trim().isNotEmpty() && text == default.trim()) return true
        for ((_, reply) in parse(rulesRaw)) {
            if (text == reply.trim()) return true
        }
        return false
    }
}
