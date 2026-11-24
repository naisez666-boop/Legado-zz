package io.legado.app.lib.theme

import android.annotation.SuppressLint
import android.content.Context
import android.content.SharedPreferences
import android.graphics.Color
import androidx.annotation.AttrRes
import androidx.annotation.CheckResult
import androidx.annotation.ColorInt
import androidx.annotation.ColorRes
import androidx.core.content.ContextCompat
import io.legado.app.R
import io.legado.app.help.config.AppConfig
import io.legado.app.utils.ColorUtils
import splitties.init.appCtx
import androidx.core.graphics.toColorInt
import androidx.core.content.edit

/**
 * @author Aidan Follestad (afollestad), Karim Abou Zeid (kabouzeid)
 */
@Suppress("unused")
class ThemeStore @SuppressLint("CommitPrefEdits")
private constructor(private val mContext: Context) : ThemeStoreInterface {

    private val mEditor = prefs(mContext).edit()

    override fun primaryColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_COLOR, color)
        if (autoGeneratePrimaryDark(mContext))
            primaryColorDark(ColorUtils.darkenColor(color))
        return this
    }

    override fun primaryColorRes(@ColorRes colorRes: Int): ThemeStore {
        return primaryColor(ContextCompat.getColor(mContext, colorRes))
    }

    override fun primaryColorAttr(@AttrRes colorAttr: Int): ThemeStore {
        return primaryColor(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun primaryColorDark(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_PRIMARY_COLOR_DARK, color)
        return this
    }

    override fun primaryColorDarkRes(@ColorRes colorRes: Int): ThemeStore {
        return primaryColorDark(ContextCompat.getColor(mContext, colorRes))
    }

    override fun primaryColorDarkAttr(@AttrRes colorAttr: Int): ThemeStore {
        return primaryColorDark(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun accentColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_ACCENT_COLOR, color)
        return this
    }

    override fun accentColorRes(@ColorRes colorRes: Int): ThemeStore {
        return accentColor(ContextCompat.getColor(mContext, colorRes))
    }

    override fun accentColorAttr(@AttrRes colorAttr: Int): ThemeStore {
        return accentColor(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun statusBarColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_STATUS_BAR_COLOR, color)
        return this
    }

    override fun statusBarColorRes(@ColorRes colorRes: Int): ThemeStore {
        return statusBarColor(ContextCompat.getColor(mContext, colorRes))
    }

    override fun statusBarColorAttr(@AttrRes colorAttr: Int): ThemeStore {
        return statusBarColor(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun navigationBarColor(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_NAVIGATION_BAR_COLOR, color)
        return this
    }

    override fun navigationBarColorRes(@ColorRes colorRes: Int): ThemeStore {
        return navigationBarColor(ContextCompat.getColor(mContext, colorRes))
    }

    override fun navigationBarColorAttr(@AttrRes colorAttr: Int): ThemeStore {
        return navigationBarColor(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun textColorPrimary(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_TEXT_COLOR_PRIMARY, color)
        return this
    }

    override fun textColorPrimaryRes(@ColorRes colorRes: Int): ThemeStore {
        return textColorPrimary(ContextCompat.getColor(mContext, colorRes))
    }

    override fun textColorPrimaryAttr(@AttrRes colorAttr: Int): ThemeStore {
        return textColorPrimary(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun textColorPrimaryInverse(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_TEXT_COLOR_PRIMARY_INVERSE, color)
        return this
    }

    override fun textColorPrimaryInverseRes(@ColorRes colorRes: Int): ThemeStore {
        return textColorPrimaryInverse(ContextCompat.getColor(mContext, colorRes))
    }

    override fun textColorPrimaryInverseAttr(@AttrRes colorAttr: Int): ThemeStore {
        return textColorPrimaryInverse(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun textColorSecondary(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_TEXT_COLOR_SECONDARY, color)
        return this
    }

    override fun textColorSecondaryRes(@ColorRes colorRes: Int): ThemeStore {
        return textColorSecondary(ContextCompat.getColor(mContext, colorRes))
    }

    override fun textColorSecondaryAttr(@AttrRes colorAttr: Int): ThemeStore {
        return textColorSecondary(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun textColorSecondaryInverse(@ColorInt color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_TEXT_COLOR_SECONDARY_INVERSE, color)
        return this
    }

    override fun textColorSecondaryInverseRes(@ColorRes colorRes: Int): ThemeStore {
        return textColorSecondaryInverse(ContextCompat.getColor(mContext, colorRes))
    }

    override fun textColorSecondaryInverseAttr(@AttrRes colorAttr: Int): ThemeStore {
        return textColorSecondaryInverse(ThemeUtils.resolveColor(mContext, colorAttr))
    }

    override fun backgroundColor(color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_BACKGROUND_COLOR, color)
        return this
    }

    override fun bottomBackground(color: Int): ThemeStore {
        mEditor.putInt(ThemeStorePrefKeys.KEY_BOTTOM_BACKGROUND, color)
        return this
    }

    override fun transparentNavBar(transparent: Boolean): ThemeStore {
        mEditor.putBoolean(ThemeStorePrefKeys.KEY_TRANSPARENT_NAV_BAR, transparent)
        return this
    }

    override fun autoGeneratePrimaryDark(autoGenerate: Boolean): ThemeStore {
        mEditor.putBoolean(ThemeStorePrefKeys.KEY_AUTO_GENERATE_PRIMARYDARK, autoGenerate)
        return this
    }

    // Commit method

    override fun apply() {
        mEditor.putLong(ThemeStorePrefKeys.VALUES_CHANGED, System.currentTimeMillis())
            .putBoolean(ThemeStorePrefKeys.IS_CONFIGURED_KEY, true)
            .apply()
        accentColor = accentColor()
    }

    companion object {

        var accentColor = accentColor()

        fun editTheme(context: Context): ThemeStore {
            return ThemeStore(context)
        }

        // Static getters

        @CheckResult
        internal fun prefs(context: Context): SharedPreferences {
            return context.getSharedPreferences(
                ThemeStorePrefKeys.CONFIG_PREFS_KEY_DEFAULT,
                Context.MODE_PRIVATE
            )
        }

        fun markChanged(context: Context) {
            ThemeStore(context).apply()
        }

        @CheckResult
        @ColorInt
        fun primaryColor(context: Context = appCtx): Int {
            // 修改说明：玻璃拟态主题下不再读取 prefs 缓存（恢复备份会把旧配色写回导致错乱），
            // 统一实时解析颜色资源，深浅色自动跟随
            return ContextCompat.getColor(context, R.color.primary)
        }

        @CheckResult
        @ColorInt
        fun primaryColorDark(context: Context): Int {
            // 修改说明：同上，实时解析资源色
            return ContextCompat.getColor(context, R.color.primaryDark)
        }

        @CheckResult
        @ColorInt
        fun accentColor(context: Context = appCtx): Int {
            // 修改说明：实时解析资源色；墨水屏模式下改纯黑，
            // 避免彩色强调色在墨水屏灰阶显示发灰（与 MaterialValueHelper.accentColor 保持一致）
            return if (AppConfig.isEInkMode) {
                if (context.isDarkTheme) Color.WHITE else Color.BLACK
            } else {
                ContextCompat.getColor(context, R.color.accent)
            }
        }

        @CheckResult
        @ColorInt
        fun statusBarColor(context: Context, transparent: Boolean): Int {
            // 修改说明：透明时返回透明色，否则实时解析资源色
            return if (transparent) {
                Color.TRANSPARENT
            } else {
                ContextCompat.getColor(context, R.color.primaryDark)
            }
        }

        @CheckResult
        @ColorInt
        fun navigationBarColor(context: Context): Int {
            // 修改说明：实时解析资源色
            return ContextCompat.getColor(context, R.color.background_card)
        }

        @CheckResult
        @ColorInt
        fun textColorPrimary(context: Context): Int {
            // 修改说明：实时解析，暗色模式浅字、浅色模式深字
            return ContextCompat.getColor(
                context,
                if (context.isDarkTheme) R.color.md_light_primary_text else R.color.md_dark_primary_text
            )
        }

        @CheckResult
        @ColorInt
        fun textColorPrimaryInverse(context: Context): Int {
            return ContextCompat.getColor(
                context,
                if (context.isDarkTheme) R.color.md_dark_primary_text else R.color.md_light_primary_text
            )
        }

        @CheckResult
        @ColorInt
        fun textColorSecondary(context: Context): Int {
            return ContextCompat.getColor(
                context,
                if (context.isDarkTheme) R.color.md_light_secondary else R.color.md_dark_secondary
            )
        }

        @CheckResult
        @ColorInt
        fun textColorSecondaryInverse(context: Context): Int {
            return ContextCompat.getColor(
                context,
                if (context.isDarkTheme) R.color.md_dark_secondary else R.color.md_light_secondary
            )
        }

        @CheckResult
        @ColorInt
        fun backgroundColor(context: Context = appCtx): Int {
            // 修改说明：实时解析资源色
            return ContextCompat.getColor(context, R.color.background)
        }

        @CheckResult
        @ColorInt
        fun bottomBackground(context: Context): Int {
            // 修改说明：实时解析资源色
            return ContextCompat.getColor(context, R.color.background_card)
        }

        @CheckResult
        fun coloredStatusBar(context: Context): Boolean {
            return prefs(context).getBoolean(
                ThemeStorePrefKeys.KEY_APPLY_PRIMARYDARK_STATUSBAR,
                true
            )
        }

        @CheckResult
        fun transparentNavBar(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.KEY_TRANSPARENT_NAV_BAR, false)
        }

        @CheckResult
        fun coloredNavigationBar(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.KEY_APPLY_PRIMARY_NAVBAR, false)
        }

        @CheckResult
        fun autoGeneratePrimaryDark(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.KEY_AUTO_GENERATE_PRIMARYDARK, true)
        }

        @CheckResult
        fun isConfigured(context: Context): Boolean {
            return prefs(context).getBoolean(ThemeStorePrefKeys.IS_CONFIGURED_KEY, false)
        }

        @SuppressLint("CommitPrefEdits")
        fun isConfigured(context: Context, version: Int): Boolean {
            val prefs = prefs(context)
            val lastVersion = prefs.getInt(ThemeStorePrefKeys.IS_CONFIGURED_VERSION_KEY, -1)
            if (version > lastVersion) {
                prefs.edit { putInt(ThemeStorePrefKeys.IS_CONFIGURED_VERSION_KEY, version) }
                return false
            }
            return true
        }
    }
}