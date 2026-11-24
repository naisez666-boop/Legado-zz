package io.legado.app.help.storage

import io.legado.app.R
import io.legado.app.constant.PreferKey
import io.legado.app.lib.theme.ThemeStorePrefKeys
import io.legado.app.utils.FileUtils
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonObject
import splitties.init.appCtx

/**
 * 备份配置
 */
@Suppress("ConstPropertyName")
object BackupConfig {

    private val ignoreConfigPath = FileUtils.getPath(appCtx.filesDir, "restoreIgnore.json")
    val ignoreConfig: HashMap<String, Boolean> by lazy {
        val file = FileUtils.createFileIfNotExist(ignoreConfigPath)
        val json = file.readText()
        GSON.fromJsonObject<HashMap<String, Boolean>>(json).getOrNull() ?: hashMapOf()
    }

    private const val readConfigKey = "readConfig"
    private const val themeConfigKey = "themeConfig"
    private const val coverConfigKey = "coverConfig"
    private const val localBookKey = "localBook"

    //配置忽略key
    val ignoreKeys = arrayOf(
        readConfigKey,
        PreferKey.themeMode,
        themeConfigKey,
        coverConfigKey,
        PreferKey.bookshelfLayout,
        PreferKey.showRss,
        PreferKey.threadCount,
        localBookKey
    )

    //配置忽略标题
    val ignoreTitle = arrayOf(
        appCtx.getString(R.string.read_config),
        appCtx.getString(R.string.theme_mode),
        appCtx.getString(R.string.theme_config),
        appCtx.getString(R.string.cover_config),
        appCtx.getString(R.string.bookshelf_layout),
        appCtx.getString(R.string.show_rss),
        appCtx.getString(R.string.thread_count),
        appCtx.getString(R.string.local_book)
    )

    //自动忽略keys
    private val ignorePrefKeys = arrayOf(
        PreferKey.defaultCover,
        PreferKey.defaultCoverDark,
        PreferKey.backupPath,
        PreferKey.defaultBookTreeUri,
        PreferKey.webDavDeviceName,
        PreferKey.launcherIcon,
        PreferKey.bitmapCacheSize,
        PreferKey.webServiceWakeLock,
        PreferKey.readAloudWakeLock,
        PreferKey.audioPlayWakeLock
    )

    //阅读配置
    private val readPrefKeys = arrayOf(
        PreferKey.readStyleSelect,
        PreferKey.comicStyleSelect,
        PreferKey.shareLayout,
        PreferKey.hideStatusBar,
        PreferKey.hideNavigationBar,
        PreferKey.autoReadSpeed,
        PreferKey.clickActionTL,
        PreferKey.clickActionTC,
        PreferKey.clickActionTR,
        PreferKey.clickActionML,
        PreferKey.clickActionMC,
        PreferKey.clickActionMR,
        PreferKey.clickActionBL,
        PreferKey.clickActionBC,
        PreferKey.clickActionBR
    )

    private val themePrefKeys = arrayOf(
        PreferKey.cPrimary,
        PreferKey.cAccent,
        PreferKey.cBackground,
        PreferKey.cBBackground,
        PreferKey.bgImage,
        PreferKey.bgImageBlurring,
        PreferKey.tNavBar,
        PreferKey.cNPrimary,
        PreferKey.cNAccent,
        PreferKey.cNBackground,
        PreferKey.cNBBackground,
        PreferKey.bgImageN,
        PreferKey.bgImageNBlurring,
        PreferKey.tNavBarN
    )

