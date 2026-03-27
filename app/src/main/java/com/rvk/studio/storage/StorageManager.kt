package com.rvk.studio.storage

import android.Manifest
import android.app.Activity
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.Settings
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.File

object StorageManager {

    const val REQUEST_STORAGE_PERMISSION = 1001
    const val REQUEST_MANAGE_STORAGE = 1002

    private const val BASE_DIR_NAME = "AndroidStudioMobile"

    fun getBaseDir(context: Context): File {
        val dir = File(Environment.getExternalStorageDirectory(), BASE_DIR_NAME)
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getProjectsDir(context: Context): File {
        val dir = File(getBaseDir(context), "Projects")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSdkDir(context: Context): File {
        val dir = File(getBaseDir(context), "sdk")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getJdkDir(context: Context, version: String = "17"): File {
        val dir = File(getSdkDir(context), "jdk$version")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getNdkDir(context: Context): File {
        val dir = File(getSdkDir(context), "ndk")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getGradleDir(context: Context, version: String = "8.14.4"): File {
        val dir = File(getSdkDir(context), "gradle-$version")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getFlutterDir(context: Context): File {
        val dir = File(getSdkDir(context), "flutter")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getNodeDir(context: Context): File {
        val dir = File(getSdkDir(context), "nodejs")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getPythonDir(context: Context): File {
        val dir = File(getSdkDir(context), "python")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getSdkToolsDir(context: Context): File {
        val dir = File(getSdkDir(context), "sdk-tools")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun getTempDir(context: Context): File {
        val dir = File(getBaseDir(context), "temp")
        if (!dir.exists()) dir.mkdirs()
        return dir
    }

    fun hasStoragePermission(context: Context): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Environment.isExternalStorageManager()
        } else {
            ContextCompat.checkSelfPermission(
                context,
                Manifest.permission.WRITE_EXTERNAL_STORAGE
            ) == PackageManager.PERMISSION_GRANTED
        }
    }

    fun requestStoragePermission(activity: Activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            try {
                val intent = Intent(Settings.ACTION_MANAGE_APP_ALL_FILES_ACCESS_PERMISSION).apply {
                    data = Uri.parse("package:${activity.packageName}")
                }
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            } catch (e: Exception) {
                val intent = Intent(Settings.ACTION_MANAGE_ALL_FILES_ACCESS_PERMISSION)
                activity.startActivityForResult(intent, REQUEST_MANAGE_STORAGE)
            }
        } else {
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(
                    Manifest.permission.READ_EXTERNAL_STORAGE,
                    Manifest.permission.WRITE_EXTERNAL_STORAGE
                ),
                REQUEST_STORAGE_PERMISSION
            )
        }
    }

    fun ensureExecutable(file: File): Boolean {
        return try {
            if (file.exists()) {
                file.setExecutable(true, false)
                file.setReadable(true, false)
                file.setWritable(true, false)
                true
            } else {
                false
            }
        } catch (e: Exception) {
            false
        }
    }

    fun makeExecutableRecursive(dir: File) {
        if (dir.isDirectory) {
            dir.listFiles()?.forEach { file ->
                if (file.isDirectory) {
                    makeExecutableRecursive(file)
                } else if (file.name.endsWith(".sh") || file.name == "gradlew" ||
                    file.name.startsWith("aapt") || file.name.startsWith("dx") ||
                    file.name.startsWith("zipalign") || file.name == "java" ||
                    file.name == "javac" || file.name == "jar"
                ) {
                    ensureExecutable(file)
                }
            }
        }
    }
}
