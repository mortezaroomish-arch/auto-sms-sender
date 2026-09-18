package com.autosms.app.sms

import android.Manifest
import android.content.Context
import android.content.pm.PackageManager
import android.telephony.SubscriptionInfo
import android.telephony.SubscriptionManager
import android.util.Log
import androidx.core.content.ContextCompat

/**
 * کمک‌تابع‌های مربوط به سیم‌کارت‌ها (برای گوشی‌های دو سیم‌کارته).
 *
 * برای خواندنِ فهرستِ سیم‌ها به مجوزِ READ_PHONE_STATE نیاز است؛ اگر مجوز نباشد یا
 * دستگاه تک‌سیمه باشد، فهرستِ خالی/تک‌عضوی برمی‌گردد و برنامه به حالتِ عادی (سیمِ
 * پیش‌فرض) کار می‌کند.
 */
object SimUtil {

    private const val TAG = "SimUtil"

    /**
     * اطلاعاتِ یک سیم‌کارتِ فعال.
     * @param subscriptionId شناسه‌ی سیستمیِ سیم (برای ارسالِ پیامک از همین سیم استفاده می‌شود).
     * @param slotIndex شماره‌ی جایگاهِ فیزیکیِ سیم (۰ = سیم ۱، ۱ = سیم ۲).
     * @param label نامِ نمایشیِ خوانا برای کاربر (مثلِ «سیم ۱ — همراه اول»).
     */
    data class SimInfo(
        val subscriptionId: Int,
        val slotIndex: Int,
        val label: String
    )

    /** آیا مجوزِ لازم برای خواندنِ اطلاعاتِ سیم داده شده است؟ */
    fun hasPermission(context: Context): Boolean =
        ContextCompat.checkSelfPermission(
            context, Manifest.permission.READ_PHONE_STATE
        ) == PackageManager.PERMISSION_GRANTED

    /**
     * فهرستِ سیم‌کارت‌های فعالِ دستگاه (مرتب‌شده بر اساسِ جایگاه). در صورتِ نبودِ مجوز،
     * دستگاهِ تک‌سیمه یا هر خطا، فهرستِ خالی برمی‌گردد.
     */
    fun activeSims(context: Context): List<SimInfo> {
        if (!hasPermission(context)) return emptyList()
        return try {
            val sm = context.getSystemService(Context.TELEPHONY_SUBSCRIPTION_SERVICE)
                as? SubscriptionManager ?: return emptyList()
            val list: List<SubscriptionInfo> = sm.activeSubscriptionInfoList ?: return emptyList()
            list.map { info ->
                SimInfo(
                    subscriptionId = info.subscriptionId,
                    slotIndex = info.simSlotIndex,
                    label = buildLabel(info)
                )
            }.sortedBy { it.slotIndex }
        } catch (e: SecurityException) {
            Log.e(TAG, "مجوزِ خواندنِ سیم‌ها نیست: ${e.message}")
            emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "خطا در خواندنِ سیم‌ها: ${e.message}", e)
            emptyList()
        }
    }

    /** آیا دستگاه بیش از یک سیم‌کارتِ فعال دارد؟ */
    fun isDualSim(context: Context): Boolean = activeSims(context).size >= 2

    /**
     * شناسه‌ی سیمِ پیش‌فرضِ پیامکِ گوشی (همانی که برنامهٔ پیامکِ خودِ گوشی از آن می‌فرستد).
     * برای عیب‌یابی مفید است: اگر پیام از این سیم رفت یعنی انتخابِ سیمِ ما اثر نکرده.
     * در صورتِ خطا ‑۱ برمی‌گرداند.
     */
    fun defaultSmsSubId(context: Context): Int = try {
        SubscriptionManager.getDefaultSmsSubscriptionId()
    } catch (e: Exception) {
        -1
    }

    /**
     * شناسه‌ی سیمِ انتخاب‌شده را اعتبارسنجی می‌کند: اگر subId ذخیره‌شده هنوز فعال باشد
     * همان برمی‌گردد، وگرنه سیمِ جایگاهِ داده‌شده (fallbackSlot) به‌عنوانِ پیش‌فرض.
     * اگر هیچ‌کدام نبود، ‑۱ (یعنی سیمِ پیش‌فرضِ سیستم).
     */
    fun resolveSubId(context: Context, savedSubId: Int, fallbackSlot: Int): Int {
        val sims = activeSims(context)
        if (sims.isEmpty()) return -1
        sims.firstOrNull { it.subscriptionId == savedSubId }?.let { return it.subscriptionId }
        sims.firstOrNull { it.slotIndex == fallbackSlot }?.let { return it.subscriptionId }
        return sims.first().subscriptionId
    }

    private fun buildLabel(info: SubscriptionInfo): String {
        val name = info.displayName?.toString()?.takeIf { it.isNotBlank() }
            ?: info.carrierName?.toString()?.takeIf { it.isNotBlank() }
        val slotLabel = "سیم ${info.simSlotIndex + 1}"
        return if (name != null && !name.equals(slotLabel, ignoreCase = true)) {
            "$slotLabel — $name"
        } else {
            slotLabel
        }
    }
}
