<div align="center">
<img width="125" height="125" src="app/src/main/res/mipmap-xxxhdpi/ic_launcher.png" alt="legado"/>
<br>
**Legado-zz · 个人定制版阅读**
<br>
基于开源阅读 [Legado](https://github.com/gedoor/legado) 的个人自用定制版，界面全新玻璃拟态风格，分组体验大幅优化。
</div>

## 下载

[![GitHub Release](https://img.shields.io/badge/%E4%B8%8B%E8%BD%BD%E6%9C%80%E6%96%B0APK-Releases-blue)](https://github.com/naisez666-boop/Legado-zz/releases)

- APK 由 GitHub Actions 自动构建发布，每次代码更新后自动打最新包
- 版本号格式：`3.年月日时.提交数`（如 `3.26.112410.1523`），每次更新版本号可见变化
- 应用内「关于 → 检查更新」可直接检测并跳转本仓库 Releases

## 定制功能

### 界面
- **玻璃拟态主题**：半透明磨砂卡片 + 霓虹紫蓝强调色，深浅两套配色跟随系统自动切换
- **亮暗手动切换**：「我的 → 主题模式」可选跟随系统 / 亮色 / 暗色
- **层级清晰的分组界面**：进入分组后顶栏显示返回箭头 + 当前分组名，返回即回分组列表
- 卡片化列表：书架分组、书源分组、书源列表、发现页统一圆角玻璃卡片

### 书架
- 番茄式分组混排：分组文件夹与书籍混排展示，分组文件夹支持四宫格封面拼图
- 书籍分组唯一，根界面隐藏「全部/本地/音频/视频」等系统虚拟分组，只显示真实分组
- 顶栏新增阅读历史入口

### 发现页
- 分组模式：默认显示发现分组列表，点进分组后显示组内发现源与目录

### 书源管理
- 分组浏览：默认显示所有分组列表（源数量 + 有发现数量一目了然）
- 自动分组：导入书源自动进入「待分配」，一键按大类 + 细分规则自动归类
- 新建分组 + 手动归组：勾选书源后通过底部菜单「加入分组」自由整理
- 分组与发现页天然同步：分组内带发现页的书源自动出现在发现页对应分组

## 原版主要功能

1. 自定义书源，自己设置规则，抓取网页数据，规则简单易懂，软件内有规则说明
2. 列表书架，网格书架自由切换
3. 书源规则支持搜索及发现，所有找书看书功能全部自定义
4. 订阅内容，可以订阅想看的任何内容
5. 支持替换净化，去除广告替换内容很方便
6. 支持本地 TXT、EPUB 阅读，手动浏览，智能扫描
7. 高度自定义阅读界面：字体、颜色、背景、行距、段距、加粗、简繁转换等
8. 多种翻页模式：覆盖、仿真、滑动、滚动等
9. 软件开源，无广告

> 新用户？软件不提供内容，需要您自己手动添加书源。可参考 [帮助文档](https://www.yuque.com/legado/wiki)。

## API

* 阅读3.0 提供了 2 种方式的 API：`Web 方式`和 `Content Provider 方式`，详见 [api.md](api.md)
* 可通过 url 唤起阅读进行一键导入，url 格式: `legado://import/{path}?src={url}`
* path 类型: bookSource, rssSource, replaceRule, textTocRule, httpTTS, theme, readConfig, dictRule, addToBookshelf
* path 类型解释: 书源, 订阅源, 替换规则, 本地 txt 小说目录规则, 在线朗读引擎, 主题, 阅读排版, 添加到书架

## 免责声明

本软件依赖系统 webview 提供网页访问功能，通过用户自定义的第三方网页书源返回阅读内容，对其返回内容概不负责，亦不承担任何法律责任。任何第三方网页书源均系他人制作或提供，非软件作者。您应该对搜索到的结果自行承担风险。

## 构建

```bash
.\gradlew.bat :app:assembleAppRelease
```

推送代码到 main 分支后，GitHub Actions 会自动构建并发布 Release APK。

## 界面预览

<img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8bdVVU8X7gejuELokmv2DGfJwQpP2UFcwHqqFK4nzfgNUTL6Wij4QqsJySkoPfQuCB1l5MstXCXWjmH5Mm6PGkU!/b=&bo=UQRICVEESAkWADA!&ek=1&tl=1" width="270">  <img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8epqbe*Bzro*ybEEVmlLpuD9E14zFMjtRx2JeZJdVzNYeSZqCm5Kv0MruAumEe0tJ77n*agIEzJIaSN9VHAen2I!/b=&bo=UQRICVEESAkWADA!&ek=1&tl=1" width="270">  
<img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8SQggDiubhwWyktI5v48VluvK1vwdY2OV9RauO04TcXb4425kVcBiAz2DhpfnrDZKnSpwp8SLnDtKOxtLm1yP9I!/b=&bo=UQRICVEESAkWADA!&ek=1&tl=1" width="270">  <img src="https://channel.qpic.cn/psc?/channel/NaDwC23LjvXqrn3RH.9z8Tx.7oqZyCeAdzSVQa6ZgtXKE*EaUCdEJ3zm2ynIrgem10TqY8aKvSbG3v5FNNHtwSFyN5QfLW*Zd14gUetfsC8!/b=&bo=UgRKCVIESgkWADA!&ek=1&tl=1" width="270">

> 以上为沿用预览图，后续可替换为本定制版实际截图。

## 感谢

基于 [gedoor/legado](https://github.com/gedoor/legado) 及各衍生版本，感谢原作者与社区贡献者，以及以下开源库：

jsoup · JsoupXpath · json-path · rhino-android · okhttp · glide · nanohttpd · bga-qrcode-zxing · commons-text · markwon · hanlp · epublib · LyricViewX · editor
