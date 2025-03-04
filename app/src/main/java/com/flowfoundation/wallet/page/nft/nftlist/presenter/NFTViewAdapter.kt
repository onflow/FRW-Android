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

    private val items = listOf(
        Pair(R.drawable.ic_list_view, "List"),
        Pair(R.drawable.ic_grid_view, "Grid")
    )

    override fun getCount(): Int = items.size

    override fun getItem(position: Int): Any = items[position]

    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: LayoutInflater.from(context)
            .inflate(R.layout.custom_menu_item, parent, false)
        val icon = view.findViewById<ImageView>(R.id.icon)
        val title = view.findViewById<TextView>(R.id.title)

        val (iconRes, text) = items[position]
        icon.setImageResource(iconRes)
        title.text = text

        val tintColor = if (position == selectedIndex)
            ContextCompat.getColor(context, R.color.accent_green)
        else
            ContextCompat.getColor(context, R.color.text_light)

        val wrappedDrawable = DrawableCompat.wrap(icon.drawable).mutate()
        DrawableCompat.setTint(wrappedDrawable, tintColor)
        icon.setImageDrawable(wrappedDrawable)

        title.setTextColor(tintColor)

        return view
    }


    fun setSelectedIndex(index: Int) {
        selectedIndex = index
        notifyDataSetChanged()
    }
}
