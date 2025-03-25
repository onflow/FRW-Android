package com.flowfoundation.wallet.utils.debug.fragments.debugViewer

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Point
import android.net.Uri
import android.os.Bundle
import android.util.Log
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.core.content.FileProvider
import androidx.core.view.isVisible
import androidx.databinding.DataBindingUtil
import androidx.fragment.app.Fragment
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModelProvider
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.flowfoundation.wallet.R
import com.flowfoundation.wallet.databinding.DebugViewerRvBinding
import com.flowfoundation.wallet.databinding.FragmentDebugViewerBinding
import com.flowfoundation.wallet.utils.debug.DebugManager
import com.flowfoundation.wallet.utils.debug.reload
import com.flowfoundation.wallet.utils.debug.toDp
import com.google.android.material.tabs.TabLayoutMediator
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileOutputStream
import java.io.OutputStreamWriter
import kotlin.math.abs
import kotlin.math.max

private val mainScope = CoroutineScope(Dispatchers.Main)

@SuppressLint("ClickableViewAccessibility")
class DebugViewerFragment : Fragment() {

    private lateinit var binding: FragmentDebugViewerBinding
    private lateinit var viewModel: DebugViewerViewModel

    override fun onCreateView(
        inflater: LayoutInflater,
        container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View {
        binding = FragmentDebugViewerBinding.inflate(inflater, container, false)
        return binding.root
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        viewModel = ViewModelProvider(this)[DebugViewerViewModel::class.java]

        val debugViewer = binding.debugEventsView
        val debugDragListener = View.OnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.startX = debugViewer.x
                    viewModel.startY = debugViewer.y
                    viewModel.dX = debugViewer.x - motionEvent.rawX
                    viewModel.dY = debugViewer.y - motionEvent.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    debugViewer.x = motionEvent.rawX + viewModel.dX
                    debugViewer.y = motionEvent.rawY + viewModel.dY
                }

                MotionEvent.ACTION_UP -> {
                    if (abs(debugViewer.x - viewModel.startX) < 10 &&
                        abs(debugViewer.y - viewModel.startY) < 10
                    ) {
                        toggleCollapse()
                    }
                    viewModel.position = Point(debugViewer.x.toInt(), debugViewer.y.toInt())
                }

                else -> {}
            }
            true
        }
        binding.collapseButton.setOnTouchListener(debugDragListener)

        val resizeDragListener = View.OnTouchListener { _, motionEvent ->
            when (motionEvent.action) {
                MotionEvent.ACTION_DOWN -> {
                    viewModel.resizeDX = debugViewer.width.toFloat() - motionEvent.rawX
                    viewModel.resizeDY = debugViewer.height.toFloat() - motionEvent.rawY
                }

                MotionEvent.ACTION_MOVE -> {
                    val params = debugViewer.layoutParams
                    params?.width = max((motionEvent.rawX + viewModel.resizeDX).toInt(), 300.toDp())
                    params?.height =
                        max((motionEvent.rawY + viewModel.resizeDY).toInt(), 300.toDp())
                    debugViewer.layoutParams = params
                }

                MotionEvent.ACTION_UP -> {
                    viewModel.size = Point(debugViewer.width, debugViewer.height)
                }

                else -> {}
            }
            true
        }
        binding.resizeButton.setOnTouchListener(resizeDragListener)
        updateLayout()
        updateTabs()
        DebugViewerDataSource.list.observe(this.viewLifecycleOwner) {
            binding.viewPager.reload()
        }
        binding.tvClear.setOnClickListener {
            DebugViewerDataSource.clear()
        }
    }

    private fun toggleCollapse() {
        viewModel.collapsed = !viewModel.collapsed
        updateLayout()
    }

    private fun updateLayout() {
        viewModel.position?.let { position ->
            binding.debugEventsView.x = position.x.toFloat()
            binding.debugEventsView.y = position.y.toFloat()
        }

        binding.debugEventsView.let {
            val layoutParams = it.layoutParams
            if (viewModel.collapsed) {
                layoutParams.width = 36.toDp()
                layoutParams.height = 36.toDp()
            } else {
                viewModel.size?.let { size ->
                    layoutParams.width = size.x
                    layoutParams.height = size.y
                } ?: run {
                    layoutParams.width = 300.toDp()
                    layoutParams.height = 400.toDp()
                }
            }
            it.layoutParams = layoutParams
        }

        binding.resizeButton.isVisible = !viewModel.collapsed
    }

    private fun updateTabs() {
        val currentItem = binding.viewPager.currentItem
        binding.viewPager.adapter = DebugViewerTabAdapter()
        TabLayoutMediator(
            binding.tabLayout,
            binding.viewPager
        ) { tab, position ->
            tab.text = DebugViewerDataSource.categories[position].name
        }.attach()
        binding.viewPager.setCurrentItem(currentItem, false)
    }
}

