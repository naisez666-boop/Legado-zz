package io.legado.app.ui.book.source.manage

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.lifecycle.repeatOnLifecycle
import androidx.recyclerview.widget.LinearLayoutManager
import io.legado.app.R
import io.legado.app.base.BaseFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.constant.AppPattern
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.databinding.FragmentSourceGroupBrowseBinding
import io.legado.app.databinding.ItemSourceGroupBinding
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.splitNotBlank
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.launch

/**
 * 修改文件路径：app/src/main/java/io/legado/app/ui/book/source/manage/SourceGroupBrowseFragment.kt（新增文件）
 * 改动说明：书源管理「分组浏览」界面
 * 1.顶部提供「新建分组」入口（与发现页分组同步：分组即书源标签，组内书源带发现页则自动出现在发现页对应分组）
 * 2.默认显示所有书源分组列表（组名 + 该组书源数量 + 有发现页的源数量，按数量降序排列）
 * 3.点击分组后回调 Activity，切换为该分组的书源列表，可返回分组列表
 */
class SourceGroupBrowseFragment : BaseFragment(R.layout.fragment_source_group_browse) {

    interface CallBack {
        fun onGroupBrowseSelect(name: String)

        /**
         * 修改说明：点击「新建分组」回调，由 Activity 弹输入框处理
         */
        fun onAddGroupRequest()
    }

    private val binding by viewBinding(FragmentSourceGroupBrowseBinding::bind)
    private val groupAdapter by lazy { GroupAdapter(requireContext()) }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.rvGroup.setEdgeEffectColor(primaryColor)
        binding.rvGroup.layoutManager = LinearLayoutManager(requireContext())
        binding.rvGroup.adapter = groupAdapter
        // 修改说明：顶部「新建分组」入口点击回调
        binding.btnAddGroup.setOnClickListener {
            (activity as? CallBack)?.onAddGroupRequest()
        }
        // 实时统计各分组书源数量与发现源数量（数据库变动后自动刷新）
        viewLifecycleOwner.lifecycleScope.launch {
            viewLifecycleOwner.repeatOnLifecycle(Lifecycle.State.STARTED) {
                appDb.bookSourceDao.flowAll()
                    .flowWithLifecycleAndDatabaseChange(
                        viewLifecycleOwner.lifecycle,
                        Lifecycle.State.STARTED,
                        AppDatabase.BOOK_SOURCE_TABLE_NAME
                    )
                    .collect { list ->
                        val counts = linkedMapOf<String, Int>()
                        // 修改说明：统计每个分组内有发现页的源数量（有无发现分类展示）
                        val exploreCounts = linkedMapOf<String, Int>()
                        list.forEach { source ->
                            source.bookSourceGroup
                                ?.splitNotBlank(AppPattern.splitGroupRegex)
                                ?.forEach { g ->
                                    counts[g] = (counts[g] ?: 0) + 1
                                    if (source.hasExploreUrl && source.enabledExplore) {
                                        exploreCounts[g] = (exploreCounts[g] ?: 0) + 1
                                    }
                                }
                        }
                        groupAdapter.setItems(
                            counts.entries.sortedByDescending { it.value }
                                .map { Triple(it.key, it.value, exploreCounts[it.key] ?: 0) }
                        )
                    }
            }
        }
    }

    /**
     * 修改文件路径：SourceGroupBrowseFragment.kt（内部类）
     * 改动说明：书源分组列表适配器，展示分组名 + 源数量 + 发现数量，点击进入分组
     */
    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<Triple<String, Int, Int>, ItemSourceGroupBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemSourceGroupBinding {
            return ItemSourceGroupBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemSourceGroupBinding,
            item: Triple<String, Int, Int>,
            payloads: MutableList<Any>
        ) {
            binding.tvGroupName.text = item.first
            // 修改说明：显示源数量与发现数量，如「12个源 · 发现5」
            binding.tvGroupCount.text = if (item.third > 0) {
                getString(R.string.source_group_count, item.second) + " · 发现${item.third}"
            } else {
                getString(R.string.source_group_count, item.second)
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemSourceGroupBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    (activity as? CallBack)?.onGroupBrowseSelect(it.first)
                }
            }
        }
    }

}
