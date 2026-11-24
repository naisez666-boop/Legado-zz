package io.legado.app.help.source

import io.legado.app.constant.BookSourceType
import io.legado.app.data.entities.BookSource

/**
 * 修改文件路径：app/src/main/java/io/legado/app/help/source/SourceGroupRules.kt（新增文件）
 * 改动说明：书源自动分组规则统一收口
 * 1.导入书源时：未指定分组且不保留原分组的书源一律进入「待分配」分组
 * 2.书源管理界面点击「自动分组」菜单时：对「待分配」分组内的书源按逻辑分类
 *   - 大类：小说/漫画/音频/影视/文件（按书源类型）
 *   - 细分：R18、VIP付费、女频、轻小说、官方正版（按关键词识别）、
 *           要登录（含登录配置）、校验失败（无搜索和发现规则）
 */
object SourceGroupRules {

    // 待分配分组名：所有未适配分类的导入书源先进此分组
    const val GROUP_TODO = "待分配"

    // 大类分组名
    const val GROUP_NOVEL = "小说"
    const val GROUP_MANGA = "漫画"
    const val GROUP_AUDIO = "音频"
    const val GROUP_VIDEO = "影视"
    const val GROUP_FILE = "文件"

    // 细分小类分组名
    const val GROUP_R18 = "R18"
    const val GROUP_NEED_LOGIN = "要登录"
    const val GROUP_CHECK_FAIL = "校验失败"
    const val GROUP_VIP = "VIP付费"
    const val GROUP_FEMALE = "女频"
    const val GROUP_MALE = "男频"
    const val GROUP_LIGHT_NOVEL = "轻小说"
    const val GROUP_OFFICIAL = "官方正版"
    const val GROUP_FINE_EDIT = "精校精排"
    const val GROUP_FINISHED = "全本"
    const val GROUP_FOREIGN = "外文翻译"

    // R18 识别关键词（匹配书源名称或地址，可自行补充）
    private val r18Keywords = arrayOf(
        "r18", "18+", "nsfw", "成人", "情色", "色情",
        "涩涩", "福利", "绅士", "肉漫", "黄文"
    )

    // VIP/付费 识别关键词
    private val vipKeywords = arrayOf(
        "vip", "付费", "收费", "会员"
    )

    // 女频 识别关键词
    private val femaleKeywords = arrayOf(
        "女频", "言情", "女生", "晋江", "红袖", "潇湘"
    )

    // 男频 识别关键词
    private val maleKeywords = arrayOf(
        "男频", "笔趣阁", "顶点", "飞卢页", "男生"
    )

    // 轻小说/二次元 识别关键词
    private val lightNovelKeywords = arrayOf(
        "轻小说", "二次元", "动漫", "哔哩", "bilibili"
    )

    // 官方正版 识别关键词
    private val officialKeywords = arrayOf(
        "正版", "起点", "番茄", "七猫", "掌阅", "咪咕", "书旗", "飞卢"
    )

    // 精校精排 识别关键词
    private val fineEditKeywords = arrayOf(
        "精校", "精排", "校正", "排版"
    )

    // 全本 识别关键词
    private val finishedKeywords = arrayOf(
        "全本", "完本", "完结"
    )

    // 外文翻译 识别关键词
    private val foreignKeywords = arrayOf(
        "翻译", "生肉", "日文", "韩文", "英文站", "机翻"
    )

    /**
     * 按逻辑规则给书源计算分组（返回逗号分隔字符串，不含「待分配」）
     */
    fun classify(source: BookSource): String {
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
        // 3.细分小类：VIP付费（名称或地址含付费关键词）
        if (vipKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_VIP)
        }
        // 4.细分小类：女频（名称或地址含女频关键词）
        if (femaleKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_FEMALE)
        }
        // 5.细分小类：男频（名称或地址含男频关键词）
        if (maleKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_MALE)
        }
        // 6.细分小类：轻小说/二次元（名称或地址含相关关键词）
        if (lightNovelKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_LIGHT_NOVEL)
        }
        // 7.细分小类：官方正版（名称或地址含正版平台关键词）
        if (officialKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_OFFICIAL)
        }
        // 8.细分小类：精校精排（名称或地址含精校关键词）
        if (fineEditKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_FINE_EDIT)
        }
        // 9.细分小类：全本（名称或地址含全本关键词）
        if (finishedKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_FINISHED)
        }
        // 10.细分小类：外文翻译（名称或地址含外文关键词）
        if (foreignKeywords.any { nameAndUrl.contains(it) }) {
            groups.add(GROUP_FOREIGN)
        }
        // 11.细分小类：要登录（配置了登录地址或登录UI）
        if (!source.loginUrl.isNullOrBlank() || !source.loginUi.isNullOrBlank()) {
            groups.add(GROUP_NEED_LOGIN)
        }
        // 12.细分小类：校验失败（既没有搜索规则也没有发现规则，无法正常使用）
        if (source.searchUrl.isNullOrBlank() && source.exploreUrl.isNullOrBlank()) {
            groups.add(GROUP_CHECK_FAIL)
        }
        return groups.joinToString(",")
    }

}
