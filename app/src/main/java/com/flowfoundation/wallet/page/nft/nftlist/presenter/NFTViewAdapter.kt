import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.ImageView
import android.widget.TextView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.DrawableCompat
import com.flowfoundation.wallet.R

class NFTViewAdapter(
    private val context: Context,
    private var selectedIndex: Int = 0
) : BaseAdapter() {

    private val HEADER_TYPE = 0
    private val ITEM_TYPE = 1

    private val headerText = "View As"
    private val items = listOf(
        Pair(R.drawable.ic_list_view, "List"),
        Pair(R.drawable.ic_grid_view, "Grid")
    )

    // Total count is header (1) + items
    override fun getCount(): Int = items.size + 1

    override fun getItem(position: Int): Any {
        return if (position == 0) headerText else items[position - 1]
    }

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getViewTypeCount(): Int = 2

    override fun getItemViewType(position: Int): Int =
        if (position == 0) HEADER_TYPE else ITEM_TYPE

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        return if (getItemViewType(position) == HEADER_TYPE) {
            // Inflate header layout
            val headerView = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.header_layout, parent, false)
            val headerTextView = headerView.findViewById<TextView>(R.id.headerTextView)
            headerTextView.text = headerText
            headerView
        } else {
            // Inflate regular item view (note: adjust index because 0 is header)
            val view = convertView ?: LayoutInflater.from(context)
                .inflate(R.layout.custom_menu_item, parent, false)
            val icon = view.findViewById<ImageView>(R.id.icon)
            val title = view.findViewById<TextView>(R.id.title)

            val (iconRes, text) = items[position - 1]
            icon.setImageResource(iconRes)
            title.text = text

            val tintColor = if (position - 1 == selectedIndex)
                ContextCompat.getColor(context, R.color.accent_green)
            else
                ContextCompat.getColor(context, R.color.text_light)

            val wrappedDrawable = DrawableCompat.wrap(icon.drawable).mutate()
            DrawableCompat.setTint(wrappedDrawable, tintColor)
            icon.setImageDrawable(wrappedDrawable)
            title.setTextColor(tintColor)

            view
        }
    }

    fun setSelectedIndex(index: Int) {
        selectedIndex = index
        notifyDataSetChanged()
    }
}
