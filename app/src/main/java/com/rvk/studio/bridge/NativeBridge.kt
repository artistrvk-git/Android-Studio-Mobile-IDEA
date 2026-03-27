package com.rvk.studio.bridge

import android.content.Context
import android.util.Log
import com.rvk.studio.storage.StorageManager
import java.io.File

/**
 * JNI Bridge for C++/Python backend communication.
 * Provides direct native method bindings for performance-critical operations.
 */
class NativeBridge(private val context: Context) {

    companion object {
        const val TAG = "NativeBridge"
        private var isLoaded = false

        init {
            try {
                System.loadLibrary("rvkbridge")
                isLoaded = true
                Log.i(TAG, "Native library loaded successfully")
            } catch (e: UnsatisfiedLinkError) {
                Log.w(TAG, "Native library not available, using fallback", e)
                isLoaded = false
            }
        }

        fun isNativeAvailable(): Boolean = isLoaded
    }

    // Native methods (JNI)
    private external fun nativeInit(sdkPath: String, javaHomePath: String): Boolean
    private external fun nativeExecuteCommand(command: String, workDir: String): String
    private external fun nativeCompileFile(filePath: String, outputPath: String, language: String): String
    private external fun nativeAnalyzeCode(code: String, language: String): String
    private external fun nativeGetSdkVersion(): String
    private external fun nativeCleanup()

    /**
     * Initialize the native backend with SDK paths.
     */
    fun initialize(): Boolean {
        if (!isLoaded) {
            Log.w(TAG, "Native library not loaded, skipping initialization")
            return false
        }

        return try {
            val sdkDir = StorageManager.getSdkDir(context)
            val jdkDir = File(sdkDir, "jdk17")
            val javaHome = if (jdkDir.exists()) jdkDir.absolutePath else "/usr"

            nativeInit(sdkDir.absolutePath, javaHome)
        } catch (e: Exception) {
            Log.e(TAG, "Native init failed", e)
            false
        }
    }

    /**
     * Execute a shell command through the native bridge.
     */
    fun executeCommand(command: String, workDir: String = ""): String {
        if (!isLoaded) {
            return executeCommandFallback(command, workDir)
        }

        return try {
            nativeExecuteCommand(command, workDir)
        } catch (e: Exception) {
            Log.e(TAG, "Native execution failed, falling back", e)
            executeCommandFallback(command, workDir)
        }
    }

    /**
     * Fallback command execution using Java ProcessBuilder.
     */
    private fun executeCommandFallback(command: String, workDir: String): String {
        return try {
            val pb = ProcessBuilder("sh", "-c", command)
            if (workDir.isNotEmpty()) {
                pb.directory(File(workDir))
            }
            pb.redirectErrorStream(true)

            val env = pb.environment()
            val sdkDir = StorageManager.getSdkDir(context)
            val jdk17 = File(sdkDir, "jdk17")
            if (jdk17.exists()) {
                env["JAVA_HOME"] = jdk17.absolutePath
                env["PATH"] = "${jdk17.absolutePath}/bin:${env["PATH"]}"
            }
            env["ANDROID_HOME"] = File(sdkDir, "sdk-tools").absolutePath

            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    /**
     * Analyze code for errors (used for real-time error detection).
     */
    fun analyzeCode(code: String, language: String): List<CodeError> {
        val errors = mutableListOf<CodeError>()

        // Basic syntax analysis
        val lines = code.split("\n")
        for ((index, line) in lines.withIndex()) {
            val lineNum = index + 1
            val trimmed = line.trim()

            when (language.lowercase()) {
                "kotlin", "java" -> {
                    // Check for unclosed brackets
                    if (trimmed.count { it == '(' } != trimmed.count { it == ')' } &&
                        !trimmed.endsWith("{") && !trimmed.startsWith("//") && !trimmed.startsWith("*")) {
                        // Only flag if this line clearly has mismatched parens
                        val openCount = trimmed.count { it == '(' }
                        val closeCount = trimmed.count { it == ')' }
                        if (openCount > 0 && closeCount > 0 && openCount != closeCount &&
                            !trimmed.contains("\"") && trimmed.endsWith(")") || trimmed.endsWith(";")) {
                            errors.add(CodeError(lineNum, "Mismatched parentheses", ErrorSeverity.ERROR))
                        }
                    }
                    // Check for missing semicolons in Java
                    if (language == "java" && trimmed.isNotEmpty() &&
                        !trimmed.endsWith(";") && !trimmed.endsWith("{") && !trimmed.endsWith("}") &&
                        !trimmed.startsWith("//") && !trimmed.startsWith("*") && !trimmed.startsWith("/*") &&
                        !trimmed.startsWith("package") && !trimmed.startsWith("import") &&
                        !trimmed.startsWith("@") && !trimmed.startsWith("if") && !trimmed.startsWith("for") &&
                        !trimmed.startsWith("while") && !trimmed.startsWith("else") &&
                        !trimmed.startsWith("class") && !trimmed.startsWith("public") &&
                        !trimmed.startsWith("private") && !trimmed.startsWith("protected") &&
                        !trimmed.startsWith("return") && !trimmed.isEmpty() &&
                        trimmed.contains("=") && !trimmed.endsWith(",")) {
                        errors.add(CodeError(lineNum, "Possible missing semicolon", ErrorSeverity.WARNING))
                    }
                }
                "python" -> {
                    // Check indentation
                    if (trimmed.startsWith("def ") || trimmed.startsWith("class ") ||
                        trimmed.startsWith("if ") || trimmed.startsWith("for ") ||
                        trimmed.startsWith("while ")) {
                        if (!trimmed.endsWith(":") && !trimmed.contains("#")) {
                            errors.add(CodeError(lineNum, "Missing colon at end of statement", ErrorSeverity.ERROR))
                        }
                    }
                }
                "xml" -> {
                    // Basic XML validation
                    if (trimmed.startsWith("<") && !trimmed.startsWith("<?") &&
                        !trimmed.startsWith("<!--") && !trimmed.startsWith("</") &&
                        !trimmed.endsWith(">") && !trimmed.endsWith("/>") && trimmed.isNotEmpty()) {
                        errors.add(CodeError(lineNum, "Possibly unclosed XML tag", ErrorSeverity.WARNING))
                    }
                }
            }
        }

        return errors
    }

    fun cleanup() {
        if (isLoaded) {
            try {
                nativeCleanup()
            } catch (e: Exception) {
                Log.e(TAG, "Native cleanup error", e)
            }
        }
    }
}

data class CodeError(
    val line: Int,
    val message: String,
    val severity: ErrorSeverity
)

enum class ErrorSeverity {
    ERROR, WARNING, INFO
}
