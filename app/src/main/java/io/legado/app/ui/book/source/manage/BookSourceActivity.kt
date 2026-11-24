package io.legado.app.ui.book.source.manage

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.WindowManager
import androidx.activity.OnBackPressedCallback
import androidx.activity.viewModels
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.widget.PopupMenu
import androidx.appcompat.widget.SearchView
import androidx.core.os.bundleOf
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleOwner
import androidx.lifecycle.LifecycleRegistry
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.ItemTouchHelper
import com.google.android.material.snackbar.Snackbar
import io.legado.app.R
import io.legado.app.base.VMBaseActivity
import io.legado.app.constant.AppLog
import io.legado.app.constant.EventBus
import io.legado.app.constant.PlazaUrl
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.ActivityBookSourceBinding
import io.legado.app.databinding.DialogEditTextBinding
import io.legado.app.help.DirectLinkUpload
import io.legado.app.help.config.LocalConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.model.CheckSource
import io.legado.app.model.Debug
import io.legado.app.ui.association.ImportBookSourceDialog
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.search.SearchScope
import io.legado.app.ui.book.source.debug.BookSourceDebugActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.config.CheckSourceConfig
import io.legado.app.ui.file.HandleFileContract
import io.legado.app.ui.qrcode.QrCodeResult
import io.legado.app.ui.widget.SelectActionBar
import io.legado.app.ui.widget.recycler.DragSelectTouchHelper
import io.legado.app.ui.widget.recycler.ItemTouchCallback
import io.legado.app.utils.ACache
import io.legado.app.utils.NetworkUtils
import io.legado.app.utils.StringUtils
import io.legado.app.utils.applyTint
import io.legado.app.utils.cnCompare
import io.legado.app.utils.dpToPx
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.flowWithLifecycleAndDatabaseChangeFirst
import io.legado.app.utils.isAbsUrl
import io.legado.app.utils.launch
import io.legado.app.utils.observeEvent
import io.legado.app.utils.sendToClip
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.share
import io.legado.app.utils.showDialogFragment
import io.legado.app.utils.showHelp
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.startActivity
import io.legado.app.utils.toastOnUi
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * 书源管理界面
 */
