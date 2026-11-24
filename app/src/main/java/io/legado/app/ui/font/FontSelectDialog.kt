package io.legado.app.ui.font

import android.net.Uri
import android.os.Bundle
import android.view.MenuItem
import android.view.View
import androidx.appcompat.widget.Toolbar
import androidx.documentfile.provider.DocumentFile
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import okhttp3.OkHttpClient
import okhttp3.Request
import java.util.concurrent.TimeUnit
import io.legado.app.base.BaseDialogFragment
import io.legado.app.constant.AppLog
import io.legado.app.constant.PreferKey
import io.legado.app.databinding.DialogFontSelectBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.SelectItem
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.permission.Permissions
import io.legado.app.lib.permission.PermissionsCompat
import io.legado.app.lib.theme.primaryColor
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.utils.FileDoc
import io.legado.app.utils.FileUtils
import io.legado.app.utils.RealPathUtil
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.externalFiles
import io.legado.app.utils.getPrefString
import io.legado.app.utils.isContentScheme
import io.legado.app.utils.list
import io.legado.app.utils.listFileDocs
import io.legado.app.utils.putPrefString
import io.legado.app.utils.setLayout
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch
import java.io.File

/**
 * 字体选择对话框
 */
class FontSelectDialog : BaseDialogFragment(R.layout.dialog_font_select),
    Toolbar.OnMenuItemClickListener,
    FontAdapter.CallBack {
    private val fontRegex = Regex("(?i).*\\.[ot]tf")
    private val binding by viewBinding(DialogFontSelectBinding::bind)
    private val adapter by lazy {
        val curFontPath = callBack?.curFontPath ?: ""
        FontAdapter(requireContext(), curFontPath, this)
    }
    private val selectFontDir = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            if (uri.isContentScheme()) {
                putPrefString(PreferKey.fontFolder, uri.toString())
                val doc = DocumentFile.fromTreeUri(requireContext(), uri)
                if (doc != null) {
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } else {
                    RealPathUtil.getPath(requireContext(), uri)?.let { path ->
                        loadFontFilesByPermission(path)
                    }
                }
            } else {
                uri.path?.let { path ->
                    putPrefString(PreferKey.fontFolder, path)
                    loadFontFilesByPermission(path)
                }
            }
        }
    }

    override fun onStart() {
        super.onStart()
        setLayout(0.9f, 0.9f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.setTitle(R.string.select_font)
        binding.toolBar.inflateMenu(R.menu.font_select)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(context)
        binding.recyclerView.adapter = adapter

        val fontPath = getPrefString(PreferKey.fontFolder)
        if (fontPath.isNullOrEmpty()) {
            openFolder()
        } else {
            if (fontPath.isContentScheme()) {
                val doc = DocumentFile.fromTreeUri(requireContext(), Uri.parse(fontPath))
                if (doc?.canRead() == true) {
                    loadFontFiles(FileDoc.fromDocumentFile(doc))
                } else {
                    openFolder()
                }
            } else {
                loadFontFilesByPermission(fontPath)
            }
        }
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_default -> {
                val requireContext = requireContext()
                alert(titleResource = R.string.system_typeface) {
                    items(
                        requireContext.resources.getStringArray(R.array.system_typefaces).toList()
                    ) { _, i ->
                        AppConfig.systemTypefaces = i
                        onDefaultFontChange()
                        dismissAllowingStateLoss()
                    }
                }
            }

            // 修改文件路径：app/src/main/java/io/legado/app/ui/font/FontSelectDialog.kt
            // 改动说明：新增「推荐字体」——一键下载开源字体（思源黑体/宋体），
            // 下载到应用私有 font 目录，完成后自动出现在字体列表
            R.id.menu_recommend -> showRecommendFonts()

            R.id.menu_other -> {
                openFolder()
            }
        }
        return true
    }

    /**
     * 修改文件路径：FontSelectDialog.kt
     * 改动说明：推荐字体数据类（名称 + 保存文件名 + 下载地址，均为 GitHub 官方仓库直链）
     */
    data class RecommendFont(val name: String, val fileName: String, val url: String)

    private val recommendFonts = listOf(
        RecommendFont(
            "思源黑体 简中 常规",
            "SourceHanSansCN-Regular.otf",
            "https://github.com/adobe-fonts/source-han-sans/raw/release/SubsetOTF/CN/SourceHanSansCN-Regular.otf"
        ),
        RecommendFont(
            "思源黑体 简中 粗体",
            "SourceHanSansCN-Bold.otf",
            "https://github.com/adobe-fonts/source-han-sans/raw/release/SubsetOTF/CN/SourceHanSansCN-Bold.otf"
        ),
        RecommendFont(
            "思源宋体 简中 常规",
            "SourceHanSerifCN-Regular.otf",
            "https://github.com/adobe-fonts/source-han-serif/raw/release/SubsetOTF/CN/SourceHanSerifCN-Regular.otf"
        )
    )

    private fun showRecommendFonts() {
        alert(title = "推荐字体") {
            setMessage("选择字体下载（约6~9MB），完成后自动出现在列表中")
            items(recommendFonts.map { it.name }) { _, i ->
                downloadFont(recommendFonts[i])
            }
            cancelButton()
        }
    }

    private fun downloadFont(font: RecommendFont) {
        toastOnUi("开始下载：${font.name}")
        execute {
            val dir = FileUtils.getPath(requireContext().externalFiles, "font")
            val file = File(dir, font.fileName)
            if (file.exists() && file.length() > 0) {
                return@execute "${font.name} 已存在"
            }
            val client = OkHttpClient.Builder()
                .connectTimeout(20, TimeUnit.SECONDS)
                .readTimeout(60, TimeUnit.SECONDS)
                .build()
            val response = client.newCall(Request.Builder().url(font.url).build()).execute()
            if (!response.isSuccessful) {
                throw Exception("HTTP ${response.code}")
            }
            response.body?.byteStream()?.use { input ->
                file.outputStream().use { output ->
                    input.copyTo(output)
                }
            } ?: throw Exception("下载内容为空")
            "${font.name} 下载完成"
        }.onSuccess {
            toastOnUi(it)
            // 刷新字体列表：合并已下载的本地字体
            val cur = ArrayList(adapter.getItems().filterIsInstance<FileDoc>())
            adapter.setItems(mergeFontItems(getLocalFonts(), cur))
        }.onError {
            AppLog.put("字体下载失败", it)
            toastOnUi("字体下载失败：${it.localizedMessage}")
        }
    }

    private fun openFolder() {
        lifecycleScope.launch {
            val defaultPath = "SD${File.separator}Fonts"
            selectFontDir.launch {
                otherActions = arrayListOf(SelectItem(defaultPath, -1))
            }
        }
    }

    private fun getLocalFonts(): ArrayList<FileDoc> {
        val path = FileUtils.getPath(requireContext().externalFiles, "font")
        return File(path).listFileDocs {
            it.name.matches(fontRegex)
        }
    }

    private fun loadFontFilesByPermission(path: String) {
        PermissionsCompat.Builder()
            .addPermissions(*Permissions.Group.STORAGE)
            .rationale(R.string.tip_perm_request_storage)
            .onGranted {
                loadFontFiles(
                    FileDoc.fromFile(File(path))
                )
            }
            .request()
    }

    private fun loadFontFiles(fileDoc: FileDoc) {
        execute {
            val fontItems = fileDoc.list {
                it.name.matches(fontRegex)
            } ?: ArrayList()
            mergeFontItems(fontItems, getLocalFonts())
        }.onSuccess {
            adapter.setItems(it)
        }.onError {
            AppLog.put("加载字体文件失败\n${it.localizedMessage}", it)
            toastOnUi("getFontFiles:${it.localizedMessage}")
        }
    }

    private fun mergeFontItems(
        items1: ArrayList<FileDoc>,
        items2: ArrayList<FileDoc>
    ): List<FileDoc> {
        val items = ArrayList(items1)
        items2.forEach { item2 ->
            var isInFirst = false
            items1.forEach for1@{ item1 ->
                if (item2.name == item1.name) {
                    isInFirst = true
                    return@for1
                }
            }
            if (!isInFirst) {
                items.add(item2)
            }
        }
        return items.sortedWith { o1, o2 ->
            o1.name.cnCompare(o2.name)
        }
    }

    override fun onFontSelect(docItem: FileDoc) {
        execute {
            callBack?.selectFont(docItem.toString())
        }.onSuccess {
            dismissAllowingStateLoss()
        }
    }

    private fun onDefaultFontChange() {
        callBack?.selectFont("")
    }

    private val callBack: CallBack?
        get() = (parentFragment as? CallBack) ?: (activity as? CallBack)

    interface CallBack {
        fun selectFont(path: String)
        val curFontPath: String
    }
}