    // 修改文件路径：BackupConfig.kt
    // 改动说明：旧版主题配色键（ThemeStore 持久化配色缓存），恢复备份时无条件忽略，
    // 配合 ThemeStore 读取实时化，彻底断掉旧配色经备份复活的链路
    private val legacyThemeColorKeys = arrayOf(
        ThemeStorePrefKeys.KEY_PRIMARY_COLOR,
        ThemeStorePrefKeys.KEY_PRIMARY_COLOR_DARK,
        ThemeStorePrefKeys.KEY_ACCENT_COLOR,
        ThemeStorePrefKeys.KEY_STATUS_BAR_COLOR,
        ThemeStorePrefKeys.KEY_NAVIGATION_BAR_COLOR,
        ThemeStorePrefKeys.KEY_TEXT_COLOR_PRIMARY,
        ThemeStorePrefKeys.KEY_TEXT_COLOR_PRIMARY_INVERSE,
        ThemeStorePrefKeys.KEY_TEXT_COLOR_SECONDARY,
        ThemeStorePrefKeys.KEY_TEXT_COLOR_SECONDARY_INVERSE,
        ThemeStorePrefKeys.KEY_BACKGROUND_COLOR,
        ThemeStorePrefKeys.KEY_BOTTOM_BACKGROUND,
        ThemeStorePrefKeys.KEY_APPLY_PRIMARYDARK_STATUSBAR,
        ThemeStorePrefKeys.KEY_APPLY_PRIMARY_NAVBAR,
        ThemeStorePrefKeys.KEY_AUTO_GENERATE_PRIMARYDARK,
        ThemeStorePrefKeys.IS_CONFIGURED_KEY,
        ThemeStorePrefKeys.IS_CONFIGURED_VERSION_KEY,
        ThemeStorePrefKeys.VALUES_CHANGED
    )

    private val coverPrefKeys = arrayOf(
        PreferKey.useDefaultCover,
        PreferKey.loadCoverOnlyWifi,
        PreferKey.coverShowName,
        PreferKey.coverShowAuthor,
        PreferKey.coverShowNameN,
        PreferKey.coverShowAuthorN
    )

    fun keyIsNotIgnore(key: String): Boolean {
        return when {
            // 修改文件路径：BackupConfig.kt
            // 改动说明：旧版主题配色键无条件忽略——玻璃拟态主题下配色固定，
            // 恢复备份时不再导入备份文件里的旧配色（旧版棕/蓝灰等），
            // 防止恢复备份后主题错乱复现；无论是否勾选「忽略主题配置」都生效
            legacyThemeColorKeys.contains(key) -> false
            ignorePrefKeys.contains(key) -> false
            ignoreReadConfig && readPrefKeys.contains(key) -> false
            ignoreThemeConfig && themePrefKeys.contains(key) -> false
            ignoreCoverConfig && coverPrefKeys.contains(key) -> false
            PreferKey.themeMode == key && ignoreThemeMode -> false
            PreferKey.bookshelfLayout == key && ignoreBookshelfLayout -> false
            PreferKey.showRss == key && ignoreShowRss -> false
            PreferKey.threadCount == key && ignoreThreadCount -> false
            else -> true
        }
    }

    val ignoreReadConfig: Boolean
        get() = ignoreConfig[readConfigKey] == true
    private val ignoreThemeMode: Boolean
        get() = ignoreConfig[PreferKey.themeMode] == true
    private val ignoreThemeConfig: Boolean
        get() = ignoreConfig[themeConfigKey] == true
    private val ignoreCoverConfig: Boolean
        get() = ignoreConfig[coverConfigKey] == true
    private val ignoreBookshelfLayout: Boolean
        get() = ignoreConfig[PreferKey.bookshelfLayout] == true
    private val ignoreShowRss: Boolean
        get() = ignoreConfig[PreferKey.showRss] == true
    private val ignoreThreadCount: Boolean
        get() = ignoreConfig[PreferKey.threadCount] == true
    val ignoreLocalBook: Boolean
        get() = ignoreConfig[localBookKey] == true

    fun saveIgnoreConfig() {
        val json = GSON.toJson(ignoreConfig)
        FileUtils.createFileIfNotExist(ignoreConfigPath).writeText(json)
    }

}