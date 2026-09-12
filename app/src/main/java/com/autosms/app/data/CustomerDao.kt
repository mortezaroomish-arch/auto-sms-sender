package com.autosms.app.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query

@Dao
interface CustomerDao {

    /**
     * درج مخاطبین جدید. مخاطبینی که از قبل وجود دارند نادیده گرفته می‌شوند تا
     * تاریخ آخرین ارسالشان از بین نرود.
     */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertNew(customers: List<Customer>): List<Long>

    /**
     * همهٔ مخاطبین به‌ترتیب قدیمی‌ترین ارسال (افرادی که هنوز پیام نگرفته‌اند اول).
     * فیلترِ پیش‌شماره و لیستِ استثنا در کد روی همین لیست اعمال می‌شود.
     */
    @Query("SELECT * FROM customers ORDER BY lastSentAt ASC")
    suspend fun getAllDue(): List<Customer>

    @Query("UPDATE customers SET lastSentAt = :timestamp WHERE phoneNumber = :phoneNumber")
    suspend fun markSent(phoneNumber: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM customers WHERE lastSentAt IS NULL")
    suspend fun countNeverSent(): Int

    /** افرادی که از زمانِ داده‌شده به بعد پیام گرفته‌اند (جدیدترین اول). */
    @Query("SELECT * FROM customers WHERE lastSentAt >= :since ORDER BY lastSentAt DESC")
    suspend fun getSentSince(since: Long): List<Customer>

    /** جدیدترین ارسال‌ها برای نمایشِ تاریخچه (حداکثر ۳۰۰ مورد تا صفحه سبک بماند). */
    @Query("SELECT * FROM customers WHERE lastSentAt IS NOT NULL ORDER BY lastSentAt DESC LIMIT 300")
    suspend fun getRecentSent(): List<Customer>

    /** پاک‌کردنِ تاریخِ ارسالِ همه (شروعِ دوباره‌ی چرخه). */
    @Query("UPDATE customers SET lastSentAt = NULL")
    suspend fun clearAllSent()
}
