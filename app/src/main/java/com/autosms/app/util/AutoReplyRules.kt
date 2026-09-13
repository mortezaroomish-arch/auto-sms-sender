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
     * جوابِ مناسب برای یک پیامِ ورودی را برمی‌گرداند:
     * ۱) اولین قانونی که کلیدواژه‌اش در متن باشد،
     * ۲) وگرنه جوابِ پیش‌فرض،
     * ۳) اگر هیچ‌کدام نبود، null (یعنی چیزی فرستاده نشود).
     */
    fun findReply(body: String, rulesRaw: String, default: String): String? {
        val text = body.trim()
        if (text.isNotEmpty()) {
            for ((keyword, reply) in parse(rulesRaw)) {
                if (text.contains(keyword, ignoreCase = true)) return reply
            }
        }
        val fallback = default.trim()
        return if (fallback.isEmpty()) null else fallback
    }
}
