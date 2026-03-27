package com.rvk.studio.ui.dialogs

import android.content.Intent
import android.os.Bundle
import android.view.View
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import com.google.android.material.button.MaterialButton
import com.google.android.material.textfield.TextInputEditText
import com.google.android.material.textfield.TextInputLayout
import com.rvk.studio.R
import com.rvk.studio.engine.project.ProjectConfig
import com.rvk.studio.engine.project.ProjectCreator
import com.rvk.studio.engine.project.ProjectPlatform
import com.rvk.studio.storage.StorageManager

class ProjectCreatorActivity : AppCompatActivity() {

    private lateinit var rgPlatform: RadioGroup
    private lateinit var etProjectName: TextInputEditText
    private lateinit var etPackageName: TextInputEditText
    private lateinit var etMinSdk: TextInputEditText
    private lateinit var etSaveLocation: TextInputEditText
    private lateinit var rgLanguage: RadioGroup
    private lateinit var tilPackageName: TextInputLayout
    private lateinit var tilMinSdk: TextInputLayout
    private lateinit var llLanguageSelection: View

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_project_creator)

        initViews()
        setupListeners()
    }

    private fun initViews() {
        rgPlatform = findViewById(R.id.rgPlatform)
        etProjectName = findViewById(R.id.etProjectName)
        etPackageName = findViewById(R.id.etPackageName)
        etMinSdk = findViewById(R.id.etMinSdk)
        etSaveLocation = findViewById(R.id.etSaveLocation)
        rgLanguage = findViewById(R.id.rgLanguage)
        tilPackageName = findViewById(R.id.tilPackageName)
        tilMinSdk = findViewById(R.id.tilMinSdk)
        llLanguageSelection = findViewById(R.id.llLanguageSelection)

        // Set default save location
        etSaveLocation.setText(StorageManager.getProjectsDir(this).absolutePath)
    }

    private fun setupListeners() {
        findViewById<ImageButton>(R.id.btnBackCreator).setOnClickListener {
            finish()
        }

        rgPlatform.setOnCheckedChangeListener { _, checkedId ->
            when (checkedId) {
                R.id.rbAndroid -> {
                    tilPackageName.visibility = View.VISIBLE
                    tilMinSdk.visibility = View.VISIBLE
                    llLanguageSelection.visibility = View.VISIBLE
                }
                else -> {
                    tilPackageName.visibility = View.GONE
                    tilMinSdk.visibility = View.GONE
                    llLanguageSelection.visibility = View.GONE
                }
            }
        }

        findViewById<MaterialButton>(R.id.btnCreateProject).setOnClickListener {
            createProject()
        }
    }

    private fun createProject() {
        val projectName = etProjectName.text?.toString()?.trim()
        if (projectName.isNullOrEmpty()) {
            etProjectName.error = "Project name is required"
            return
        }

        val platform = when (rgPlatform.checkedRadioButtonId) {
            R.id.rbAndroid -> {
                if (rgLanguage.checkedRadioButtonId == R.id.rbJava) {
                    ProjectPlatform.ANDROID_JAVA
                } else {
                    ProjectPlatform.ANDROID_KOTLIN
                }
            }
            R.id.rbPython -> ProjectPlatform.PYTHON
            R.id.rbReactNative -> ProjectPlatform.REACT_NATIVE
            R.id.rbCpp -> ProjectPlatform.CPP
            R.id.rbFlutter -> ProjectPlatform.FLUTTER
            else -> ProjectPlatform.ANDROID_KOTLIN
        }

        val packageName = etPackageName.text?.toString()?.trim()
            ?: "com.example.${projectName.lowercase().replace(Regex("[^a-z0-9]"), "")}"

        val minSdk = etMinSdk.text?.toString()?.toIntOrNull() ?: 24
        val saveLocation = etSaveLocation.text?.toString()?.trim() ?: ""

        val config = ProjectConfig(
            name = projectName,
            platform = platform,
            packageName = packageName,
            minSdk = minSdk,
            saveLocation = saveLocation
        )

        try {
            val creator = ProjectCreator(this)
            val projectDir = creator.createProject(config)

            // Make gradlew executable for Android projects
            if (platform == ProjectPlatform.ANDROID_KOTLIN || platform == ProjectPlatform.ANDROID_JAVA) {
                StorageManager.makeExecutableRecursive(projectDir)
            }

            Toast.makeText(this, "Project created: ${projectDir.absolutePath}", Toast.LENGTH_LONG).show()

            // Return project path to MainActivity
            val resultIntent = Intent().apply {
                putExtra("project_path", projectDir.absolutePath)
            }
            setResult(RESULT_OK, resultIntent)
            finish()

        } catch (e: Exception) {
            Toast.makeText(this, "Error creating project: ${e.message}", Toast.LENGTH_LONG).show()
        }
    }
}