class DebugViewerTabAdapter : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
    init {
        setHasStableIds(true)
    }

    override fun getItemCount() = DebugViewerDataSource.categories.count()

    override fun getItemId(position: Int): Long {
        return DebugViewerDataSource.categories[position].name.hashCode().toLong()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
        val layoutInflater = LayoutInflater.from(parent.context)
        val viewBinding: DebugViewerRvBinding = DataBindingUtil.inflate(
            layoutInflater,
            R.layout.debug_viewer_rv, parent, false
        )
        return DebugViewerTab(viewBinding)
    }

    override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
        (holder as? DebugViewerTab)?.bind(DebugViewerDataSource.categories[position])
    }
}

class DebugViewerTab(private val binding: DebugViewerRvBinding) :
    RecyclerView.ViewHolder(binding.root) {
    init {
        binding.recyclerView.apply {
            layoutManager =
                LinearLayoutManager(DebugManager.applicationContext, RecyclerView.VERTICAL, false)
            adapter = DebugAdapter(listOf())
        }
    }

    fun bind(category: DebugMessageCategory) {
        binding.recyclerView.apply {
            val messages = DebugViewerDataSource.messages(category)
            (adapter as? DebugAdapter)?.list = messages
            this.reload()
            scrollToPosition(messages.count() - 1)
        }
    }
}

enum class DebugMessageCategory {
    LOG,
    ERROR,
}

data class DebugMessage(
    val title: String,
    val body: String,
    val titleColor: Int = R.color.text_1,
    val bodyColor: Int = R.color.text_2,
    var collapsed: Boolean = true
)

object DebugViewerDataSource {
    val list = MutableLiveData<Map<DebugMessageCategory, List<DebugMessage>>>()

    val categories: List<DebugMessageCategory>
        get() {
            return list.value?.keys?.toList()?.sorted() ?: listOf()
        }

    fun messages(category: DebugMessageCategory): List<DebugMessage> {
        return list.value?.get(category) ?: listOf()
    }

    private fun append(category: DebugMessageCategory, message: DebugMessage) {
        if (!DebugManager.isProductionBuild) {
            mainScope.launch {
                val map = list.value?.toMutableMap() ?: mutableMapOf()
                val messages = map[category]?.toMutableList() ?: mutableListOf()
                messages.add(message)
                map[category] = messages
                list.value = map
            }
        }
    }

    fun clear() {
        list.value = emptyMap()
    }

    private fun textColor(priority: Int): Int {
        return when (priority) {
            Log.ERROR -> R.color.accent_red
            Log.WARN -> R.color.accent_orange
            else -> R.color.text_1
        }
    }

    fun log(priority: Int, tag: String?, message: String) {
        if (!DebugManager.isProductionBuild) {
            if (priority != Log.DEBUG) {
                append(
                    DebugMessageCategory.LOG,
                    DebugMessage(
                        tag ?: "",
                        message,
                        textColor(priority)
                    )
                )
            }
        }
    }

    fun error(title: String, message: String) {
        append(
            DebugMessageCategory.ERROR,
            DebugMessage(
                title,
                message,
                textColor(Log.ERROR)
            )
        )
    }

    fun exportDebugMessagesAndShare(context: Context) {
        val debugMessage = list.value ?: return
        val stringBuilder = StringBuilder()
        debugMessage.forEach { (category, messages) ->
            stringBuilder.append("Category: ${category.name}\n")
            messages.forEach { message ->
                stringBuilder.append("Title: ${message.title}\n")
                stringBuilder.append("Body: ${message.body}\n")
            }
        }

        val fileName = "debug_messages.txt"
        val file = File(context.cacheDir, fileName)
        FileOutputStream(file).use { fos ->
            OutputStreamWriter(fos).use { writer ->
                writer.write(stringBuilder.toString())
            }
        }

        val fileUri: Uri = FileProvider.getUriForFile(
            context,
            "${context.packageName}.provider",
            file
        )

        val shareIntent = Intent(Intent.ACTION_SEND).apply {
            type = "text/plain"
            putExtra(Intent.EXTRA_STREAM, fileUri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }

        context.startActivity(Intent.createChooser(shareIntent, "Share Debug Messages"))
    }
}
