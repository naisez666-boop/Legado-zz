package io.legado.app.ui.association

import android.app.Application
import androidx.lifecycle.MutableLiveData
import com.jayway.jsonpath.JsonPath
import io.legado.app.R
import io.legado.app.base.BaseViewModel
import io.legado.app.constant.AppConst
import io.legado.app.constant.AppLog
import io.legado.app.constant.AppPattern
import io.legado.app.constant.BookSourceType
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSource
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.exception.NoStackTraceException
import io.legado.app.help.book.ContentProcessor
import io.legado.app.help.config.AppConfig
import io.legado.app.help.http.decompressed
import io.legado.app.help.http.newCallResponseBody
import io.legado.app.help.http.okHttpClient
import io.legado.app.help.source.SourceHelp
import io.legado.app.model.RuleUpdate
import io.legado.app.utils.GSON
import io.legado.app.utils.fromJsonArray
import io.legado.app.utils.fromJsonObject
import io.legado.app.utils.inputStream
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.isJsonArray
import io.legado.app.utils.isJsonObject
import io.legado.app.utils.isUri
import io.legado.app.utils.splitNotBlank
import androidx.core.net.toUri


class ImportBookSourceViewModel(app: Application) : BaseViewModel(app) {
    var isAddGroup = false
    var groupName: String? = null

    companion object {
        // ====================================================================
        // 修改文件路径：app/src/main/java/io/legado/app/ui/association/ImportBookSourceViewModel.kt
        // 改动说明：新增书源导入自动分组规则
        // 1.按书源类型自动分大类：小说/漫画/音频/影视/文件
        // 2.小说类细分小类：R18(按关键词识别)、要登录(含登录配置)、校验失败(无搜索和发现规则)
        // 3.所有新导入的书源同时进入「待整理」分组，由用户后续手动归组
        // ====================================================================

        // 书源类型对应的大类分组名
        const val GROUP_NOVEL = "小说"
        const val GROUP_MANGA = "漫画"
        const val GROUP_AUDIO = "音频"
        const val GROUP_VIDEO = "影视"
        const val GROUP_FILE = "文件"
        // 细分小类分组名
        const val GROUP_R18 = "R18"
        const val GROUP_NEED_LOGIN = "要登录"
        const val GROUP_CHECK_FAIL = "校验失败"
        // 所有导入的书源先进此临时分组，等待用户手动整理
        const val GROUP_TODO = "待整理"

        // R18 识别关键词（匹配书源名称或地址，可自行补充）
        private val r18Keywords = arrayOf(
            "r18", "18+", "nsfw", "成人", "情色", "色情",
            "涩涩", "福利", "绅士", "肉漫", "黄文"
        )
    }
    val errorLiveData = MutableLiveData<String>()
    val successLiveData = MutableLiveData<Int>()

    val allSources = arrayListOf<BookSource>()
    val checkSources = arrayListOf<BookSourcePart?>()
    val selectStatus = arrayListOf<Boolean>()
    val newSourceStatus = arrayListOf<Boolean>()
    val updateSourceStatus = arrayListOf<Boolean>()

    val isSelectAll: Boolean
        get() {
            selectStatus.forEach {
                if (!it) {
                    return false
                }
            }
            return true
        }

