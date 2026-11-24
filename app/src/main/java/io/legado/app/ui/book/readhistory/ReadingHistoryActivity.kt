package io.legado.app.ui.book.readhistory

import android.content.Context
import android.os.Bundle
import android.view.ViewGroup
import androidx.core.view.isGone
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseActivity
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.AppDatabase
import io.legado.app.data.appDb
import io.legado.app.data.entities.Book
import io.legado.app.databinding.ActivityReadHistoryBinding
import io.legado.app.databinding.ItemReadHistoryBinding
import io.legado.app.help.config.AppConfig
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.flowWithLifecycleAndDatabaseChange
import io.legado.app.utils.setEdgeEffectColor
import io.legado.app.utils.startActivityForBook
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * 修改文件路径：app/src/main/java/io/legado/app/ui/book/readhistory/ReadingHistoryActivity.kt（新增）
 * 功能说明：阅读历史页面
 * 1.按最近阅读时间倒序展示书架中读过的书籍（封面 + 书名 + 作者 + 最近阅读时间）
 * 2.点击书籍直接进入阅读
 * 3.数据源复用 bookDao.flowAll()（本身就是按 durChapterTime 降序查询）
 */
class ReadingHistoryActivity : BaseActivity<ActivityReadHistoryBinding>() {

    override val binding by viewBinding(ActivityReadHistoryBinding::inflate)
    private val adapter by lazy { HistoryAdapter(this) }

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        binding.titleBar.setBackgroundColor(primaryColor)
        binding.recyclerView.setEdgeEffectColor(primaryColor)
        binding.recyclerView.layoutManager = LinearLayoutManager(this)
        binding.recyclerView.adapter = adapter
        initData()
    }

    private fun initData() {
        lifecycleScope.launch {
            appDb.bookDao.flowAll()
                .flowWithLifecycleAndDatabaseChange(
                    lifecycle,
                    Lifecycle.State.RESUMED,
                    AppDatabase.BOOK_TABLE_NAME
                )
                .map { list ->
                    // 只保留读过（durChapterTime > 0）的书，并保持最近阅读时间倒序
                    list.filter { it.durChapterTime > 0 }
                }
                .catch {
                    it.printStackTrace()
                }
                .conflate()
                .flowOn(Dispatchers.Default)
                .collect { list ->
                    adapter.setItems(list)
                    binding.tvEmptyMsg.isGone = list.isNotEmpty()
                }
        }
    }

    /**
     * 修改文件路径：ReadingHistoryActivity.kt（内部类）
     * 改动说明：阅读历史列表适配器
     */
    inner class HistoryAdapter(context: Context) :
        RecyclerAdapter<Book, ItemReadHistoryBinding>(context) {

        private val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())

        override fun getViewBinding(parent: ViewGroup): ItemReadHistoryBinding {
            return ItemReadHistoryBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemReadHistoryBinding,
            item: Book,
            payloads: MutableList<Any>
        ) {
            binding.run {
                tvName.text = item.name
                tvAuthor.text = item.author
                tvLastRead.text = getString(R.string.last_read_time, dateFormat.format(Date(item.durChapterTime)))
                ivCover.load(item, AppConfig.loadCoverOnlyWifi)
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemReadHistoryBinding) {
            binding.root.setOnClickListener {
                getItem(holder.layoutPosition)?.let {
                    startActivityForBook(it)
                }
            }
        }
    }

}
