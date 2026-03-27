package com.rvk.studio.ui.explorer

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.rvk.studio.R
import java.io.File

data class FileNode(
    val file: File,
    val depth: Int = 0,
    var isExpanded: Boolean = false,
    var children: List<FileNode>? = null
)

class FileTreeAdapter(
    private val onFileClick: (File) -> Unit,
    private val onFileLongClick: (File) -> Unit
) : RecyclerView.Adapter<FileTreeAdapter.ViewHolder>() {

    private val nodes = mutableListOf<FileNode>()
    private var rootDir: File? = null

    class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val expandArrow: ImageView = view.findViewById(R.id.ivExpandArrow)
        val fileIcon: ImageView = view.findViewById(R.id.ivFileIcon)
        val fileName: TextView = view.findViewById(R.id.tvFileName)
    }

    fun setRootDirectory(dir: File) {
        rootDir = dir
        nodes.clear()
        if (dir.exists() && dir.isDirectory) {
            val sortedChildren = getSortedChildren(dir)
            for (child in sortedChildren) {
                nodes.add(FileNode(child, 0))
            }
        }
        notifyDataSetChanged()
    }

    fun refresh() {
        rootDir?.let { setRootDirectory(it) }
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_file_tree, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val node = nodes[position]
        val file = node.file

        // Set indentation
        val paddingStart = 12 + (node.depth * 20)
        holder.itemView.setPadding(paddingStart, holder.itemView.paddingTop,
            holder.itemView.paddingRight, holder.itemView.paddingBottom)

        // Set file name
        holder.fileName.text = file.name

        // Set icon and expand arrow based on file type
        if (file.isDirectory) {
            holder.expandArrow.visibility = View.VISIBLE
            holder.expandArrow.rotation = if (node.isExpanded) 0f else -90f
            holder.fileIcon.setImageResource(android.R.drawable.ic_menu_sort_by_size)

            // Color based on directory type
            val tintColor = when {
                file.name == "src" || file.name == "main" -> 0xFF4A88C7.toInt()
                file.name == "java" || file.name == "kotlin" -> 0xFF7B68EE.toInt()
                file.name == "res" || file.name == "resources" -> 0xFF4CAF50.toInt()
                file.name == "build" || file.name == "gradle" -> 0xFFFFA726.toInt()
                file.name == "test" || file.name == "tests" -> 0xFF66BB6A.toInt()
                else -> 0xFFA9B7C6.toInt()
            }
            holder.fileIcon.setColorFilter(tintColor)
        } else {
            holder.expandArrow.visibility = View.INVISIBLE
            val iconAndColor = getFileIconAndColor(file.name)
            holder.fileIcon.setImageResource(iconAndColor.first)
            holder.fileIcon.setColorFilter(iconAndColor.second)
        }

        // Click handlers
        holder.itemView.setOnClickListener {
            if (file.isDirectory) {
                toggleExpand(position, node)
            } else {
                onFileClick(file)
            }
        }

        holder.itemView.setOnLongClickListener {
            onFileLongClick(file)
            true
        }
    }

    override fun getItemCount() = nodes.size

    private fun toggleExpand(position: Int, node: FileNode) {
        if (node.isExpanded) {
            // Collapse
            node.isExpanded = false
            val removeCount = countChildren(position)
            if (removeCount > 0) {
                nodes.subList(position + 1, position + 1 + removeCount).clear()
                notifyItemRangeRemoved(position + 1, removeCount)
            }
            notifyItemChanged(position)
        } else {
            // Expand
            node.isExpanded = true
            val children = getSortedChildren(node.file)
            val childNodes = children.map { FileNode(it, node.depth + 1) }
            nodes.addAll(position + 1, childNodes)
            notifyItemRangeInserted(position + 1, childNodes.size)
            notifyItemChanged(position)
        }
    }

    private fun countChildren(position: Int): Int {
        val parentDepth = nodes[position].depth
        var count = 0
        for (i in position + 1 until nodes.size) {
            if (nodes[i].depth > parentDepth) {
                count++
            } else {
                break
            }
        }
        return count
    }

    private fun getSortedChildren(dir: File): List<File> {
        return dir.listFiles()
            ?.filter { !it.name.startsWith(".") || it.name == ".gradle" || it.name == ".git" }
            ?.sortedWith(compareBy<File> { !it.isDirectory }.thenBy { it.name.lowercase() })
            ?: emptyList()
    }

    private fun getFileIconAndColor(fileName: String): Pair<Int, Int> {
        return when {
            fileName.endsWith(".kt") || fileName.endsWith(".kts") ->
                android.R.drawable.ic_menu_edit to 0xFF7B68EE.toInt()
            fileName.endsWith(".java") ->
                android.R.drawable.ic_menu_edit to 0xFFE86C00.toInt()
            fileName.endsWith(".xml") ->
                android.R.drawable.ic_menu_edit to 0xFFFF9800.toInt()
            fileName.endsWith(".py") ->
                android.R.drawable.ic_menu_edit to 0xFF3776AB.toInt()
            fileName.endsWith(".js") || fileName.endsWith(".jsx") ->
                android.R.drawable.ic_menu_edit to 0xFFF7DF1E.toInt()
            fileName.endsWith(".ts") || fileName.endsWith(".tsx") ->
                android.R.drawable.ic_menu_edit to 0xFF3178C6.toInt()
            fileName.endsWith(".dart") ->
                android.R.drawable.ic_menu_edit to 0xFF00B4AB.toInt()
            fileName.endsWith(".cpp") || fileName.endsWith(".c") || fileName.endsWith(".h") ->
                android.R.drawable.ic_menu_edit to 0xFF659AD2.toInt()
            fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts") ->
                android.R.drawable.ic_menu_edit to 0xFF02303A.toInt()
            fileName.endsWith(".json") ->
                android.R.drawable.ic_menu_edit to 0xFFCBCB41.toInt()
            fileName.endsWith(".yaml") || fileName.endsWith(".yml") ->
                android.R.drawable.ic_menu_edit to 0xFFCB171E.toInt()
            fileName.endsWith(".md") ->
                android.R.drawable.ic_menu_edit to 0xFF42A5F5.toInt()
            fileName.endsWith(".html") || fileName.endsWith(".css") ->
                android.R.drawable.ic_menu_edit to 0xFFE44D26.toInt()
            fileName.endsWith(".txt") ->
                android.R.drawable.ic_menu_edit to 0xFFA9B7C6.toInt()
            fileName.endsWith(".png") || fileName.endsWith(".jpg") || fileName.endsWith(".svg") ->
                android.R.drawable.ic_menu_gallery to 0xFF4CAF50.toInt()
            fileName.endsWith(".apk") ->
                android.R.drawable.ic_menu_save to 0xFF4CAF50.toInt()
            fileName == "gradlew" || fileName.endsWith(".sh") ->
                android.R.drawable.ic_menu_manage to 0xFF4CAF50.toInt()
            fileName == "Makefile" || fileName == "CMakeLists.txt" ->
                android.R.drawable.ic_menu_manage to 0xFF02303A.toInt()
            else -> android.R.drawable.ic_menu_edit to 0xFFA9B7C6.toInt()
        }
    }
}
