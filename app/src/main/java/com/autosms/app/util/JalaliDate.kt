package com.autosms.app.util

import java.util.Calendar

/**
 * تبدیل تاریخ میلادی به شمسی (جلالی) و قالب‌بندی با ارقام فارسی.
 * فقط برای نمایش استفاده می‌شود.
 */
object JalaliDate {

    /** خروجی: «۱۴۰۳/۰۶/۲۱  ۲۰:۳۵» */
    fun format(timestamp: Long): String {
        val c = Calendar.getInstance().apply { timeInMillis = timestamp }
        val (jy, jm, jd) = gregorianToJalali(
            c.get(Calendar.YEAR),
            c.get(Calendar.MONTH) + 1,
            c.get(Calendar.DAY_OF_MONTH)
        )
        val hour = c.get(Calendar.HOUR_OF_DAY)
        val minute = c.get(Calendar.MINUTE)
        val text = "%04d/%02d/%02d  %02d:%02d".format(jy, jm, jd, hour, minute)
        return toPersianDigits(text)
    }

    private fun gregorianToJalali(gy: Int, gm: Int, gd: Int): Triple<Int, Int, Int> {
        val gDaysInMonth = intArrayOf(0, 31, 59, 90, 120, 151, 181, 212, 243, 273, 304, 334)
        val gy2 = gy - 1600
        val gm2 = gm - 1
        val gd2 = gd - 1

        var gDayNo = 365 * gy2 + (gy2 + 3) / 4 - (gy2 + 99) / 100 + (gy2 + 399) / 400
        gDayNo += gDaysInMonth[gm2]
        if (gm2 > 1 && ((gy % 4 == 0 && gy % 100 != 0) || (gy % 400 == 0))) gDayNo++
        gDayNo += gd2

        var jDayNo = gDayNo - 79
        val jNp = jDayNo / 12053
        jDayNo %= 12053

        var jy = 979 + 33 * jNp + 4 * (jDayNo / 1461)
        jDayNo %= 1461

        if (jDayNo >= 366) {
            jy += (jDayNo - 1) / 365
            jDayNo = (jDayNo - 1) % 365
        }

        val jDaysInMonth = intArrayOf(31, 31, 31, 31, 31, 31, 30, 30, 30, 30, 30, 29)
        var i = 0
        while (i < 11 && jDayNo >= jDaysInMonth[i]) {
            jDayNo -= jDaysInMonth[i]
            i++
        }
        val jm = i + 1
        val jd = jDayNo + 1
        return Triple(jy, jm, jd)
    }

    private fun toPersianDigits(input: String): String {
        val persian = charArrayOf('۰', '۱', '۲', '۳', '۴', '۵', '۶', '۷', '۸', '۹')
        val sb = StringBuilder()
        for (ch in input) {
            if (ch in '0'..'9') sb.append(persian[ch - '0']) else sb.append(ch)
        }
        return sb.toString()
    }
}