class BookSourceActivity : VMBaseActivity<ActivityBookSourceBinding, BookSourceViewModel>(),
    SourceGroupBrowseFragment.CallBack,
    PopupMenu.OnMenuItemClickListener,
    BookSourceAdapter.CallBack,
    SelectActionBar.CallBack,
    SearchView.OnQueryTextListener {
    override val binding by viewBinding(ActivityBookSourceBinding::inflate)
    override val viewModel by viewModels<BookSourceViewModel>()
    private val importRecordKey = "bookSourceRecordKey"
    private val adapter by lazy { BookSourceAdapter(this, this, binding.recyclerView) }
    private val itemTouchCallback by lazy { ItemTouchCallback(adapter) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private var sourceFlowJob: Job? = null
    private var checkMessageRefreshJob: Job? = null
    private val groups = linkedSetOf<String>()
    private var groupMenu: SubMenu? = null
    override var sort = BookSourceSort.Default
        private set
    override var sortAscending = true
        private set
    private var snackBar: Snackbar? = null
    private var groupSourcesByDomain = false
    private val hostMap = hashMapOf<String, String>()
    private val qrResult = registerForActivityResult(QrCodeResult()) {
        it ?: return@registerForActivityResult
        showDialogFragment(ImportBookSourceDialog(it))
    }
    private val importDoc = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            showDialogFragment(ImportBookSourceDialog(uri.toString()))
        }
    }
    private val exportResult = registerForActivityResult(HandleFileContract()) {
        it.uri?.let { uri ->
            alert(R.string.export_success) {
                val url = uri.toString()
                if (url.isAbsUrl()) {
                    setMessage(DirectLinkUpload.getSummary())
                    val time = System.currentTimeMillis()
                    shibboleth {
                        val shibbolethUrl = StringUtils.toShibboleth(url, StringUtils.BOOK_SOURCE, time)
                        alert(R.string.shibboleth) {
                            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                                editView.setText(shibbolethUrl)
                            }
                            customView { alertBinding.root }
                            okButton {
                                sendToClip(shibbolethUrl)
                            }
                        }
                    }
                }
                val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.hint = getString(R.string.path)
                    editView.setText(url)
                }
                customView { alertBinding.root }
                okButton {
                    sendToClip(url)
                }
            }
        }
    }
    private val groupMenuLifecycleOwner = object : LifecycleOwner {
        private val registry = LifecycleRegistry(this)
        override val lifecycle: Lifecycle get() = registry

        fun onMenuOpened() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_START)
        }

        fun onMenuClosed() {
            registry.handleLifecycleEvent(Lifecycle.Event.ON_STOP)
        }

    }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        initRecyclerView()
        initSearchView()
        upBookSource()
        initLiveDataGroup()
        initSelectActionBar()
        resumeCheckSource()
        onBackPressedDispatcher.addCallback(this, backCallback)
        // 修改文件路径：app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt
        // 改动说明：默认显示「分组浏览」层，点击分组进入书源列表，可返回
        showGroupBrowse()
        if (!LocalConfig.bookSourcesHelpVersionIsLast) {
            showHelp("SourceMBookHelp")
        }
    }

    /**
     * 修改文件路径：BookSourceActivity.kt
     * 改动说明：系统返回键/顶栏返回箭头拦截器——
     * 有搜索词时清空搜索（清空后自动恢复分组浏览层）；
     * 处于分组筛选（group:xxx）时，返回键回到「分组浏览」列表而不是退出页面
     */
    private val backCallback = object : OnBackPressedCallback(false) {
        override fun handleOnBackPressed() {
            when {
                searchView.query?.isNotBlank() == true -> {
                    searchView.setQuery("", true)
                }

                else -> {
                    binding.titleBar.title = getString(R.string.book_source)
                    showGroupBrowse()
                }
            }
        }
    }

    /**
     * 修改文件路径：BookSourceActivity.kt
     * 改动说明：记录分组浏览层是否正在显示，供搜索联动使用——
     * 输入搜索词时自动隐藏分组浏览层显示搜索结果；清空搜索词后恢复分组浏览层
     */
    private var groupBrowseShowing = false

    /**
     * 修改说明：记录开始搜索前是否处于分组浏览态——
     * 只有从分组浏览默认页发起的搜索，清空后才恢复分组浏览层；
     * 从某个分组列表内发起的搜索，清空后留在书源列表
     */
    private var browseBeforeSearch = false

    /**
     * 修改文件路径：BookSourceActivity.kt
     * 改动说明：显示「分组浏览」层（所有书源分组列表）
     */
    private fun showGroupBrowse() {
        supportFragmentManager.beginTransaction()
            .replace(R.id.fl_group_browse, SourceGroupBrowseFragment(), "sourceGroupBrowse")
            .commitAllowingStateLoss()
        groupBrowseShowing = true
    }

    /**
     * 修改文件路径：BookSourceActivity.kt
     * 改动说明：隐藏「分组浏览」层
     */
    private fun hideGroupBrowse() {
        supportFragmentManager.findFragmentByTag("sourceGroupBrowse")?.let {
            supportFragmentManager.beginTransaction().remove(it).commitAllowingStateLoss()
        }
        groupBrowseShowing = false
    }

    /**
     * 修改文件路径：BookSourceActivity.kt
     * 改动说明：分组浏览点击分组回调——切换为该分组的书源列表，标题显示分组名
     */
    override fun onGroupBrowseSelect(name: String) {
        hideGroupBrowse()
        searchView.setQuery("group:$name", true)
        binding.titleBar.title = name
    }

    /**
     * 修改文件路径：BookSourceActivity.kt
     * 改动说明：分组浏览「新建分组」回调——输入组名后回到书源列表，
     * 勾选源后点底部菜单「加入分组」即可把源归入新分组；
     * 分组即书源标签，组内带发现页的源会自动出现在发现页对应分组（与发现分组同步）
     */
    override fun onAddGroupRequest() {
        alert(titleResource = R.string.add_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
            }
            customView { alertBinding.root }
            okButton {
                val name = alertBinding.editView.text?.toString()?.trim().orEmpty()
                if (name.isNotEmpty()) {
                    hideGroupBrowse()
                    backCallback.isEnabled = false
                    searchView.setQuery("", true)
                    binding.titleBar.title = getString(R.string.book_source)
                    toastOnUi("分组「$name」已创建：请勾选书源后点底部菜单「加入分组」完成归组")
                }
            }
        }
    }

    override fun onCompatCreateOptionsMenu(menu: Menu): Boolean {
        menuInflater.inflate(R.menu.book_source, menu)
        return super.onCompatCreateOptionsMenu(menu)
    }

    override fun onPrepareOptionsMenu(menu: Menu): Boolean {
        groupMenu = menu.findItem(R.id.menu_group).subMenu
        val sortSubMenu = menu.findItem(R.id.action_sort).subMenu!!
        sortSubMenu.findItem(R.id.menu_sort_desc).isChecked = !sortAscending
        sortSubMenu.setGroupCheckable(R.id.menu_group_sort, true, true)
        upGroupMenu()
        return super.onPrepareOptionsMenu(menu)
    }

    override fun onCompatOptionsItemSelected(item: MenuItem): Boolean {
        when (item.itemId) {
            // 修改文件路径：BookSourceActivity.kt
            // 改动说明：顶栏返回箭头——分组筛选状态下返回分组列表，否则退出页面
            android.R.id.home -> {
                if (backCallback.isEnabled) {
                    backCallback.handleOnBackPressed()
                } else {
                    finish()
                }
                return true
            }
            R.id.menu_add_book_source -> startActivity<BookSourceEditActivity>()
            R.id.menu_import_qr -> qrResult.launch()
            R.id.menu_group_manage -> showDialogFragment<GroupManageDialog>()
            R.id.menu_import_local -> importDoc.launch {
                mode = HandleFileContract.FILE
                allowExtensions = arrayOf("txt", "json")
            }

            R.id.menu_import_onLine -> showImportDialog()

            // 修改文件路径：app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt
            // 改动说明：新增「自动分组」菜单，把「待分配」分组内的书源按逻辑规则分类
            R.id.menu_auto_group -> viewModel.autoGroupSources { count ->
                toastOnUi(if (count > 0) "自动分组完成，已处理 $count 个书源" else "「待分配」分组内没有书源")
            }

            // 修改文件路径：BookSourceActivity.kt
            // 改动说明：新增「重新分类全部书源」——为全部书源按规则补全大类/细分标签，
            // 无法补救的失效源（无搜索无发现规则）自动归入「校验失败」细分
            R.id.menu_reclassify -> alert("重新分类全部书源") {
                setMessage("将按规则为全部书源补全大类与细分标签，原有分组保留，\n无搜索无发现规则的失效源将归入「校验失败」。")
                okButton {
                    viewModel.reclassifyAll { count ->
                        toastOnUi("重新分类完成，共处理 $count 个书源")
                    }
                }
                cancelButton()
            }

            // 修改文件路径：BookSourceActivity.kt
            // 改动说明：新增「书源广场」——从托管广场地址一键拉取书源列表并进入导入流程，
            // 导入的书源会自动进入「待分配」并通过「自动分组」归类；
            // 广场地址可自定义，默认为用户仓库 plaza/sources.json
            R.id.menu_source_plaza -> alert("书源广场") {
                setMessage("从广场地址拉取书源列表后进入导入流程，导入后可用「自动分组」归类。")
                val bindingView = DialogEditTextBinding.inflate(layoutInflater).apply {
                    editView.setText(PlazaUrl.SOURCE_PLAZA)
                    editView.hint = "广场地址(json)"
                }
                customView { bindingView.root }
                okButton {
                    val url = bindingView.editView.text?.toString()?.trim()
                    if (url.isNullOrBlank()) {
                        toastOnUi("广场地址不能为空")
                    } else {
                        showDialogFragment(ImportBookSourceDialog(url))
                    }
                }
                cancelButton()
            }

            // 修改文件路径：BookSourceActivity.kt
            // 改动说明：新增「校验全部启用源」——不用手动勾选，一键联网校验
            // 所有启用状态的书源；失败源由校验服务自动打上失效标签并归组，
            // 校验期间显示进度提示，可随时取消
            R.id.menu_check_all_enabled -> alert("校验全部启用源") {
                setMessage("将对所有启用状态的书源逐个联网校验，耗时较长（进度见界面提示与通知栏），\n失败源将自动打上「网站失效/搜索失效」等标签并归组。")
                okButton {
                    lifecycleScope.launch(IO) {
                        val sources = appDb.bookSourceDao.flowEnabled().first()
                        if (sources.isEmpty()) {
                            toastOnUi("没有启用状态的书源")
                        } else {
                            keepScreenOn(true)
                            CheckSource.start(this@BookSourceActivity, sources)
                            Debug.isChecking = false
                            startCheckMessageRefreshJob(0, 0)
                        }
                    }
                }
                cancelButton()
            }

            R.id.menu_sort_desc -> {
                sortAscending = !sortAscending
                item.isChecked = !sortAscending
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_manual -> {
                item.isChecked = true
                sort = BookSourceSort.Default
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_auto -> {
                item.isChecked = true
                sort = BookSourceSort.Weight
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_name -> {
                item.isChecked = true
                sort = BookSourceSort.Name
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_url -> {
                item.isChecked = true
                sort = BookSourceSort.Url
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_time -> {
                item.isChecked = true
                sort = BookSourceSort.Update
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_respondTime -> {
                item.isChecked = true
                sort = BookSourceSort.Respond
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_sort_enable -> {
                item.isChecked = true
                sort = BookSourceSort.Enable
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_enabled_group -> {
                searchView.setQuery(getString(R.string.enabled), true)
            }

            R.id.menu_disabled_group -> {
                searchView.setQuery(getString(R.string.disabled), true)
            }

            R.id.menu_group_login -> {
                searchView.setQuery(getString(R.string.need_login), true)
            }

            R.id.menu_group_null -> {
                searchView.setQuery(getString(R.string.no_group), true)
            }

            R.id.menu_enabled_explore_group -> {
                searchView.setQuery(getString(R.string.enabled_explore), true)
            }

            R.id.menu_disabled_explore_group -> {
                searchView.setQuery(getString(R.string.disabled_explore), true)
            }

            R.id.menu_group_sources_by_domain -> {
                item.isChecked = !item.isChecked
                groupSourcesByDomain = item.isChecked
                adapter.showSourceHost = item.isChecked
                upBookSource(searchView.query?.toString())
            }

            R.id.menu_help -> showHelp("SourceMBookHelp")
        }
        if (item.groupId == R.id.source_group) {
            searchView.setQuery("group:${item.title}", true)
        }
        return super.onCompatOptionsItemSelected(item)
    }

    private fun initRecyclerView() {
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        // 修改文件路径：BookSourceActivity.kt
        // 改动说明：列表条目已玻璃卡片化自带间距，移除原分隔线避免割裂
        binding.recyclerView.adapter = adapter
        binding.recyclerView.recycledViewPool.setMaxRecycledViews(0, 15)
        // When this page is opened, it is in selection mode
        val dragSelectTouchHelper =
            DragSelectTouchHelper(adapter.dragSelectCallback).setSlideArea(16, 50)
        dragSelectTouchHelper.attachToRecyclerView(binding.recyclerView)
        dragSelectTouchHelper.activeSlideSelect()
        // Note: need judge selection first, so add ItemTouchHelper after it.
        ItemTouchHelper(itemTouchCallback).attachToRecyclerView(binding.recyclerView)
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.queryHint = getString(R.string.search_book_source)
        searchView.setOnQueryTextListener(this)
    }


    private fun upBookSource(searchKey: String? = null) {
        sourceFlowJob?.cancel()
        sourceFlowJob = lifecycleScope.launch {
            when {
                searchKey.isNullOrEmpty() -> {
                    appDb.bookSourceDao.flowAll()
                }

                searchKey == getString(R.string.enabled) -> {
                    appDb.bookSourceDao.flowEnabled()
                }

                searchKey == getString(R.string.disabled) -> {
                    appDb.bookSourceDao.flowDisabled()
                }

                searchKey == getString(R.string.need_login) -> {
                    appDb.bookSourceDao.flowLogin()
                }

                searchKey == getString(R.string.no_group) -> {
                    appDb.bookSourceDao.flowNoGroup()
                }

                searchKey == getString(R.string.enabled_explore) -> {
                    appDb.bookSourceDao.flowEnabledExplore()
                }

                searchKey == getString(R.string.disabled_explore) -> {
                    appDb.bookSourceDao.flowDisabledExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    appDb.bookSourceDao.flowGroupSearch(key)
                }

                else -> {
                    appDb.bookSourceDao.flowSearch(searchKey)
                }
            }.map { data ->
                hostMap.clear()
                if (groupSourcesByDomain) {
                    data.sortedWith(
                        compareBy<BookSourcePart> { getSourceHost(it.bookSourceUrl) == "#" }
                            .thenBy { getSourceHost(it.bookSourceUrl) }
                            .thenByDescending { it.lastUpdateTime })
                } else if (sortAscending) {
                    when (sort) {
                        BookSourceSort.Weight -> data.sortedBy { it.weight }
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o1.bookSourceName.cnCompare(o2.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedBy { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedByDescending { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedBy { it.respondTime }
                        BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sort = -o1.enabled.compareTo(o2.enabled)
                            if (sort == 0) {
                                sort = o1.bookSourceName.cnCompare(o2.bookSourceName)
                            }
                            sort
                        }

                        else -> data
                    }
                } else {
                    when (sort) {
                        BookSourceSort.Weight -> data.sortedByDescending { it.weight }
                        BookSourceSort.Name -> data.sortedWith { o1, o2 ->
                            o2.bookSourceName.cnCompare(o1.bookSourceName)
                        }

                        BookSourceSort.Url -> data.sortedByDescending { it.bookSourceUrl }
                        BookSourceSort.Update -> data.sortedBy { it.lastUpdateTime }
                        BookSourceSort.Respond -> data.sortedByDescending { it.respondTime }
                        BookSourceSort.Enable -> data.sortedWith { o1, o2 ->
                            var sort = o1.enabled.compareTo(o2.enabled)
                            if (sort == 0) {
                                sort = o1.bookSourceName.cnCompare(o2.bookSourceName)
                            }
                            sort
                        }

                        else -> data.reversed()
                    }
                }
            }.flowWithLifecycleAndDatabaseChange(
                lifecycle,
                table = AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("书源界面更新书源出错", it)
            }.flowOn(IO).conflate().collect { data ->
                adapter.setItems(data, adapter.diffItemCallback, !Debug.isChecking)
                itemTouchCallback.isCanDrag =
                    sort == BookSourceSort.Default && !groupSourcesByDomain
                delay(500)
            }
        }
    }

    override fun onResume() {
        super.onResume()
        adapter.upResumed(true)
    }

    override fun onPause() {
        adapter.upResumed(false)
        super.onPause()
    }


    private fun initLiveDataGroup() {
        lifecycleScope.launch {
            appDb.bookSourceDao.flowGroups()
                .flowWithLifecycleAndDatabaseChange(
                    lifecycle,
                    table = AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .flowWithLifecycleAndDatabaseChangeFirst(
                    groupMenuLifecycleOwner.lifecycle,
                    table = AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupMenu()
                    delay(500)
                }
        }
    }

    override fun selectAll(selectAll: Boolean) {
        if (selectAll) {
            adapter.selectAll()
        } else {
            adapter.revertSelection()
        }
    }

    override fun revertSelection() {
        adapter.revertSelection()
    }

    override fun onClickSelectBarMainAction() {
        alert(titleResource = R.string.draw, messageResource = R.string.sure_del) {
            yesButton { viewModel.del(adapter.selection) }
            noButton()
        }
    }

    override fun onMenuOpened(featureId: Int, menu: Menu): Boolean {
        if (menu === groupMenu) {
            groupMenuLifecycleOwner.onMenuOpened()
        }
        return super.onMenuOpened(featureId, menu)
    }

    override fun onPanelClosed(featureId: Int, menu: Menu) {
        super.onPanelClosed(featureId, menu)
        if (menu === groupMenu) {
            groupMenuLifecycleOwner.onMenuClosed()
        }
    }

    private fun initSelectActionBar() {
        binding.selectActionBar.setMainActionText(R.string.delete)
        binding.selectActionBar.inflateMenu(R.menu.book_source_sel)
        binding.selectActionBar.setOnMenuItemClickListener(this)
        binding.selectActionBar.setCallBack(this)
    }

    override fun onMenuItemClick(item: MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_enable_selection -> viewModel.enableSelection(adapter.selection)
            R.id.menu_disable_selection -> viewModel.disableSelection(adapter.selection)
            R.id.menu_enable_explore -> viewModel.enableSelectExplore(adapter.selection)
            R.id.menu_disable_explore -> viewModel.disableSelectExplore(adapter.selection)
            R.id.menu_check_source -> checkSource()
            R.id.menu_top_sel -> viewModel.topSource(*adapter.selection.toTypedArray())
            R.id.menu_bottom_sel -> viewModel.bottomSource(*adapter.selection.toTypedArray())
            R.id.menu_add_group -> selectionAddToGroups()
            R.id.menu_remove_group -> selectionRemoveFromGroups()
            R.id.menu_export_selection -> viewModel.saveToFile(
                adapter,
                searchView.query?.toString(),
                sortAscending,
                sort
            ) { file, name ->
                exportResult.launch {
                    mode = HandleFileContract.EXPORT
                    fileData = HandleFileContract.FileData(
                        name,
                        file,
                        "application/json"
                    )
                }
            }

            R.id.menu_share_source -> viewModel.saveToFile(
                adapter,
                searchView.query?.toString(),
                sortAscending,
                sort
            ) { file, name ->
                share(file)
            }

            R.id.menu_check_selected_interval -> adapter.checkSelectedInterval()
        }
        return true
    }

    @SuppressLint("InflateParams")
    private fun checkSource() {
        val dialog = alert(titleResource = R.string.search_book_key) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "search word"
                editView.setText(CheckSource.keyword)
            }
            customView { alertBinding.root }
            okButton {
                keepScreenOn(true)
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        CheckSource.keyword = it
                    }
                }
                val selectItems = adapter.selection
                CheckSource.start(this@BookSourceActivity, selectItems)
                val adapterItems = adapter.getItems()
                val firstItem = adapterItems.indexOf(selectItems.firstOrNull())
                val lastItem = adapterItems.indexOf(selectItems.lastOrNull())
                Debug.isChecking = firstItem >= 0 && lastItem >= 0
                startCheckMessageRefreshJob(firstItem, lastItem)
            }
            neutralButton(R.string.check_source_config)
            cancelButton()
        }
        //手动设置监听 避免点击打开校验设置后对话框关闭
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL)?.setOnClickListener {
            showDialogFragment<CheckSourceConfig>()
        }
    }

    private fun resumeCheckSource() {
        if (!Debug.isChecking) {
            return
        }
        keepScreenOn(true)
        CheckSource.resume(this)
        startCheckMessageRefreshJob(0, 0)
    }

    @SuppressLint("InflateParams")
    private fun selectionAddToGroups() {
        alert(titleResource = R.string.add_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionAddToGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    @SuppressLint("InflateParams")
    private fun selectionRemoveFromGroups() {
        alert(titleResource = R.string.remove_group) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.setHint(R.string.group_name)
                editView.setFilterValues(groups.toList())
                editView.dropDownHeight = 180.dpToPx()
            }
            customView { alertBinding.root }
            okButton {
                alertBinding.editView.text?.toString()?.let {
                    if (it.isNotEmpty()) {
                        viewModel.selectionRemoveFromGroups(adapter.selection, it)
                    }
                }
            }
            cancelButton()
        }
    }

    private fun upGroupMenu() = groupMenu?.transaction { menu ->
        menu.removeGroup(R.id.source_group)
        groups.forEach {
            menu.add(R.id.source_group, Menu.NONE, Menu.NONE, it)
        }
    }

    @SuppressLint("InflateParams")
    private fun showImportDialog() {
        val aCache = ACache.get(cacheDir = false)
        val cacheUrls: MutableList<String> = aCache
            .getAsString(importRecordKey)
            ?.splitNotBlank(",")
            ?.toMutableList() ?: mutableListOf()
        alert(titleResource = R.string.import_on_line) {
            val alertBinding = DialogEditTextBinding.inflate(layoutInflater).apply {
                editView.hint = "url"
                editView.setFilterValues(cacheUrls)
                editView.delCallBack = {
                    cacheUrls.remove(it)
                    aCache.put(importRecordKey, cacheUrls.joinToString(","))
                }
            }
            customView { alertBinding.root }
            okButton {
                val text = alertBinding.editView.text?.toString()
                text?.let {
                    if (it.isAbsUrl() && !cacheUrls.contains(it)) {
                        cacheUrls.add(0, it)
                        aCache.put(importRecordKey, cacheUrls.joinToString(","))
                    }
                    showDialogFragment(ImportBookSourceDialog(it))
                }
            }
            cancelButton()
        }
    }

    override fun observeLiveBus() {
        observeEvent<String>(EventBus.CHECK_SOURCE) { msg ->
            snackBar?.setText(msg) ?: let {
                snackBar = Snackbar
                    .make(binding.root, msg, Snackbar.LENGTH_INDEFINITE)
                    .setAction(R.string.cancel) {
                        CheckSource.stop(this)
                        Debug.finishChecking()
                    }.apply { show() }
            }
        }
        observeEvent<Int>(EventBus.CHECK_SOURCE_DONE) {
            keepScreenOn(false)
            snackBar?.dismiss()
            snackBar = null
            adapter.notifyItemRangeChanged(
                0,
                adapter.itemCount,
                bundleOf(Pair("checkSourceMessage", null))
            )
            groups.forEach { group ->
                if (group.contains("失效") && searchView.query.isEmpty()) {
                    searchView.setQuery("失效", true)
                    toastOnUi("发现有失效书源，已为您自动筛选！")
                }
            }
        }
    }

    private fun startCheckMessageRefreshJob(firstItem: Int, lastItem: Int) {
        checkMessageRefreshJob?.cancel()
        checkMessageRefreshJob = lifecycleScope.launch {
            repeatOnLifecycle(Lifecycle.State.STARTED) {
                while (isActive) {
                    if (lastItem == 0) {
                        adapter.notifyItemRangeChanged(
                            0,
                            adapter.itemCount,
                            bundleOf(Pair("checkSourceMessage", null))
                        )
                    } else {
                        adapter.notifyItemRangeChanged(
                            firstItem,
                            lastItem + 1,
                            bundleOf(Pair("checkSourceMessage", null))
                        )
                    }
                    if (!Debug.isChecking) {
                        checkMessageRefreshJob?.cancel()
                    }
                    delay(300L)
                }
            }
        }
    }

    /**
     * 保持亮屏
     */
    private fun keepScreenOn(on: Boolean) {
        val isScreenOn =
            (window.attributes.flags and WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON) != 0
        if (on == isScreenOn) return
        if (on) {
            window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        } else {
            window.clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        }
    }

    override fun upCountView() {
        binding.selectActionBar
            .upCountView(adapter.selection.size, adapter.itemCount)
    }

    override fun getSourceHost(origin: String): String {
        return hostMap.getOrPut(origin) {
            NetworkUtils.getSubDomainOrNull(origin) ?: "#"
        }
    }

    override fun onQueryTextChange(newText: String?): Boolean {
        newText?.let {
            // 修改文件路径：BookSourceActivity.kt
            // 改动说明：搜索与分组浏览联动——输入搜索词时隐藏分组浏览层，
            // 直接显示搜索结果（可搜索到分组内的具体书源：源名/组名/URL/备注均支持）；
            // 清空搜索词后自动恢复分组浏览层
            if (it.isNotBlank()) {
                if (groupBrowseShowing) {
                    browseBeforeSearch = true
                    hideGroupBrowse()
                }
            } else if (browseBeforeSearch) {
                browseBeforeSearch = false
                showGroupBrowse()
            }
            upBookSource(it)
            // 修改说明：有搜索词时启用返回拦截，返回键先清空搜索回到之前的层级
            backCallback.isEnabled = it.isNotBlank()
            // 修改文件路径：BookSourceActivity.kt
            // 改动说明：层级区分——分组筛选时顶栏标题显示当前分组名，否则恢复默认标题
            if (it.startsWith("group:")) {
                binding.titleBar.title = it.substringAfter("group:")
            } else {
                binding.titleBar.title = getString(R.string.book_source)
            }
        }
        return false
    }

    override fun onQueryTextSubmit(query: String?): Boolean {
        return false
    }

    override fun del(bookSource: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + bookSource.bookSourceName)
            noButton()
            yesButton {
                viewModel.del(listOf(bookSource))
            }
        }
    }

    override fun edit(bookSource: BookSourcePart) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", bookSource.bookSourceUrl)
        }
    }

    override fun upOrder(items: List<BookSourcePart>) {
        viewModel.upOrder(items)
    }

    override fun enable(enable: Boolean, bookSource: BookSourcePart) {
        viewModel.enable(enable, listOf(bookSource))
    }

    override fun enableExplore(enable: Boolean, bookSource: BookSourcePart) {
        viewModel.enableExplore(enable, listOf(bookSource))
    }

    override fun toTop(bookSource: BookSourcePart) {
        if (sortAscending) {
            viewModel.topSource(bookSource)
        } else {
            viewModel.bottomSource(bookSource)
        }
    }

    override fun toBottom(bookSource: BookSourcePart) {
        if (sortAscending) {
            viewModel.bottomSource(bookSource)
        } else {
            viewModel.topSource(bookSource)
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(this, bookSource)
    }

    override fun debug(bookSource: BookSourcePart) {
        startActivity<BookSourceDebugActivity> {
            putExtra("key", bookSource.bookSourceUrl)
        }
    }

    /**
     * 修改文件路径：app/src/main/java/io/legado/app/ui/book/source/manage/BookSourceActivity.kt
     * 改动说明：实现「更改分组」回调，弹出书源分组选择对话框
     */
    override fun changeGroup(bookSource: BookSourcePart) {
        showDialogFragment(SourceGroupSelectDialog(bookSource.bookSourceUrl))
    }

    override fun finish() {
        if (searchView.query.isNullOrEmpty()) {
            super.finish()
        } else {
            searchView.setQuery("", true)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        if (!Debug.isChecking) {
            Debug.debugMessageMap.clear()
        }
    }

}