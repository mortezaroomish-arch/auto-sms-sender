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
     * انتخاب افرادی که بیشترین مدت از آخرین پیامشان گذشته است.
     * در SQLite مقدار NULL کوچک‌تر از همه در نظر گرفته می‌شود، پس افرادی که هنوز
     * پیامی نگرفته‌اند (lastSentAt = null) در ابتدای صف قرار می‌گیرند.
     */
    @Query("SELECT * FROM customers ORDER BY lastSentAt ASC LIMIT :limit")
    suspend fun getDueCustomers(limit: Int): List<Customer>

    @Query("UPDATE customers SET lastSentAt = :timestamp WHERE phoneNumber = :phoneNumber")
    suspend fun markSent(phoneNumber: String, timestamp: Long)

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM customers WHERE lastSentAt IS NULL")
    suspend fun countNeverSent(): Int

    @Query("SELECT MIN(lastSentAt) FROM customers WHERE lastSentAt IS NOT NULL")
    suspend fun oldestSentAt(): Long?

    /** افرادی که از زمانِ داده‌شده به بعد پیام گرفته‌اند (جدیدترین ارسال اول). */
    @Query("SELECT * FROM customers WHERE lastSentAt >= :since ORDER BY lastSentAt DESC")
    suspend fun getSentSince(since: Long): List<Customer>
}
