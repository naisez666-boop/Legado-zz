package io.legado.app.ui.main.explore

import android.content.Context
import android.os.Bundle
import android.view.Menu
import android.view.MenuItem
import android.view.SubMenu
import android.view.View
import android.view.ViewGroup
import androidx.appcompat.widget.SearchView
import androidx.core.view.isGone
import androidx.fragment.app.viewModels
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.VMBaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppLog
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.BookSourcePart
import io.legado.app.databinding.FragmentExploreBinding
import io.legado.app.databinding.ItemExploreGroupBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.dialogs.alert
import io.legado.app.lib.theme.primaryColor
import io.legado.app.lib.theme.primaryTextColor
import io.legado.app.ui.book.explore.ExploreShowActivity
import io.legado.app.ui.book.search.SearchActivity
import io.legado.app.ui.book.source.edit.BookSourceEditActivity
import io.legado.app.ui.main.MainFragmentInterface
import io.legado.app.utils.applyTint
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivity
import io.legado.app.utils.transaction
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers.IO
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.launch

/**
 * 发现界面
 */
class ExploreFragment() : VMBaseFragment<ExploreViewModel>(R.layout.fragment_explore),
    MainFragmentInterface,
    ExploreAdapter.CallBack {

    constructor(position: Int) : this() {
        val bundle = Bundle()
        bundle.putInt("position", position)
        arguments = bundle
    }

    override val position: Int? get() = arguments?.getInt("position")

    override val viewModel by viewModels<ExploreViewModel>()
    private val binding by viewBinding(FragmentExploreBinding::bind)
    private val adapter by lazy { ExploreAdapter(requireContext(), this) }
    private val linearLayoutManager by lazy { LinearLayoutManager(context) }
    private val searchView: SearchView by lazy {
        binding.titleBar.findViewById(R.id.search_view)
    }
    private val diffItemCallBack = ExploreDiffItemCallBack()
    private val groups = linkedSetOf<String>()
    private var exploreFlowJob: Job? = null
    private var groupsMenu: SubMenu? = null

    // ========================================================================
    // 修改文件路径：app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt
    // 改动说明：发现页改为「分组模式」展示
    // 1.selectedGroup 为空时：列表显示发现分组，点进分组后才显示组内发现源与目录
    // 2.selectedGroup 不为空时：沿用原有发现源列表逻辑
    // 3.顶栏新增「分组列表」菜单，可随时返回分组列表
    // ========================================================================
    private var selectedGroup: String? = null
    private val groupAdapter by lazy { GroupAdapter(requireContext()) }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        setSupportToolbar(binding.titleBar.toolbar)
        initSearchView()
        initRecyclerView()
        initGroupData()
        // 默认显示分组列表
        showGroupList()
    }

    override fun onCompatCreateOptionsMenu(menu: Menu) {
        super.onCompatCreateOptionsMenu(menu)
        menuInflater.inflate(R.menu.main_explore, menu)
        groupsMenu = menu.findItem(R.id.menu_group)?.subMenu
        upGroupsMenu()
    }

    private fun initSearchView() {
        searchView.applyTint(primaryTextColor)
        searchView.isSubmitButtonEnabled = true
        searchView.queryHint = getString(R.string.screen_find)
        searchView.setOnQueryTextListener(object : SearchView.OnQueryTextListener {
            override fun onQueryTextSubmit(query: String?): Boolean {
                return false
            }

            override fun onQueryTextChange(newText: String?): Boolean {
                upExploreData(newText)
                return false
            }
        })
    }

    private fun initRecyclerView() {
        binding.rvFind.setEdgeEffectColor(primaryColor)
        binding.rvFind.layoutManager = linearLayoutManager
        binding.rvFind.adapter = adapter
        adapter.registerAdapterDataObserver(object : RecyclerView.AdapterDataObserver() {

            override fun onItemRangeInserted(positionStart: Int, itemCount: Int) {
                super.onItemRangeInserted(positionStart, itemCount)
                if (positionStart == 0) {
                    binding.rvFind.scrollToPosition(0)
                }
            }
        })
    }

    private fun initGroupData() {
        viewLifecycleOwner.lifecycleScope.launch {
            appDb.bookSourceDao.flowExploreGroups()
                .flowWithLifecycleAndDatabaseChange(
                    viewLifecycleOwner.lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_SOURCE_TABLE_NAME
                )
                .conflate()
                .distinctUntilChanged()
                .collect {
                    groups.clear()
                    groups.addAll(it)
                    upGroupsMenu()
                    // 分组模式下实时刷新分组列表
                    if (selectedGroup == null) {
                        groupAdapter.setItems(groups.toList())
                    }
                    delay(500)
                }
        }
    }

    /**
     * 修改文件路径：app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt
     * 改动说明：切换到分组列表模式（发现页默认界面）
     * 层级区分：退出分组时顶栏恢复「发现」标题并移除返回箭头
     */
    private fun showGroupList() {
        selectedGroup = null
        binding.tvEmptyMsg.isGone = true
        binding.rvFind.adapter = groupAdapter
        groupAdapter.setItems(groups.toList())
        // 层级区分：顶栏恢复默认标题、隐藏返回箭头
        binding.titleBar.title = getString(R.string.discovery)
        binding.titleBar.toolbar.navigationIcon = null
        binding.titleBar.toolbar.setNavigationOnClickListener(null)
        // 退出分组后取消原有的发现数据监听，并清空搜索框内容
        exploreFlowJob?.cancel()
        searchView.setQuery("", false)
    }

    /**
     * 修改文件路径：app/src/main/java/io/legado/app/ui/main/explore/ExploreFragment.kt
     * 改动说明：进入指定分组，显示该分组下的发现源与发现目录
     * 层级区分：顶栏显示返回箭头 + 当前分组名，点击返回箭头回到分组列表
     */
    private fun selectGroup(name: String) {
        selectedGroup = name
        binding.rvFind.adapter = adapter
        searchView.setQuery("group:$name", true)
        // 层级区分：顶栏标题改为分组名并显示返回箭头
        binding.titleBar.title = name
        binding.titleBar.toolbar.setNavigationIcon(R.drawable.ic_arrow_back)
        binding.titleBar.toolbar.setNavigationOnClickListener {
            showGroupList()
        }
    }

    private fun upExploreData(searchKey: String? = null) {
        exploreFlowJob?.cancel()
        exploreFlowJob = viewLifecycleOwner.lifecycleScope.launch {
            when {
                searchKey.isNullOrBlank() -> {
                    // 修改说明：无查询时，未选中分组则显示分组列表模式
                    if (selectedGroup == null) {
                        showGroupList()
                        return@launch
                    }
                    appDb.bookSourceDao.flowExplore()
                }

                searchKey.startsWith("group:") -> {
                    val key = searchKey.substringAfter("group:")
                    binding.rvFind.adapter = adapter
                    appDb.bookSourceDao.flowGroupExplore(key)
                }

                else -> {
                    // 修改说明：普通关键字搜索时切换到发现源列表模式显示搜索结果
                    binding.rvFind.adapter = adapter
                    appDb.bookSourceDao.flowExplore(searchKey)
                }
            }.flowWithLifecycleAndDatabaseChange(
                viewLifecycleOwner.lifecycle,
                Lifecycle.State.RESUMED,
                AppDatabase.BOOK_SOURCE_TABLE_NAME
            ).catch {
                AppLog.put("发现界面更新数据出错", it)
            }.conflate().flowOn(IO).collect {
                binding.tvEmptyMsg.isGone = it.isNotEmpty() || searchView.query.isNotEmpty()
                adapter.setItems(it, diffItemCallBack)
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
        searchView.clearFocus()
        adapter.onPause()
        super.onPause()
    }

    private fun upGroupsMenu() = groupsMenu?.transaction { subMenu ->
        subMenu.removeGroup(R.id.menu_group_text)
        groups.forEach {
            subMenu.add(R.id.menu_group_text, Menu.NONE, Menu.NONE, it)
        }
    }

    override val scope: CoroutineScope
        get() = viewLifecycleOwner.lifecycleScope

    override fun onCompatOptionsItemSelected(item: MenuItem) {
        super.onCompatOptionsItemSelected(item)
        if (item.groupId == R.id.menu_group_text) {
            // 修改文件路径：ExploreFragment.kt 点击顶栏分组菜单项 → 进入对应分组
            selectGroup(item.title.toString())
        }
        when (item.itemId) {
            // 修改文件路径：ExploreFragment.kt 顶栏「分组列表」菜单 → 返回分组列表模式
            R.id.menu_show_groups -> showGroupList()
        }
    }

    /**
     * 修改文件路径：ExploreFragment.kt（内部类）
     * 改动说明：发现分组列表适配器，展示所有发现分组，点击进入分组
     */
    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<String, ItemExploreGroupBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemExploreGroupBinding {
            return ItemExploreGroupBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemExploreGroupBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.tvGroupName.text = item
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemExploreGroupBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    selectGroup(it)
                }
            }
        }
    }

    override fun scrollTo(pos: Int) {
        (binding.rvFind.layoutManager as LinearLayoutManager).scrollToPositionWithOffset(pos, 0)
    }

    override fun openExplore(sourceUrl: String, title: String, exploreUrl: String?) {
        if (exploreUrl.isNullOrBlank()) return
        startActivity<ExploreShowActivity> {
            putExtra("exploreName", title)
            putExtra("sourceUrl", sourceUrl)
            putExtra("exploreUrl", exploreUrl)
        }
    }

    override fun editSource(sourceUrl: String) {
        startActivity<BookSourceEditActivity> {
            putExtra("sourceUrl", sourceUrl)
        }
    }

    override fun toTop(source: BookSourcePart) {
        viewModel.topSource(source)
    }

    override fun deleteSource(source: BookSourcePart) {
        alert(R.string.draw) {
            setMessage(getString(R.string.sure_del) + "\n" + source.bookSourceName)
            noButton()
            yesButton {
                viewModel.deleteSource(source)
            }
        }
    }

    override fun searchBook(bookSource: BookSourcePart) {
        SearchActivity.start(requireContext(), bookSource)
    }

    fun compressExplore() {
        if (!adapter.compressExplore()) {
            if (AppConfig.isEInkMode) {
                binding.rvFind.scrollToPosition(0)
            } else {
                binding.rvFind.smoothScrollToPosition(0)
            }
        }
    }

}
