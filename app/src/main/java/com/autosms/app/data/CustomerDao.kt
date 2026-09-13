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

    // ---- مدیریتِ دستیِ مخاطبین ----

    /** افزودن/جایگزینیِ یک مخاطب (برای افزودنِ دستی). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(customer: Customer)

    /** یک مخاطب بر اساسِ شماره (یا null اگر نبود). */
    @Query("SELECT * FROM customers WHERE phoneNumber = :phoneNumber")
    suspend fun getByNumber(phoneNumber: String): Customer?

    /** تغییرِ نامِ یک مخاطب بدونِ دست‌زدن به تاریخِ ارسالش. */
    @Query("UPDATE customers SET name = :name WHERE phoneNumber = :phoneNumber")
    suspend fun updateName(phoneNumber: String, name: String)

    /** حذفِ یک مخاطب. */
    @Query("DELETE FROM customers WHERE phoneNumber = :phoneNumber")
    suspend fun deleteByNumber(phoneNumber: String)

    /** فهرستِ مخاطبین به‌ترتیبِ نام (برای صفحهٔ مدیریت). */
    @Query("SELECT * FROM customers ORDER BY name LIMIT :limit")
    suspend fun listContacts(limit: Int): List<Customer>

    /** جست‌وجوی مخاطب بر اساسِ نام یا شماره. */
    @Query("SELECT * FROM customers WHERE name LIKE :query OR phoneNumber LIKE :query ORDER BY name LIMIT :limit")
    suspend fun searchContacts(query: String, limit: Int): List<Customer>

    // ---- پشتیبان‌گیری / بازیابی ----

    /** همهٔ مخاطبین (برای خروجیِ پشتیبان). */
    @Query("SELECT * FROM customers")
    suspend fun getAll(): List<Customer>

    /** درجِ گروهیِ مخاطبین با جایگزینی (برای بازیابیِ پشتیبان). */
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(customers: List<Customer>)

    /** پاک‌کردنِ کاملِ جدولِ مخاطبین (پیش از بازیابی). */
    @Query("DELETE FROM customers")
    suspend fun deleteAll()

    @Query("SELECT COUNT(*) FROM customers")
    suspend fun count(): Int

    @Query("SELECT COUNT(*) FROM customers WHERE lastSentAt IS NULL")
    suspend fun countNeverSent(): Int

    /** تعداد کلِ افرادی که تا حالا پیام گرفته‌اند. */
    @Query("SELECT COUNT(*) FROM customers WHERE lastSentAt IS NOT NULL")
    suspend fun countSent(): Int

    /** تعداد ارسال‌ها از یک زمان به بعد (برای «امروز» و «این ماه»). */
    @Query("SELECT COUNT(*) FROM customers WHERE lastSentAt >= :since")
    suspend fun countSince(since: Long): Int

    /** جدیدترین ارسال‌ها برای نمایشِ تاریخچه (حداکثر ۳۰۰ مورد تا صفحه سبک بماند). */
    @Query("SELECT * FROM customers WHERE lastSentAt IS NOT NULL ORDER BY lastSentAt DESC LIMIT 300")
    suspend fun getRecentSent(): List<Customer>

    /** پاک‌کردنِ تاریخِ ارسالِ همه (شروعِ دوباره‌ی چرخه). */
    @Query("UPDATE customers SET lastSentAt = NULL")
    suspend fun clearAllSent()
}
