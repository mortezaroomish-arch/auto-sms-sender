package com.autosms.app.data

import org.json.JSONArray
import org.json.JSONObject

/**
 * ساخت و خواندنِ فایلِ پشتیبان (JSON) شاملِ مخاطبین، تنظیمات، لیستِ لغو و وضعیتِ چرخه.
 * از org.json (داخلِ خودِ اندروید) استفاده می‌کند تا وابستگیِ اضافه لازم نباشد.
 */
object BackupManager {

    private const val VERSION = 1

    data class Backup(
        val customers: List<Customer>,
        val settings: AppSettings,
        val optedOut: Set<String>,
        val cycleStart: Long,
        val cycleMessageIndex: Int
    )

    /** ساختِ متنِ JSON از داده‌های فعلی. */
    fun toJson(
        customers: List<Customer>,
        settings: AppSettings,
        optedOut: Set<String>,
        cycleStart: Long,
        cycleMessageIndex: Int
    ): String {
        val root = JSONObject()
        root.put("version", VERSION)
        root.put("cycleStart", cycleStart)
        root.put("cycleMessageIndex", cycleMessageIndex)

        val s = JSONObject()
        s.put("enabled", settings.enabled)
        s.put("messageText", settings.messageText)
        s.put("dailyCount", settings.dailyCount)
        s.put("startHour", settings.startHour)
        s.put("endHour", settings.endHour)
        s.put("delaySeconds", settings.delaySeconds)
        s.put("delayMaxSeconds", settings.delayMaxSeconds)
        s.put("personalizeWithName", settings.personalizeWithName)
        s.put("sequentialMessages", settings.sequentialMessages)
        s.put("numberPrefixes", settings.numberPrefixes)
        s.put("excludedNumbers", settings.excludedNumbers)
        s.put("autoOptOut", settings.autoOptOut)
        s.put("optOutKeyword", settings.optOutKeyword)
        s.put("autoReplyEnabled", settings.autoReplyEnabled)
        s.put("autoReplyDefault", settings.autoReplyDefault)
        s.put("autoReplyRules", settings.autoReplyRules)
        s.put("missedCallReplyEnabled", settings.missedCallReplyEnabled)
        s.put("missedCallReplyText", settings.missedCallReplyText)
        root.put("settings", s)

        val arr = JSONArray()
        for (c in customers) {
            val o = JSONObject()
            o.put("phoneNumber", c.phoneNumber)
            o.put("name", c.name)
            o.put("lastSentAt", c.lastSentAt ?: JSONObject.NULL)
            o.put("addedAt", c.addedAt)
            arr.put(o)
        }
        root.put("customers", arr)

        val opted = JSONArray()
        for (n in optedOut) opted.put(n)
        root.put("optedOut", opted)

        return root.toString(2)
    }

    /** خواندنِ متنِ JSON و بازگرداندنِ داده‌ها. در صورتِ نامعتبربودن، استثنا پرتاب می‌شود. */
    fun fromJson(text: String): Backup {
        val root = JSONObject(text)

        val s = root.getJSONObject("settings")
        val settings = AppSettings(
            enabled = s.optBoolean("enabled", false),
            messageText = s.optString("messageText", ""),
            dailyCount = s.optInt("dailyCount", 100),
            startHour = s.optInt("startHour", 20),
            endHour = s.optInt("endHour", 22),
            delaySeconds = s.optInt("delaySeconds", 70),
            delayMaxSeconds = s.optInt("delayMaxSeconds", 0),
            personalizeWithName = s.optBoolean("personalizeWithName", false),
            sequentialMessages = s.optBoolean("sequentialMessages", false),
            numberPrefixes = s.optString("numberPrefixes", ""),
            excludedNumbers = s.optString("excludedNumbers", ""),
            autoOptOut = s.optBoolean("autoOptOut", false),
            optOutKeyword = s.optString("optOutKeyword", "لغو"),
            autoReplyEnabled = s.optBoolean("autoReplyEnabled", false),
            autoReplyDefault = s.optString("autoReplyDefault", ""),
            autoReplyRules = s.optString("autoReplyRules", ""),
            missedCallReplyEnabled = s.optBoolean("missedCallReplyEnabled", false),
            missedCallReplyText = s.optString("missedCallReplyText", "در اسرع وقت با شما تماس می‌گیرم")
        )

        val customers = mutableListOf<Customer>()
        val arr = root.optJSONArray("customers") ?: JSONArray()
        for (i in 0 until arr.length()) {
            val o = arr.getJSONObject(i)
            val phone = o.getString("phoneNumber")
            val last = if (o.isNull("lastSentAt")) null else o.getLong("lastSentAt")
            customers.add(
                Customer(
                    phoneNumber = phone,
                    name = o.optString("name", phone),
                    lastSentAt = last,
                    addedAt = o.optLong("addedAt", System.currentTimeMillis())
                )
            )
        }

        val optedOut = mutableSetOf<String>()
        val oarr = root.optJSONArray("optedOut") ?: JSONArray()
        for (i in 0 until oarr.length()) optedOut.add(oarr.getString(i))

        val cycleStart = root.optLong("cycleStart", 0L)
        val cycleMessageIndex = root.optInt("cycleMessageIndex", 0)

        return Backup(customers, settings, optedOut, cycleStart, cycleMessageIndex)
    }
}
