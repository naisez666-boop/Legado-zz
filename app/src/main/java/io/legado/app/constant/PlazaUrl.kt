package io.legado.app.constant

/**
 * 修改文件路径：app/src/main/java/io/legado/app/constant/PlazaUrl.kt（新增文件）
 * 改动说明：书源/净化规则广场默认地址——托管在用户仓库 plaza 目录。
 * 用户可在仓库维护 plaza/sources.json 与 plaza/replaceRules.json，
 * App 内「书源广场」「净化规则广场」一键拉取导入；弹窗中地址可自定义
 */
object PlazaUrl {
    const val SOURCE_PLAZA =
        "https://raw.githubusercontent.com/naisez666-boop/Legado-zz/main/plaza/sources.json"
    const val REPLACE_PLAZA =
        "https://raw.githubusercontent.com/naisez666-boop/Legado-zz/main/plaza/replaceRules.json"
}
