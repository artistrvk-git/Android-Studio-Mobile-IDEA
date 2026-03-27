package com.rvk.studio.ui

import android.content.Intent
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.text.Editable
import android.text.TextWatcher
import android.view.View
import android.view.inputmethod.EditorInfo
import android.widget.*
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.GravityCompat
import androidx.drawerlayout.widget.DrawerLayout
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import com.rvk.studio.R
import com.rvk.studio.engine.build.BuildEngine
import com.rvk.studio.engine.sdk.SdkDownloadService
import com.rvk.studio.storage.StorageManager
import com.rvk.studio.ui.about.AboutActivity
import com.rvk.studio.ui.dialogs.ProjectCreatorActivity
import com.rvk.studio.ui.explorer.FileTreeAdapter
import com.rvk.studio.ui.terminal.TerminalManager
import com.rvk.studio.utils.AutoCompleteProvider
import com.rvk.studio.utils.SyntaxHighlighter
import java.io.File

class MainActivity : AppCompatActivity() {

    // UI Components
    private lateinit var drawerLayout: DrawerLayout
    private lateinit var codeEditor: EditText
    private lateinit var tvLineNumbers: TextView
    private lateinit var tvEmptyState: TextView
    private lateinit var editorLayout: View
    private lateinit var tvTerminalOutput: TextView
    private lateinit var etTerminalInput: EditText
    private lateinit var terminalScrollView: ScrollView
    private lateinit var tvProjectName: TextView
    private lateinit var tvStatusLeft: TextView
    private lateinit var tvStatusRight: TextView
    private lateinit var tabContainer: LinearLayout
    private lateinit var fileTreeRecyclerView: RecyclerView

    // Bottom panel tabs
    private lateinit var tabTerminal: TextView
    private lateinit var tabBuildOutput: TextView
    private lateinit var tabLogcat: TextView

    // Engine components
    private lateinit var terminalManager: TerminalManager
    private lateinit var buildEngine: BuildEngine
    private lateinit var syntaxHighlighter: SyntaxHighlighter
    private lateinit var autoCompleteProvider: AutoCompleteProvider
    private lateinit var fileTreeAdapter: FileTreeAdapter

