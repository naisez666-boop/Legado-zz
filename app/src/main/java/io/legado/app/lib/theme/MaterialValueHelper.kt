@file:Suppress("unused")

package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.graphics.drawable.GradientDrawable
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import androidx.fragment.app.Fragment
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.dpToPx

/**
 * @author Karim Abou Zeid (kabouzeid)
 */
// 修改文件路径：app/src/main/java/io/legado/app/lib/theme/MaterialValueHelper.kt
// 改动说明：玻璃拟态主题配色全部改为实时解析颜色资源（values/values-night 自动跟随深浅色），
// 不再读取 ThemeStore 持久化缓存，彻底解决亮暗切换后颜色错乱、文字看不清的问题

@ColorInt
fun Context.getPrimaryTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_primary_text)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_primary_text)
    }
}

@ColorInt
fun Context.getSecondaryTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_secondary)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_primary_text)
    }
}

@ColorInt
fun Context.getPrimaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    }
}

@ColorInt
fun Context.getSecondaryDisabledTextColor(dark: Boolean): Int {
    return if (dark) {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_light
        )
    } else {
        ContextCompat.getColor(
            this,
            androidx.appcompat.R.color.secondary_text_disabled_material_dark
        )
    }
}

val Context.primaryColor: Int
    get() = ContextCompat.getColor(this, R.color.primary)

val Context.primaryColorDark: Int
    get() = ContextCompat.getColor(this, R.color.primaryDark)

val Context.accentColor: Int
    get() = ContextCompat.getColor(this, R.color.accent)

val Context.backgroundColor: Int
    get() = ContextCompat.getColor(this, R.color.background)

val Context.bottomBackground: Int
    get() = ContextCompat.getColor(this, R.color.background_card)

val Context.primaryTextColor: Int
    get() = getPrimaryTextColor(isDarkTheme)

val Context.transparentNavBar: Boolean
    get() = ThemeStore.transparentNavBar(this)

val Context.secondaryTextColor: Int
    get() = getSecondaryTextColor(isDarkTheme)

val Context.primaryDisabledTextColor: Int
    get() = getPrimaryDisabledTextColor(isDarkTheme)

val Context.secondaryDisabledTextColor: Int
    get() = getSecondaryDisabledTextColor(isDarkTheme)

val Fragment.primaryColor: Int
    get() = requireContext().primaryColor

val Fragment.primaryColorDark: Int
    get() = requireContext().primaryColorDark

val Fragment.accentColor: Int
    get() = requireContext().accentColor

val Fragment.backgroundColor: Int
    get() = requireContext().backgroundColor

val Fragment.bottomBackground: Int
    get() = requireContext().bottomBackground

val Fragment.primaryTextColor: Int
    get() = requireContext().getPrimaryTextColor(isDarkTheme)

val Fragment.secondaryTextColor: Int
    get() = requireContext().getSecondaryTextColor(isDarkTheme)

val Fragment.primaryDisabledTextColor: Int
    get() = requireContext().getPrimaryDisabledTextColor(isDarkTheme)

val Fragment.secondaryDisabledTextColor: Int
    get() = requireContext().getSecondaryDisabledTextColor(isDarkTheme)

val Context.buttonDisabledColor: Int
    get() = if (isDarkTheme) {
        ContextCompat.getColor(this, R.color.md_dark_disabled)
    } else {
        ContextCompat.getColor(this, R.color.md_light_disabled)
    }

// 修改说明：深浅色判断改用系统 uiMode，不再通过颜色亮度推断（半透明色会导致误判）
val Context.isDarkTheme: Boolean
    get() = (resources.configuration.uiMode and android.content.res.Configuration.UI_MODE_NIGHT_MASK) ==
        android.content.res.Configuration.UI_MODE_NIGHT_YES

val Fragment.isDarkTheme: Boolean
    get() = requireContext().isDarkTheme

val Context.elevation: Float
    @SuppressLint("PrivateResource")
    get() {
        return if (AppConfig.elevation < 0) {
            ThemeUtils.resolveFloat(
                this,
                android.R.attr.elevation,
                resources.getDimension(com.google.android.material.R.dimen.design_appbar_elevation)
            )
        } else {
            AppConfig.elevation.toFloat().dpToPx()
        }
    }

val Context.filletBackground: GradientDrawable
    get() {
        val background = GradientDrawable()
        background.cornerRadius = 3f.dpToPx()
        background.setColor(backgroundColor)
        return background
    }