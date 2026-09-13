package com.autosms.app.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

/**
 * تایپوگرافیِ تنظیم‌شده برای فارسی.
 *
 * فونت، فونتِ پیش‌فرضِ سیستم است (روی سامسونگ، فونتِ فارسیِ One UI که تمیز و خواناست).
 * نکته‌های مهم برای زیباییِ متنِ فارسی:
 *  - «فاصلهٔ حروف» (letterSpacing) صفر باشد؛ خطِ فارسی نباید کشیده شود.
 *  - «فاصلهٔ خطوط» (lineHeight) کمی سخاوتمندانه باشد تا متن نفس بکشد و خوانا شود.
 *  - عنوان‌ها درشت‌تر و پُروزن‌تر تا سلسله‌مراتبِ بصری واضح باشد.
 */
private val Fa = FontFamily.Default

val AppTypography = Typography(
    headlineLarge = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Bold,
        fontSize = 30.sp, lineHeight = 40.sp, letterSpacing = 0.sp
    ),
    headlineMedium = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Bold,
        fontSize = 26.sp, lineHeight = 36.sp, letterSpacing = 0.sp
    ),
    titleLarge = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Bold,
        fontSize = 21.sp, lineHeight = 30.sp, letterSpacing = 0.sp
    ),
    titleMedium = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.SemiBold,
        fontSize = 18.sp, lineHeight = 27.sp, letterSpacing = 0.sp
    ),
    titleSmall = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 22.sp, letterSpacing = 0.sp
    ),
    bodyLarge = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Normal,
        fontSize = 16.sp, lineHeight = 27.sp, letterSpacing = 0.sp
    ),
    bodyMedium = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Normal,
        fontSize = 15.sp, lineHeight = 25.sp, letterSpacing = 0.sp
    ),
    bodySmall = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Normal,
        fontSize = 13.sp, lineHeight = 22.sp, letterSpacing = 0.sp
    ),
    labelLarge = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.SemiBold,
        fontSize = 15.sp, lineHeight = 20.sp, letterSpacing = 0.sp
    ),
    labelMedium = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Medium,
        fontSize = 13.sp, lineHeight = 18.sp, letterSpacing = 0.sp
    ),
    labelSmall = TextStyle(
        fontFamily = Fa, fontWeight = FontWeight.Medium,
        fontSize = 12.sp, lineHeight = 16.sp, letterSpacing = 0.sp
    )
)