    // State
    private var currentProjectDir: File? = null
    private var currentFile: File? = null
    private val openFiles = mutableListOf<File>()
    private val fileContents = mutableMapOf<String, String>()
    private var currentBottomTab = "terminal"
    private val buildOutputBuffer = StringBuilder()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        initViews()
        initEngine()
        setupListeners()
        checkPermissions()
    }

    private fun initViews() {
        drawerLayout = findViewById(R.id.drawerLayout)
        codeEditor = findViewById(R.id.codeEditor)
        tvLineNumbers = findViewById(R.id.tvLineNumbers)
        tvEmptyState = findViewById(R.id.tvEmptyState)
        editorLayout = findViewById(R.id.editorLayout)
        tvTerminalOutput = findViewById(R.id.tvTerminalOutput)
        etTerminalInput = findViewById(R.id.etTerminalInput)
        terminalScrollView = findViewById(R.id.terminalScrollView)
        tvProjectName = findViewById(R.id.tvProjectName)
        tvStatusLeft = findViewById(R.id.tvStatusLeft)
        tvStatusRight = findViewById(R.id.tvStatusRight)
        tabContainer = findViewById(R.id.tabContainer)
        fileTreeRecyclerView = findViewById(R.id.fileTreeRecyclerView)
        tabTerminal = findViewById(R.id.tabTerminal)
        tabBuildOutput = findViewById(R.id.tabBuildOutput)
        tabLogcat = findViewById(R.id.tabLogcat)
    }

    private fun initEngine() {
        // Terminal
        terminalManager = TerminalManager(this)
        terminalManager.setListener(object : TerminalManager.TerminalListener {
            override fun onOutput(text: String) {
                runOnUiThread {
                    if (currentBottomTab == "terminal") {
                        appendTerminalOutput(text)
                    }
                }
            }
            override fun onError(text: String) {
                runOnUiThread {
                    if (currentBottomTab == "terminal") {
                        appendTerminalOutput("ERR: $text")
                    }
                }
            }
            override fun onProcessExit(code: Int) {
                runOnUiThread {
                    appendTerminalOutput("Process exited with code: $code")
                }
            }
        })
        terminalManager.startShell()

        // Build Engine
        buildEngine = BuildEngine(this)
        buildEngine.setListener(object : BuildEngine.BuildListener {
            override fun onBuildOutput(line: String) {
                runOnUiThread {
                    buildOutputBuffer.appendLine(line)
                    if (currentBottomTab == "build") {
                        tvTerminalOutput.text = buildOutputBuffer.toString()
                        scrollTerminalToBottom()
                    }
                }
            }
            override fun onBuildProgress(progress: Int, message: String) {
                runOnUiThread {
                    tvStatusLeft.text = "$message ($progress%)"
                }
            }
            override fun onBuildComplete(success: Boolean, apkPath: String?, message: String) {
                runOnUiThread {
                    tvStatusLeft.text = if (success) "Build Successful" else "Build Failed"
                    if (success && apkPath != null) {
                        showBuildSuccessDialog(apkPath)
                    }
                }
            }
        })

        // Syntax Highlighter
        syntaxHighlighter = SyntaxHighlighter(codeEditor)
        codeEditor.addTextChangedListener(syntaxHighlighter.createTextWatcher())

        // Auto Complete
        autoCompleteProvider = AutoCompleteProvider(this)

        // File Tree
        fileTreeAdapter = FileTreeAdapter(
            onFileClick = { file -> openFile(file) },
            onFileLongClick = { file -> showFileContextMenu(file) }
        )
        fileTreeRecyclerView.layoutManager = LinearLayoutManager(this)
        fileTreeRecyclerView.adapter = fileTreeAdapter

        // Line numbers
        codeEditor.addTextChangedListener(object : TextWatcher {
            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}
            override fun afterTextChanged(s: Editable?) {
                updateLineNumbers()
            }
        })
    }

    private fun setupListeners() {
        // Toolbar buttons
        findViewById<ImageButton>(R.id.btnMenu).setOnClickListener {
            drawerLayout.openDrawer(GravityCompat.START)
        }

        findViewById<ImageButton>(R.id.btnRun).setOnClickListener {
            currentProjectDir?.let { buildApk(it) }
                ?: Toast.makeText(this, "No project open", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnBuild).setOnClickListener {
            currentProjectDir?.let { buildApk(it) }
                ?: Toast.makeText(this, "No project open", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnSync).setOnClickListener {
            currentProjectDir?.let { syncGradle(it) }
                ?: Toast.makeText(this, "No project open", Toast.LENGTH_SHORT).show()
        }

        findViewById<ImageButton>(R.id.btnMore).setOnClickListener {
            showMainMenu()
        }

        // Terminal input
        etTerminalInput.setOnEditorActionListener { _, actionId, _ ->
            if (actionId == EditorInfo.IME_ACTION_SEND || actionId == EditorInfo.IME_ACTION_DONE) {
                val command = etTerminalInput.text.toString()
                if (command.isNotBlank()) {
                    terminalManager.executeCommand(command)
                    etTerminalInput.text.clear()
                }
                true
            } else {
                false
            }
        }

        // Bottom panel tabs
        tabTerminal.setOnClickListener { switchBottomTab("terminal") }
        tabBuildOutput.setOnClickListener { switchBottomTab("build") }
        tabLogcat.setOnClickListener { switchBottomTab("logcat") }

        // File tree buttons
        findViewById<ImageButton>(R.id.btnNewFile).setOnClickListener {
            showNewFileDialog()
        }

        findViewById<ImageButton>(R.id.btnRefreshTree).setOnClickListener {
            fileTreeAdapter.refresh()
        }
    }

    private fun checkPermissions() {
        if (!StorageManager.hasStoragePermission(this)) {
            StorageManager.requestStoragePermission(this)
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        when (requestCode) {
            StorageManager.REQUEST_MANAGE_STORAGE -> {
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                    if (Environment.isExternalStorageManager()) {
                        tvStatusLeft.text = "Storage permission granted"
                    } else {
                        Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
                    }
                }
            }
            REQUEST_OPEN_PROJECT -> {
                data?.getStringExtra("project_path")?.let { path ->
                    openProject(File(path))
                }
            }
            REQUEST_CREATE_PROJECT -> {
                data?.getStringExtra("project_path")?.let { path ->
                    openProject(File(path))
                }
            }
        }
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == StorageManager.REQUEST_STORAGE_PERMISSION) {
            if (grantResults.isNotEmpty() && grantResults.all { it == android.content.pm.PackageManager.PERMISSION_GRANTED }) {
                tvStatusLeft.text = "Storage permission granted"
            } else {
                Toast.makeText(this, getString(R.string.permission_required), Toast.LENGTH_LONG).show()
            }
        }
    }

    // === Project Management ===

    fun openProject(projectDir: File) {
        if (!projectDir.exists() || !projectDir.isDirectory) {
            Toast.makeText(this, "Invalid project directory", Toast.LENGTH_SHORT).show()
            return
        }

        currentProjectDir = projectDir
        tvProjectName.text = projectDir.name
        tvStatusLeft.text = "Project: ${projectDir.name}"

        // Set file tree root
        fileTreeAdapter.setRootDirectory(projectDir)

        // Set terminal working directory
        terminalManager.setWorkingDirectory(projectDir)

        // Open drawer to show file tree
        drawerLayout.openDrawer(GravityCompat.START)
    }

    private fun openFile(file: File) {
        if (!file.exists() || file.isDirectory) return

        // Check file size (limit to 2MB)
        if (file.length() > 2 * 1024 * 1024) {
            Toast.makeText(this, "File too large to open in editor", Toast.LENGTH_SHORT).show()
            return
        }

        try {
            val content = file.readText()
            currentFile = file

            // Add to open files if not already open
            if (!openFiles.any { it.absolutePath == file.absolutePath }) {
                openFiles.add(file)
                addEditorTab(file)
            }

            // Show editor
            tvEmptyState.visibility = View.GONE
            editorLayout.visibility = View.VISIBLE

            // Set content
            codeEditor.setText(content)
            fileContents[file.absolutePath] = content

            // Set language for syntax highlighting
            syntaxHighlighter.detectLanguage(file.name)

            // Update status bar
            tvStatusRight.text = "${getFileEncoding(file)} | LF | ${file.name}"
            tvStatusLeft.text = file.absolutePath

            // Highlight active tab
            updateTabHighlights()

            // Close drawer
            drawerLayout.closeDrawer(GravityCompat.START)

        } catch (e: Exception) {
            Toast.makeText(this, "Error opening file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveCurrentFile() {
        val file = currentFile ?: return
        val content = codeEditor.text.toString()

        try {
            file.writeText(content)
            fileContents[file.absolutePath] = content
            tvStatusLeft.text = "Saved: ${file.name}"
            Toast.makeText(this, "Saved: ${file.name}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Toast.makeText(this, "Error saving: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun saveAllFiles() {
        for (file in openFiles) {
            val content = fileContents[file.absolutePath]
            if (content != null) {
                try {
                    file.writeText(content)
                } catch (e: Exception) {
                    // Skip files that can't be saved
                }
            }
        }
        tvStatusLeft.text = "All files saved"
        Toast.makeText(this, "All files saved", Toast.LENGTH_SHORT).show()
    }

    // === Editor Tabs ===

    private fun addEditorTab(file: File) {
        val tabView = layoutInflater.inflate(R.layout.item_editor_tab, tabContainer, false)
        val tvTitle = tabView.findViewById<TextView>(R.id.tvTabTitle)
        val btnClose = tabView.findViewById<ImageButton>(R.id.btnCloseTab)

        tvTitle.text = file.name
        tabView.tag = file.absolutePath

        tabView.setOnClickListener {
            openFile(file)
        }

        btnClose.setOnClickListener {
            closeTab(file)
        }

        tabContainer.addView(tabView)
    }

    private fun closeTab(file: File) {
        // Save content
        val content = codeEditor.text.toString()
        if (currentFile?.absolutePath == file.absolutePath) {
            fileContents[file.absolutePath] = content
        }

        // Remove from lists
        openFiles.removeAll { it.absolutePath == file.absolutePath }
        fileContents.remove(file.absolutePath)

        // Remove tab view
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i)
            if (child.tag == file.absolutePath) {
                tabContainer.removeViewAt(i)
                break
            }
        }

        // Open next file or show empty state
        if (openFiles.isNotEmpty()) {
            openFile(openFiles.last())
        } else {
            currentFile = null
            tvEmptyState.visibility = View.VISIBLE
            editorLayout.visibility = View.GONE
            tvStatusRight.text = "UTF-8 | LF"
        }
    }

    private fun updateTabHighlights() {
        for (i in 0 until tabContainer.childCount) {
            val child = tabContainer.getChildAt(i)
            val isActive = child.tag == currentFile?.absolutePath
            child.setBackgroundColor(
                if (isActive) resources.getColor(R.color.dark_bg_tab_active, theme)
                else resources.getColor(R.color.dark_bg_tab, theme)
            )
        }
    }

    // === Build System ===

    private fun buildApk(projectDir: File) {
        switchBottomTab("build")
        buildOutputBuffer.clear()
        tvTerminalOutput.text = ""

        // Ensure gradlew permissions before build
        val gradlewFile = File(projectDir, "gradlew")
        if (gradlewFile.exists()) {
            StorageManager.ensureExecutable(gradlewFile)
        }

        // Make all scripts executable
        StorageManager.makeExecutableRecursive(projectDir)

        buildEngine.buildApk(projectDir)
    }

    private fun syncGradle(projectDir: File) {
        switchBottomTab("build")
        buildOutputBuffer.clear()
        tvTerminalOutput.text = ""

        val gradlewFile = File(projectDir, "gradlew")
        if (gradlewFile.exists()) {
            StorageManager.ensureExecutable(gradlewFile)
        }

        buildEngine.syncGradle(projectDir)
    }

    private fun showBuildSuccessDialog(apkPath: String) {
        AlertDialog.Builder(this)
            .setTitle("Build Successful")
            .setMessage("APK generated at:\n$apkPath")
            .setPositiveButton("OK", null)
            .setNeutralButton("Share") { _, _ ->
                shareApk(File(apkPath))
            }
            .show()
    }

    private fun shareApk(apkFile: File) {
        try {
            val uri = androidx.core.content.FileProvider.getUriForFile(
                this, "${packageName}.fileprovider", apkFile
            )
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "application/vnd.android.package-archive"
                putExtra(Intent.EXTRA_STREAM, uri)
                addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
            }
            startActivity(Intent.createChooser(intent, "Share APK"))
        } catch (e: Exception) {
            Toast.makeText(this, "Error sharing APK: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }

    // === Bottom Panel ===

    private fun switchBottomTab(tab: String) {
        currentBottomTab = tab

        tabTerminal.setBackgroundColor(
            if (tab == "terminal") resources.getColor(R.color.dark_bg_terminal, theme)
            else resources.getColor(android.R.color.transparent, theme)
        )
        tabTerminal.setTextColor(
            if (tab == "terminal") resources.getColor(R.color.dark_text_primary, theme)
            else resources.getColor(R.color.dark_text_secondary, theme)
        )
        tabBuildOutput.setBackgroundColor(
            if (tab == "build") resources.getColor(R.color.dark_bg_terminal, theme)
            else resources.getColor(android.R.color.transparent, theme)
        )
        tabBuildOutput.setTextColor(
            if (tab == "build") resources.getColor(R.color.dark_text_primary, theme)
            else resources.getColor(R.color.dark_text_secondary, theme)
        )
        tabLogcat.setBackgroundColor(
            if (tab == "logcat") resources.getColor(R.color.dark_bg_terminal, theme)
            else resources.getColor(android.R.color.transparent, theme)
        )
        tabLogcat.setTextColor(
            if (tab == "logcat") resources.getColor(R.color.dark_text_primary, theme)
            else resources.getColor(R.color.dark_text_secondary, theme)
        )

        when (tab) {
            "terminal" -> {
                tvTerminalOutput.text = ""  // Will be populated by terminal manager
                etTerminalInput.visibility = View.VISIBLE
            }
            "build" -> {
                tvTerminalOutput.text = buildOutputBuffer.toString()
                etTerminalInput.visibility = View.GONE
            }
            "logcat" -> {
                tvTerminalOutput.text = "Logcat output will appear here during device testing."
                etTerminalInput.visibility = View.GONE
            }
        }
    }

    private fun appendTerminalOutput(text: String) {
        tvTerminalOutput.append("$text\n")
        scrollTerminalToBottom()
    }

    private fun scrollTerminalToBottom() {
        terminalScrollView.post {
            terminalScrollView.fullScroll(ScrollView.FOCUS_DOWN)
        }
    }

    // === Line Numbers ===

    private fun updateLineNumbers() {
        val text = codeEditor.text.toString()
        val lineCount = text.count { it == '\n' } + 1
        val lineNumbers = StringBuilder()
        for (i in 1..lineCount) {
            lineNumbers.appendLine(i.toString())
        }
        tvLineNumbers.text = lineNumbers.toString()
    }

    // === Menu ===

    private fun showMainMenu() {
        val popup = PopupMenu(this, findViewById(R.id.btnMore))
        popup.menuInflater.inflate(R.menu.main_menu, popup.menu)
        popup.setOnMenuItemClickListener { item ->
            when (item.itemId) {
                R.id.menu_new_project -> {
                    val intent = Intent(this, ProjectCreatorActivity::class.java)
                    startActivityForResult(intent, REQUEST_CREATE_PROJECT)
                    true
                }
                R.id.menu_open_project -> {
                    showOpenProjectDialog()
                    true
                }
                R.id.menu_save -> {
                    saveCurrentFile()
                    true
                }
                R.id.menu_save_all -> {
                    saveAllFiles()
                    true
                }
                R.id.menu_build_apk -> {
                    currentProjectDir?.let { buildApk(it) }
                        ?: Toast.makeText(this, "No project open", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_clean_build -> {
                    currentProjectDir?.let { dir ->
                        switchBottomTab("build")
                        buildOutputBuffer.clear()
                        StorageManager.makeExecutableRecursive(dir)
                        buildEngine.cleanBuild(dir)
                    }
                    true
                }
                R.id.menu_sync_gradle -> {
                    currentProjectDir?.let { syncGradle(it) }
                    true
                }
                R.id.menu_sdk_manager -> {
                    SdkDownloadService.startDownloadAll(this)
                    Toast.makeText(this, "SDK download started", Toast.LENGTH_SHORT).show()
                    true
                }
                R.id.menu_settings -> {
                    startActivity(Intent(this, SettingsActivity::class.java))
                    true
                }
                R.id.menu_about -> {
                    startActivity(Intent(this, com.rvk.studio.ui.about.AboutActivity::class.java))
                    true
                }
                else -> false
            }
        }
        popup.show()
    }

    private fun showOpenProjectDialog() {
        val projectsDir = StorageManager.getProjectsDir(this)
        val projects = projectsDir.listFiles()?.filter { it.isDirectory }?.map { it.name }?.toTypedArray()

        if (projects.isNullOrEmpty()) {
            Toast.makeText(this, "No projects found. Create a new project first.", Toast.LENGTH_SHORT).show()
            return
        }

        AlertDialog.Builder(this)
            .setTitle("Open Project")
            .setItems(projects) { _, which ->
                val projectDir = File(projectsDir, projects[which])
                openProject(projectDir)
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showNewFileDialog() {
        val projectDir = currentProjectDir
        if (projectDir == null) {
            Toast.makeText(this, "Open a project first", Toast.LENGTH_SHORT).show()
            return
        }

        val editText = EditText(this).apply {
            hint = "File name (e.g., MyClass.kt)"
            setPadding(48, 24, 48, 24)
        }

        AlertDialog.Builder(this)
            .setTitle("New File")
            .setView(editText)
            .setPositiveButton("Create") { _, _ ->
                val fileName = editText.text.toString().trim()
                if (fileName.isNotEmpty()) {
                    val newFile = File(projectDir, fileName)
                    try {
                        newFile.parentFile?.mkdirs()
                        newFile.createNewFile()
                        fileTreeAdapter.refresh()
                        openFile(newFile)
                    } catch (e: Exception) {
                        Toast.makeText(this, "Error creating file: ${e.message}", Toast.LENGTH_SHORT).show()
                    }
                }
            }
            .setNegativeButton("Cancel", null)
            .show()
    }

    private fun showFileContextMenu(file: File) {
        val options = if (file.isDirectory) {
            arrayOf("New File Here", "New Folder Here", "Delete", "Rename")
        } else {
            arrayOf("Open", "Delete", "Rename", "Copy Path")
        }

        AlertDialog.Builder(this)
            .setTitle(file.name)
            .setItems(options) { _, which ->
                when (options[which]) {
                    "Open" -> openFile(file)
                    "Delete" -> {
                        AlertDialog.Builder(this)
                            .setTitle("Delete ${file.name}?")
                            .setMessage("This action cannot be undone.")
                            .setPositiveButton("Delete") { _, _ ->
                                if (file.isDirectory) file.deleteRecursively() else file.delete()
                                fileTreeAdapter.refresh()
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    "Rename" -> {
                        val editText = EditText(this).apply {
                            setText(file.name)
                            setPadding(48, 24, 48, 24)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Rename")
                            .setView(editText)
                            .setPositiveButton("Rename") { _, _ ->
                                val newName = editText.text.toString().trim()
                                if (newName.isNotEmpty()) {
                                    val newFile = File(file.parentFile, newName)
                                    file.renameTo(newFile)
                                    fileTreeAdapter.refresh()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    "Copy Path" -> {
                        val clipboard = getSystemService(CLIPBOARD_SERVICE) as android.content.ClipboardManager
                        clipboard.setPrimaryClip(android.content.ClipData.newPlainText("path", file.absolutePath))
                        Toast.makeText(this, "Path copied", Toast.LENGTH_SHORT).show()
                    }
                    "New File Here" -> {
                        val editText = EditText(this).apply {
                            hint = "File name"
                            setPadding(48, 24, 48, 24)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("New File")
                            .setView(editText)
                            .setPositiveButton("Create") { _, _ ->
                                val name = editText.text.toString().trim()
                                if (name.isNotEmpty()) {
                                    File(file, name).createNewFile()
                                    fileTreeAdapter.refresh()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                    "New Folder Here" -> {
                        val editText = EditText(this).apply {
                            hint = "Folder name"
                            setPadding(48, 24, 48, 24)
                        }
                        AlertDialog.Builder(this)
                            .setTitle("New Folder")
                            .setView(editText)
                            .setPositiveButton("Create") { _, _ ->
                                val name = editText.text.toString().trim()
                                if (name.isNotEmpty()) {
                                    File(file, name).mkdirs()
                                    fileTreeAdapter.refresh()
                                }
                            }
                            .setNegativeButton("Cancel", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun getFileEncoding(file: File): String = "UTF-8"

    override fun onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START)
        } else {
            super.onBackPressed()
        }
    }

    override fun onDestroy() {
        terminalManager.destroy()
        super.onDestroy()
    }

    companion object {
        const val REQUEST_OPEN_PROJECT = 2001
        const val REQUEST_CREATE_PROJECT = 2002
    }
}
