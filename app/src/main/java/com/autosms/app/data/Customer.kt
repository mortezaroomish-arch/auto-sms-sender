package com.autosms.app.data

import androidx.room.Entity
import androidx.room.PrimaryKey

/**
 * یک مشتری (مخاطب) که پیامک برایش ارسال می‌شود.
 *
 * کلید اصلی، شمارهٔ تلفن نرمال‌شده است تا از ارسال دوباره به یک شماره جلوگیری شود.
 * فیلد [lastSentAt] تاریخ آخرین ارسال است؛ null یعنی هنوز پیامی نگرفته است.
 * انتخاب افراد برای ارسال روزانه بر اساس همین فیلد انجام می‌شود (قدیمی‌ترها اول).
 */
@Entity(tableName = "customers")
data class Customer(
    @PrimaryKey
    val phoneNumber: String,
    val name: String,
    val lastSentAt: Long? = null,
    val addedAt: Long = System.currentTimeMillis()
)
