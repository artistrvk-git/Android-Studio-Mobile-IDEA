package com.rvk.studio.ui.terminal

import android.content.Context
import android.util.Log
import com.rvk.studio.storage.StorageManager
import kotlinx.coroutines.*
import java.io.BufferedReader
import java.io.File
import java.io.InputStreamReader
import java.io.OutputStream

class TerminalManager(private val context: Context) {

    companion object {
        const val TAG = "TerminalManager"
    }

    interface TerminalListener {
        fun onOutput(text: String)
        fun onError(text: String)
        fun onProcessExit(code: Int)
    }

    private var listener: TerminalListener? = null
    private var shellProcess: Process? = null
    private var shellOutputStream: OutputStream? = null
    private var outputJob: Job? = null
    private var errorJob: Job? = null
    private var workingDir: File? = null
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val commandHistory = mutableListOf<String>()
    private var historyIndex = -1

    fun setListener(l: TerminalListener?) {
        listener = l
    }

    fun setWorkingDirectory(dir: File) {
        workingDir = dir
    }

    fun startShell() {
        scope.launch {
            try {
                val env = buildEnvironment()
                val pb = ProcessBuilder("sh")
                pb.directory(workingDir ?: StorageManager.getProjectsDir(context))
                pb.redirectErrorStream(false)
                pb.environment().putAll(env)

                shellProcess = pb.start()
                shellOutputStream = shellProcess!!.outputStream

                // Read stdout
                outputJob = launch {
                    try {
                        val reader = BufferedReader(InputStreamReader(shellProcess!!.inputStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            withContext(Dispatchers.Main) {
                                listener?.onOutput(line!!)
                            }
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            Log.e(TAG, "Output reading error", e)
                        }
                    }
                }

                // Read stderr
                errorJob = launch {
                    try {
                        val reader = BufferedReader(InputStreamReader(shellProcess!!.errorStream))
                        var line: String?
                        while (reader.readLine().also { line = it } != null) {
                            withContext(Dispatchers.Main) {
                                listener?.onError(line!!)
                            }
                        }
                    } catch (e: Exception) {
                        if (e !is CancellationException) {
                            Log.e(TAG, "Error reading error", e)
                        }
                    }
                }

                // Wait for process exit
                launch {
                    val exitCode = shellProcess!!.waitFor()
                    withContext(Dispatchers.Main) {
                        listener?.onProcessExit(exitCode)
                    }
                }

                withContext(Dispatchers.Main) {
                    listener?.onOutput("Android Studio Mobile Terminal")
                    listener?.onOutput("Shell started at: ${workingDir?.absolutePath ?: "~"}")
                    listener?.onOutput("Type 'help' for available commands.")
                    listener?.onOutput("")
                }

            } catch (e: Exception) {
                Log.e(TAG, "Failed to start shell", e)
                withContext(Dispatchers.Main) {
                    listener?.onError("Failed to start shell: ${e.message}")
                }
            }
        }
    }

    fun executeCommand(command: String) {
        if (command.isBlank()) return

        commandHistory.add(command)
        historyIndex = commandHistory.size

        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    listener?.onOutput("$ $command")
                }

                // Handle built-in commands
                when {
                    command == "help" -> {
                        showHelp()
                        return@launch
                    }
                    command.startsWith("cd ") -> {
                        handleCd(command.removePrefix("cd ").trim())
                        return@launch
                    }
                    command == "pwd" -> {
                        withContext(Dispatchers.Main) {
                            listener?.onOutput(workingDir?.absolutePath ?: "Unknown")
                        }
                        return@launch
                    }
                    command == "clear" -> {
                        withContext(Dispatchers.Main) {
                            listener?.onOutput("\u001B[2J\u001B[H")
                        }
                        return@launch
                    }
                    command.startsWith("chmod ") -> {
                        handleChmod(command)
                    }
                }

                // Send to shell process
                if (shellProcess != null && shellProcess!!.isAlive) {
                    shellOutputStream?.write("$command\n".toByteArray())
                    shellOutputStream?.flush()
                } else {
                    // Execute as standalone command
                    executeStandalone(command)
                }

            } catch (e: Exception) {
                Log.e(TAG, "Command execution error", e)
                withContext(Dispatchers.Main) {
                    listener?.onError("Error: ${e.message}")
                }
            }
        }
    }

    private suspend fun executeStandalone(command: String) {
        withContext(Dispatchers.IO) {
            try {
                val env = buildEnvironment()
                val pb = ProcessBuilder("sh", "-c", command)
                pb.directory(workingDir ?: StorageManager.getProjectsDir(context))
                pb.redirectErrorStream(true)
                pb.environment().putAll(env)

                val process = pb.start()
                val reader = BufferedReader(InputStreamReader(process.inputStream))
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    withContext(Dispatchers.Main) {
                        listener?.onOutput(line!!)
                    }
                }

                val exitCode = process.waitFor()
                if (exitCode != 0) {
                    withContext(Dispatchers.Main) {
                        listener?.onOutput("Process exited with code: $exitCode")
                    }
                } else {
                    // Success - no additional output needed
                }
            } catch (e: Exception) {
                withContext(Dispatchers.Main) {
                    listener?.onError("Error: ${e.message}")
                }
            }
        }
    }

    private suspend fun handleCd(path: String) {
        val newDir = if (path.startsWith("/")) {
            File(path)
        } else if (path == "~" || path == "") {
            StorageManager.getProjectsDir(context)
        } else if (path == "..") {
            workingDir?.parentFile ?: workingDir
        } else {
            File(workingDir ?: StorageManager.getProjectsDir(context), path)
        }

        if (newDir != null && newDir.exists() && newDir.isDirectory) {
            workingDir = newDir
            withContext(Dispatchers.Main) {
                listener?.onOutput(newDir.absolutePath)
            }
        } else {
            withContext(Dispatchers.Main) {
                listener?.onError("cd: $path: No such directory")
            }
        }
    }

    private suspend fun handleChmod(command: String) {
        // Let the shell handle chmod, but also try Java API
        val parts = command.split(" ")
        if (parts.size >= 3) {
            val mode = parts[1]
            val filePath = parts.drop(2).joinToString(" ")
            val file = if (filePath.startsWith("/")) {
                File(filePath)
            } else {
                File(workingDir ?: StorageManager.getProjectsDir(context), filePath)
            }

            if (file.exists()) {
                when (mode) {
                    "755", "+x" -> {
                        file.setExecutable(true, false)
                        file.setReadable(true, false)
                        file.setWritable(true, false)
                    }
                    "644" -> {
                        file.setExecutable(false, false)
                        file.setReadable(true, false)
                        file.setWritable(true, false)
                    }
                    "777" -> {
                        file.setExecutable(true, false)
                        file.setReadable(true, false)
                        file.setWritable(true, false)
                    }
                }
                withContext(Dispatchers.Main) {
                    listener?.onOutput("chmod $mode ${file.absolutePath}")
                }
            }
        }
    }

    private suspend fun showHelp() {
        val helpText = """
            |Available commands:
            |  help     - Show this help message
            |  cd       - Change directory
            |  pwd      - Print working directory
            |  ls       - List files
            |  clear    - Clear terminal
            |  chmod    - Change file permissions
            |  cat      - Display file contents
            |  mkdir    - Create directory
            |  rm       - Remove files
            |  cp       - Copy files
            |  mv       - Move files
            |  touch    - Create empty file
            |  echo     - Print text
            |  grep     - Search in files
            |  find     - Find files
            |  
            |Build commands:
            |  ./gradlew assembleDebug    - Build debug APK
            |  ./gradlew assembleRelease  - Build release APK
            |  ./gradlew clean            - Clean build
            |  ./gradlew dependencies     - Show dependencies
            |
            |Environment variables:
            |  JAVA_HOME, ANDROID_HOME, PATH
        """.trimMargin()

        withContext(Dispatchers.Main) {
            listener?.onOutput(helpText)
        }
    }

    private fun buildEnvironment(): Map<String, String> {
        val env = mutableMapOf<String, String>()
        val sdkDir = StorageManager.getSdkDir(context)

        val jdk17Dir = File(sdkDir, "jdk17")
        val jdk21Dir = File(sdkDir, "jdk21")
        val javaHome = when {
            jdk17Dir.exists() -> findJavaHome(jdk17Dir)
            jdk21Dir.exists() -> findJavaHome(jdk21Dir)
            else -> System.getenv("JAVA_HOME") ?: "/usr"
        }
        env["JAVA_HOME"] = javaHome

        val sdkToolsDir = File(sdkDir, "sdk-tools")
        env["ANDROID_HOME"] = if (sdkToolsDir.exists()) sdkToolsDir.absolutePath else sdkDir.absolutePath
        env["ANDROID_SDK_ROOT"] = env["ANDROID_HOME"]!!

        val pathParts = mutableListOf("$javaHome/bin")
        pathParts.add(System.getenv("PATH") ?: "/usr/bin:/bin")
        env["PATH"] = pathParts.joinToString(":")

        env["HOME"] = context.filesDir.absolutePath
        env["TMPDIR"] = context.cacheDir.absolutePath

        return env
    }

    private fun findJavaHome(dir: File): String {
        dir.listFiles()?.forEach { child ->
            if (child.isDirectory && File(child, "bin/java").exists()) {
                return child.absolutePath
            }
        }
        return dir.absolutePath
    }

    fun getPreviousCommand(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = maxOf(0, historyIndex - 1)
        return commandHistory.getOrNull(historyIndex)
    }

    fun getNextCommand(): String? {
        if (commandHistory.isEmpty()) return null
        historyIndex = minOf(commandHistory.size, historyIndex + 1)
        return commandHistory.getOrNull(historyIndex)
    }

    fun destroy() {
        outputJob?.cancel()
        errorJob?.cancel()
        shellProcess?.destroyForcibly()
        scope.cancel()
    }
}
