package io.legado.app.ui.book.source.manage

import android.content.Context
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import androidx.appcompat.widget.Toolbar
import androidx.fragment.app.viewModels
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import io.legado.app.R
import io.legado.app.base.BaseDialogFragment
import io.legado.app.base.adapter.ItemViewHolder
import io.legado.app.base.adapter.RecyclerAdapter
import io.legado.app.data.appDb
import io.legado.app.databinding.DialogBookGroupPickerBinding
import io.legado.app.databinding.ItemGroupSelectBinding
import io.legado.app.lib.theme.accentColor
import io.legado.app.lib.theme.backgroundColor
import io.legado.app.lib.theme.primaryColor
import io.legado.app.utils.applyTint
import io.legado.app.utils.setLayout
import io.legado.app.utils.viewbindingdelegate.viewBinding
import kotlinx.coroutines.flow.conflate
import kotlinx.coroutines.launch

/**
 * 修改文件路径：app/src/main/java/io/legado/app/ui/book/source/manage/SourceGroupSelectDialog.kt（新增）
 * 功能说明：书源「更改分组」对话框
 * 1.列出当前所有书源分组，勾选的书源将归入勾选的分组（替换原有分组）
 * 2.工具栏「添加」按钮可新建分组
 * 3.复用书籍分组选择的布局 dialog_book_group_picker.xml 与 item_group_select.xml
 */
class SourceGroupSelectDialog() : BaseDialogFragment(R.layout.dialog_book_group_picker),
    Toolbar.OnMenuItemClickListener {

    constructor(bookSourceUrl: String) : this() {
        arguments = Bundle().apply {
            putString("bookSourceUrl", bookSourceUrl)
        }
    }

    private val binding by viewBinding(DialogBookGroupPickerBinding::bind)
    private val viewModel: BookSourceViewModel by viewModels()
    private val adapter by lazy { GroupAdapter(requireContext()) }
    private var bookSourceUrl: String = ""
    // 当前书源已归属的分组集合
    private val selectedGroups = linkedSetOf<String>()

    override fun onStart() {
        super.onStart()
        setLayout(0.8f, 0.8f)
    }

    override fun onFragmentCreated(view: View, savedInstanceState: Bundle?) {
        binding.toolBar.setBackgroundColor(primaryColor)
        binding.toolBar.title = getString(R.string.source_group_select)
        binding.toolBar.inflateMenu(R.menu.book_group_manage)
        binding.toolBar.menu.applyTint(requireContext())
        binding.toolBar.setOnMenuItemClickListener(this)
        binding.recyclerView.layoutManager = LinearLayoutManager(requireContext())
        binding.recyclerView.adapter = adapter
        binding.tvCancel.setOnClickListener {
            dismissAllowingStateLoss()
        }
        binding.tvOk.setTextColor(requireContext().accentColor)
        binding.tvOk.setOnClickListener {
            // 确定后把书源分组替换为勾选的分组（逗号分隔存储）
            viewModel.updateGroups(bookSourceUrl, selectedGroups.toList())
            dismissAllowingStateLoss()
        }
        arguments?.let {
            bookSourceUrl = it.getString("bookSourceUrl") ?: ""
        }
        initData()
    }

    private fun initData() {
        viewLifecycleOwner.lifecycleScope.launch {
            // 先读取书源当前分组，用于勾选状态回显
            val source = appDb.bookSourceDao.getBookSource(bookSourceUrl)
            source?.bookSourceGroup?.split(",")?.forEach {
                if (it.isNotBlank()) selectedGroups.add(it)
            }
            // 监听所有书源分组名，实时刷新列表
            appDb.bookSourceDao.flowGroups().conflate().collect {
                adapter.setItems(it)
            }
        }
    }

    override fun onMenuItemClick(item: android.view.MenuItem?): Boolean {
        when (item?.itemId) {
            R.id.menu_add -> showNewGroupDialog()
        }
        return true
    }

    /**
     * 新建分组：弹出输入框，输入分组名后加入列表并勾选
     */
    private fun showNewGroupDialog() {
        val editText = EditText(requireContext()).apply {
            setSingleLine()
            setHint(R.string.new_source_group)
        }
        androidx.appcompat.app.AlertDialog.Builder(requireContext())
            .setTitle(R.string.new_source_group)
            .setView(editText)
            .setPositiveButton(R.string.ok) { _, _ ->
                val name = editText.text?.toString()?.trim() ?: ""
                if (name.isNotEmpty()) {
                    if (selectedGroups.add(name)) {
                        adapter.addItems(0, listOf(name))
                    }
                }
            }
            .setNegativeButton(R.string.cancel, null)
            .show()
    }

    private inner class GroupAdapter(context: Context) :
        RecyclerAdapter<String, ItemGroupSelectBinding>(context) {

        override fun getViewBinding(parent: ViewGroup): ItemGroupSelectBinding {
            return ItemGroupSelectBinding.inflate(inflater, parent, false)
        }

        override fun convert(
            holder: ItemViewHolder,
            binding: ItemGroupSelectBinding,
            item: String,
            payloads: MutableList<Any>
        ) {
            binding.run {
                root.setBackgroundColor(context.backgroundColor)
                cbGroup.text = item
                cbGroup.isChecked = selectedGroups.contains(item)
                // 书源分组名编辑走「分组管理」菜单，这里隐藏编辑按钮
                tvEdit.visibility = View.GONE
            }
        }

        override fun registerListener(holder: ItemViewHolder, binding: ItemGroupSelectBinding) {
            binding.cbGroup.setOnUserCheckedChangeListener { checked ->
                getItem(holder.layoutPosition)?.let {
                    if (checked) {
                        selectedGroups.add(it)
                    } else {
                        selectedGroups.remove(it)
                    }
                }
            }
        }

    }

}
