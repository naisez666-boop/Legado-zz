package io.legado.app.base

import android.annotation.SuppressLint
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.Bundle
import android.util.AttributeSet
import android.view.Menu
import android.view.MenuItem
import android.view.MotionEvent
import android.view.View
import android.widget.FrameLayout
import androidx.activity.addCallback
import androidx.annotation.RequiresApi
import androidx.appcompat.app.AppCompatActivity
import androidx.viewbinding.ViewBinding
import io.legado.app.R
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.Theme
import io.legado.app.help.config.AppConfig
import io.legado.app.help.config.ThemeConfig
import io.legado.app.lib.theme.ThemeStore
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.bottomBackground
import io.legado.app.lib.theme.isDarkTheme
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryColorDark
import io.legado.app.ui.widget.TitleBar
import io.legado.app.utils.ColorUtils
import io.legado.app.utils.applyBackgroundTint
import io.legado.app.utils.applyOpenTint
import io.legado.app.utils.applyTint
import io.legado.app.utils.disableAutoFill
import io.legado.app.utils.fullScreen
import io.legado.app.utils.hideSoftInput
import io.legado.app.utils.setLightStatusBar
import io.legado.app.utils.setNavigationBarColorAuto
import io.legado.app.utils.setStatusBarColorAuto
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.windowSize

abstract class BaseActivity<VB : ViewBinding>(
    val fullScreen: Boolean = true,
    private val theme: Theme = Theme.Auto,
    private val toolBarTheme: Theme = Theme.Auto,
    private val transparent: Boolean = false,
    private val imageBg: Boolean = true,
    private val showOpenMenuIcon: Boolean = true
) : AppCompatActivity() {

    protected abstract val binding: VB

    val isInMultiWindow: Boolean
        @SuppressLint("ObsoleteSdkInt")
        get() {
            return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
                isInMultiWindowMode
            } else {
                false
            }
        }

    override fun attachBaseContext(newBase: Context) {
        super.attachBaseContext(AppContextWrapper.wrap(newBase))
    }

    override fun onCreateView(
        parent: View?,
        name: String,
        context: Context,
        attrs: AttributeSet
    ): View? {
        if (AppConst.menuViewNames.contains(name) && parent?.parent is FrameLayout) {
            (parent.parent as View).setBackgroundColor(backgroundColor)
        }
        return super.onCreateView(parent, name, context, attrs)
    }

    @SuppressLint("ObsoleteSdkInt")
    override fun onCreate(savedInstanceState: Bundle?) {
        window.decorView.disableAutoFill()
        initTheme()
        super.onCreate(savedInstanceState)
        setupSystemBar()
        setContentView(binding.root)
        upBackgroundImage()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            findViewById<TitleBar>(R.id.title_bar)
                ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        }
        onBackPressedDispatcher.addCallback(this) {
            finish()
        }
        observeLiveBus()
        onActivityCreated(savedInstanceState)
    }

    @RequiresApi(Build.VERSION_CODES.O)
    override fun onMultiWindowModeChanged(isInMultiWindowMode: Boolean, newConfig: Configuration) {
        super.onMultiWindowModeChanged(isInMultiWindowMode, newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindowMode, fullScreen)
        setupSystemBar()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        findViewById<TitleBar>(R.id.title_bar)
            ?.onMultiWindowModeChanged(isInMultiWindow, fullScreen)
        setupSystemBar()
    }

    abstract fun onActivityCreated(savedInstanceState: Bundle?)

    final override fun onCreateOptionsMenu(menu: Menu): Boolean {
        val bool = onCompatCreateOptionsMenu(menu)
        menu.applyTint(this, toolBarTheme)
        return bool
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        menu.applyOpenTint(this, showOpenMenuIcon)
        return super.onMenuOpened(featureId, menu)
    }

    open fun onCompatCreateOptionsMenu(menu: Menu) = super.onCreateOptionsMenu(menu)

    final override fun onOptionsItemSelected(item: MenuItem): Boolean {
        if (item.itemId == android.R.id.home) {
            supportFinishAfterTransition()
            return true
        }
        return onCompatOptionsItemSelected(item)
    }

    open fun onCompatOptionsItemSelected(item: MenuItem) = super.onOptionsItemSelected(item)

    open fun initTheme() {
        when (theme) {
            Theme.Transparent -> setTheme(R.style.AppTheme_Transparent)
            Theme.Dark -> {
                setTheme(R.style.AppTheme_Dark)
               window.decorView.applyBackgroundTint(backgroundColor)
            }

            Theme.Light -> {
                setTheme(R.style.AppTheme_Light)
               window.decorView.applyBackgroundTint(backgroundColor)
            }

            else -> {
                // 修改文件路径：BaseActivity.kt
                // 改动说明：深浅风格改用 uiMode 判断（与资源解析同源，永不错位），
                // 不再用半透明主色的亮度推断
                if (isDarkTheme) {
                    setTheme(R.style.AppTheme_Dark)
                } else {
                    setTheme(R.style.AppTheme_Light)
                }
               window.decorView.applyBackgroundTint(backgroundColor)
            }
        }
    }

    open fun upBackgroundImage() {
        if (imageBg) {
            try {
                ThemeConfig.getBgImage(this, windowManager.windowSize)?.let { drawable ->
                   window.decorView.background = drawable
                }
            } catch (_: OutOfMemoryError) {
                toastOnUi("背景图片太大,内存溢出")
            } catch (e: Exception) {
                AppLog.put("加载背景出错\n${e.localizedMessage}", e)
            }
        }
    }

    open fun setupSystemBar() {
        if (fullScreen && !isInMultiWindow) {
            fullScreen()
        }
        // 修改文件路径：BaseActivity.kt
        // 改动说明：状态栏颜色改用实时解析的玻璃配色，不再读 ThemeStore 残留缓存
        val isTransparentStatusBar = AppConfig.isTransparentStatusBar
        val statusBarColor = if (isTransparentStatusBar) {
            primaryColor
        } else {
            primaryColorDark
        }
        setStatusBarColorAuto(statusBarColor, isTransparentStatusBar, fullScreen)
        if (toolBarTheme == Theme.Dark) {
            setLightStatusBar(false)
        } else if (toolBarTheme == Theme.Light) {
            setLightStatusBar(true)
        }
        upNavigationBarColor()
    }

    open fun upNavigationBarColor() {
        // 修改文件路径：BaseActivity.kt
        // 改动说明：导航栏颜色改用实时解析的玻璃卡片底色，不再读 ThemeStore 残留缓存
        if (AppConfig.immNavigationBar) {
            setNavigationBarColorAuto(bottomBackground)
        } else {
            val nbColor = ColorUtils.darkenColor(bottomBackground)
            setNavigationBarColorAuto(nbColor)
        }
    }

    open fun observeLiveBus() {
    }

    override fun dispatchTouchEvent(ev: MotionEvent): Boolean {
        return try {
            super.dispatchTouchEvent(ev)
        } catch (e: IllegalArgumentException) {
            e.printStackTrace()
            false
        }
    }

    override fun finish() {
        currentFocus?.hideSoftInput()
        super.finish()
    }
}