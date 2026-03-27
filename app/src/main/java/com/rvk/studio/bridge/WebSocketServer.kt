package com.rvk.studio.bridge

import android.content.Context
import android.util.Log
import com.rvk.studio.storage.StorageManager
import kotlinx.coroutines.*
import java.io.*
import java.net.ServerSocket
import java.net.Socket

/**
 * Local WebSocket/HTTP server for real-time communication between
 * the Kotlin frontend and the Python/C++ backend.
 * Provides real-time terminal output, build logs, and Gradle sync status.
 */
class WebSocketServer(private val context: Context) {

    companion object {
        const val TAG = "WebSocketServer"
        const val DEFAULT_PORT = 8391
        private var instance: WebSocketServer? = null

        fun getInstance(context: Context): WebSocketServer {
            return instance ?: WebSocketServer(context).also { instance = it }
        }
    }

    interface ServerListener {
        fun onMessage(type: String, data: String)
        fun onClientConnected()
        fun onClientDisconnected()
        fun onServerStarted(port: Int)
        fun onServerError(error: String)
    }

    private var serverSocket: ServerSocket? = null
    private var listener: ServerListener? = null
    private var isRunning = false
    private var port = DEFAULT_PORT
    private val scope = CoroutineScope(Dispatchers.IO + SupervisorJob())
    private val clients = mutableListOf<Socket>()

    fun setListener(l: ServerListener?) {
        listener = l
    }

    fun start(serverPort: Int = DEFAULT_PORT) {
        if (isRunning) return
        port = serverPort

        scope.launch {
            try {
                serverSocket = ServerSocket(port)
                isRunning = true

                withContext(Dispatchers.Main) {
                    listener?.onServerStarted(port)
                }

                Log.i(TAG, "Server started on port $port")

                while (isRunning) {
                    try {
                        val client = serverSocket!!.accept()
                        clients.add(client)
                        handleClient(client)
                    } catch (e: Exception) {
                        if (isRunning) {
                            Log.e(TAG, "Error accepting client", e)
                        }
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Server error", e)
                withContext(Dispatchers.Main) {
                    listener?.onServerError("Server error: ${e.message}")
                }
            }
        }
    }

    private fun handleClient(client: Socket) {
        scope.launch {
            try {
                withContext(Dispatchers.Main) {
                    listener?.onClientConnected()
                }

                val reader = BufferedReader(InputStreamReader(client.inputStream))
                val writer = PrintWriter(BufferedOutputStream(client.outputStream), true)

                // Read HTTP request
                val requestLine = reader.readLine() ?: return@launch
                val headers = mutableMapOf<String, String>()
                var line: String?
                while (reader.readLine().also { line = it } != null) {
                    if (line!!.isEmpty()) break
                    val parts = line!!.split(": ", limit = 2)
                    if (parts.size == 2) {
                        headers[parts[0]] = parts[1]
                    }
                }

                // Parse request
                val parts = requestLine.split(" ")
                val method = parts.getOrNull(0) ?: "GET"
                val path = parts.getOrNull(1) ?: "/"

                // Handle request
                val response = handleRequest(method, path, headers, reader)

                // Send response
                writer.println("HTTP/1.1 200 OK")
                writer.println("Content-Type: application/json")
                writer.println("Access-Control-Allow-Origin: *")
                writer.println("Connection: close")
                writer.println()
                writer.println(response)
                writer.flush()

                client.close()
                clients.remove(client)

                withContext(Dispatchers.Main) {
                    listener?.onClientDisconnected()
                }

            } catch (e: Exception) {
                Log.e(TAG, "Client handling error", e)
                try { client.close() } catch (_: Exception) {}
                clients.remove(client)
            }
        }
    }

    private fun handleRequest(
        method: String,
        path: String,
        headers: Map<String, String>,
        reader: BufferedReader
    ): String {
        return when {
            path == "/status" -> {
                """{"status":"running","port":$port,"version":"1.0.0"}"""
            }
            path == "/sdk/status" -> {
                val sdkDir = StorageManager.getSdkDir(context)
                val components = listOf("jdk17", "jdk21", "ndk", "sdk-tools",
                    "gradle-8.14.4", "gradle-9.3.0", "gradle-9.4.0",
                    "flutter", "nodejs", "python")
                val status = components.map { name ->
                    val dir = File(sdkDir, name)
                    val installed = dir.exists() && (dir.listFiles()?.isNotEmpty() ?: false)
                    "\"$name\":$installed"
                }.joinToString(",")
                "{$status}"
            }
            path == "/terminal/execute" && method == "POST" -> {
                val contentLength = headers["Content-Length"]?.toIntOrNull() ?: 0
                val body = CharArray(contentLength)
                reader.read(body, 0, contentLength)
                val command = String(body).trim()
                val result = executeCommand(command)
                """{"output":"${escapeJson(result)}"}"""
            }
            path.startsWith("/build/") -> {
                val action = path.removePrefix("/build/")
                """{"action":"$action","status":"acknowledged"}"""
            }
            path == "/projects" -> {
                val projectsDir = StorageManager.getProjectsDir(context)
                val projects = projectsDir.listFiles()
                    ?.filter { it.isDirectory }
                    ?.map { "\"${escapeJson(it.name)}\"" }
                    ?.joinToString(",") ?: ""
                """{"projects":[$projects]}"""
            }
            else -> {
                """{"error":"Unknown endpoint: $path"}"""
            }
        }
    }

    private fun executeCommand(command: String): String {
        return try {
            val pb = ProcessBuilder("sh", "-c", command)
            pb.redirectErrorStream(true)
            pb.directory(StorageManager.getProjectsDir(context))
            val process = pb.start()
            val output = process.inputStream.bufferedReader().readText()
            process.waitFor()
            output
        } catch (e: Exception) {
            "Error: ${e.message}"
        }
    }

    fun broadcastMessage(type: String, data: String) {
        scope.launch {
            withContext(Dispatchers.Main) {
                listener?.onMessage(type, data)
            }
        }
    }

    fun stop() {
        isRunning = false
        clients.forEach { try { it.close() } catch (_: Exception) {} }
        clients.clear()
        try { serverSocket?.close() } catch (_: Exception) {}
        scope.cancel()
        Log.i(TAG, "Server stopped")
    }

    fun isRunning(): Boolean = isRunning

    private fun escapeJson(text: String): String {
        return text.replace("\\", "\\\\")
            .replace("\"", "\\\"")
            .replace("\n", "\\n")
            .replace("\r", "\\r")
            .replace("\t", "\\t")
    }
}