    val isSelectAllNew: Boolean
        get() {
            newSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val isSelectAllUpdate: Boolean
        get() {
            updateSourceStatus.forEachIndexed { index, b ->
                if (b && !selectStatus[index]) {
                    return false
                }
            }
            return true
        }

    val selectCount: Int
        get() {
            var count = 0
            selectStatus.forEach {
                if (it) {
                    count++
                }
            }
            return count
        }

    fun importSelect(finally: () -> Unit) {
        execute {
            val group = groupName?.trim()
            val keepName = AppConfig.importKeepName
            val keepGroup = AppConfig.importKeepGroup
            val keepEnable = AppConfig.importKeepEnable
            val selectSource = arrayListOf<BookSource>()
            selectStatus.forEachIndexed { index, b ->
                if (b) {
                    val source = allSources[index]
                    checkSources[index]?.let {
                        if (keepName) {
                            source.bookSourceName = it.bookSourceName
                        }
                        if (keepGroup) {
                            source.bookSourceGroup = it.bookSourceGroup
                        }
                        if (keepEnable) {
                            source.enabled = it.enabled
                            source.enabledExplore = it.enabledExplore
                        }
                        source.customOrder = it.customOrder
                    }
                    if (!group.isNullOrEmpty()) {
                        if (isAddGroup) {
                            val groups = linkedSetOf<String>()
                            source.bookSourceGroup?.splitNotBlank(AppPattern.splitGroupRegex)?.let {
                                groups.addAll(it)
                            }
                            groups.add(group)
                            source.bookSourceGroup = groups.joinToString(",")
                        } else {
                            source.bookSourceGroup = group
                        }
                    } else {
                        // ------------------------------------------------------------
                        // 修改文件路径：app/src/main/java/io/legado/app/ui/association/ImportBookSourceViewModel.kt
                        // 改动说明：导入时用户未指定分组，则自动按规则分组（大类 + 细分小类 + 待整理）
                        // 若勾选了"保留原有分组"且原分组不为空，则不做自动分组
                        // ------------------------------------------------------------
                        val hasKeepGroup = keepGroup && !source.bookSourceGroup.isNullOrBlank()
                        if (!hasKeepGroup) {
                            source.bookSourceGroup = autoClassifyGroups(source)
                        }
                    }
                    selectSource.add(source)
                }
            }
            SourceHelp.insertBookSource(*selectSource.toTypedArray())
            ContentProcessor.upReplaceRules()
        }.onFinally {
            finally.invoke()
        }
    }

    /**
     * 修改文件路径：app/src/main/java/io/legado/app/ui/association/ImportBookSourceViewModel.kt
     * 改动说明：自动给书源计算分组
     * 规则：大类（小说/漫画/音频/影视/文件）+ 细分小类（R18/要登录/校验失败）+ 待整理
     * 返回逗号分隔的分组字符串，直接覆盖书源原有分组
     */
    private fun autoClassifyGroups(source: BookSource): String {
        val groups = linkedSetOf<String>()
        // 1.按书源类型分大类
        groups.add(
            when (source.bookSourceType) {
                BookSourceType.image -> GROUP_MANGA
                BookSourceType.audio -> GROUP_AUDIO
                BookSourceType.video -> GROUP_VIDEO
                BookSourceType.file -> GROUP_FILE
                else -> GROUP_NOVEL
            }
        )
        // 2.细分小类：R18（名称或地址含成人关键词）
        val nameAndUrl = "${source.bookSourceName}${source.bookSourceUrl}".lowercase()
        if (r18Keywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_R18)
        }
        // 3.细分小类：要登录（配置了登录地址或登录UI）
        if (!source.loginUrl.isNullOrBlank() || !source.loginUi.isNullOrBlank()) {
            groups.add(GROUP_NEED_LOGIN)
        }
        // 4.细分小类：校验失败（既没有搜索规则也没有发现规则，无法正常使用）
        if (source.searchUrl.isNullOrBlank() && source.exploreUrl.isNullOrBlank()) {
            groups.add(GROUP_CHECK_FAIL)
        }
        // 5.统一加入待整理分组，由用户后续手动归组
        groups.add(GROUP_TODO)
        return groups.joinToString(",")
    }

    fun importSource(text: String) {
        execute {
            val mText = text.trim()
            when {
                mText.isJsonObject() -> {
                    kotlin.runCatching {
                        val json = JsonPath.parse(mText)
                        json.read<List<String>>("$.sourceUrls")
                    }.onSuccess { listUrl ->
                        listUrl.forEach {
                            importSourceUrl(it)
                        }
                    }.onFailure {
                        GSON.fromJsonObject<BookSource>(mText).getOrThrow().let {
                            if (it.bookSourceUrl.isEmpty()) {
                                throw NoStackTraceException("不是书源")
                            }
                            allSources.add(it)
                        }
                    }
                }

                mText.isJsonArray() -> GSON.fromJsonArray<BookSource>(mText).getOrThrow()
                    .let { items ->
                        val source = items.firstOrNull() ?: return@let
                        if (source.bookSourceUrl.isEmpty()) {
                            throw NoStackTraceException("不是书源")
                        }
                        allSources.addAll(items)
                    }

                mText.isAbsUrl() -> {
                    importSourceUrl(mText)
                }

                mText.isUri() -> {
                    val uri = mText.toUri()
                    uri.inputStream(context).getOrThrow().use { inputS ->
                        GSON.fromJsonArray<BookSource>(inputS).getOrThrow().let {
                            val source = it.firstOrNull() ?: return@let
                            if (source.bookSourceUrl.isEmpty()) {
                                throw NoStackTraceException("不是书源")
                            }
                            allSources.addAll(it)
                        }
                    }
                }

                else -> throw NoStackTraceException(context.getString(R.string.wrong_format))
            }
        }.onError {
            errorLiveData.postValue("ImportError:${it.localizedMessage}")
            AppLog.put("ImportError:${it.localizedMessage}", it)
        }.onSuccess {
            comparisonSource()
        }
    }

    private suspend fun importSourceUrl(url: String) {
        RuleUpdate.cacheBookSourceMap[url]?.also {
            allSources.addAll(it)
            RuleUpdate.cacheBookSourceMap.remove(url)
            return
        }
        okHttpClient.newCallResponseBody {
            if (url.endsWith("#requestWithoutUA")) {
                url(url.substringBeforeLast("#requestWithoutUA"))
                header(AppConst.UA_NAME, "null")
            } else {
                url(url)
            }
        }.decompressed().byteStream().use {
            GSON.fromJsonArray<BookSource>(it).getOrThrow().let { list ->
                val source = list.firstOrNull() ?: return@let
                if (source.bookSourceUrl.isEmpty()) {
                    throw NoStackTraceException("不是书源")
                }
                allSources.addAll(list)
            }
        }
    }

    private fun comparisonSource() {
        execute {
            allSources.forEach {
                val source = appDb.bookSourceDao.getBookSourcePart(it.bookSourceUrl)
                checkSources.add(source)
                selectStatus.add(source == null || source.lastUpdateTime < it.lastUpdateTime)
                newSourceStatus.add(source == null)
                updateSourceStatus.add(source != null && source.lastUpdateTime < it.lastUpdateTime)
            }
            successLiveData.postValue(allSources.size)
        }
    }

}