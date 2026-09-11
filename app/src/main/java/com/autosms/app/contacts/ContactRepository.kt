package com.autosms.app.contacts

import android.content.Context
import android.provider.ContactsContract
import com.autosms.app.data.Customer

/**
 * خواندن مخاطبین گوشی و همگام‌سازی آن‌ها با پایگاه‌داده.
 * مخاطبین جدید اضافه می‌شوند و مخاطبین قبلی (همراه با تاریخ ارسالشان) دست‌نخورده می‌مانند.
 */
class ContactRepository(private val context: Context) {

    /** همهٔ شماره‌های موجود در مخاطبین گوشی را برمی‌گرداند (بدون تکرار). */
    fun readAllContacts(): List<Customer> {
        val result = LinkedHashMap<String, Customer>()
        val projection = arrayOf(
            ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME,
            ContactsContract.CommonDataKinds.Phone.NUMBER
        )

        context.contentResolver.query(
            ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
            projection,
            null,
            null,
            null
        )?.use { cursor ->
            val nameIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME)
            val numberIdx = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER)
            while (cursor.moveToNext()) {
                val rawNumber = if (numberIdx >= 0) cursor.getString(numberIdx) else null
                val name = if (nameIdx >= 0) cursor.getString(nameIdx) else null
                val normalized = normalize(rawNumber) ?: continue
                if (!result.containsKey(normalized)) {
                    result[normalized] = Customer(
                        phoneNumber = normalized,
                        name = name ?: normalized
                    )
                }
            }
        }
        return result.values.toList()
    }

    /** شمارهٔ تلفن را نرمال می‌کند: فقط ارقام و در صورت وجود، + ابتدایی. */
    private fun normalize(raw: String?): String? {
        if (raw.isNullOrBlank()) return null
        val trimmed = raw.trim()
        val hasPlus = trimmed.startsWith("+")
        val digits = trimmed.filter { it.isDigit() }
        if (digits.length < 5) return null
        return if (hasPlus) "+$digits" else digits
    }
}
