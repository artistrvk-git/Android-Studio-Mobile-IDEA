package com.rvk.studio.utils

import android.graphics.Color
import android.text.Editable
import android.text.Spannable
import android.text.SpannableStringBuilder
import android.text.TextWatcher
import android.text.style.BackgroundColorSpan
import android.text.style.ForegroundColorSpan
import android.text.style.UnderlineSpan
import android.widget.EditText
import java.util.regex.Pattern

class SyntaxHighlighter(private val editor: EditText) {

    companion object {
        // Darcula theme colors
        private const val COLOR_KEYWORD = 0xFFCC7832.toInt()
        private const val COLOR_STRING = 0xFF6A8759.toInt()
        private const val COLOR_NUMBER = 0xFF6897BB.toInt()
        private const val COLOR_COMMENT = 0xFF808080.toInt()
        private const val COLOR_ANNOTATION = 0xFFBBB529.toInt()
        private const val COLOR_FUNCTION = 0xFFFFC66D.toInt()
        private const val COLOR_TYPE = 0xFF287BDE.toInt()
        private const val COLOR_DEFAULT = 0xFFA9B7C6.toInt()
        private const val COLOR_ERROR_UNDERLINE = 0xFFFF0000.toInt()
        private const val COLOR_ERROR_BG = 0x33FF0000

        private val KOTLIN_KEYWORDS = setOf(
            "abstract", "actual", "annotation", "as", "break", "by", "catch", "class",
            "companion", "const", "constructor", "continue", "crossinline", "data",
            "delegate", "do", "else", "enum", "expect", "external", "false", "final",
            "finally", "for", "fun", "get", "if", "import", "in", "infix", "init",
            "inline", "inner", "interface", "internal", "is", "it", "lateinit",
            "noinline", "null", "object", "open", "operator", "out", "override",
            "package", "private", "protected", "public", "reified", "return", "sealed",
            "set", "super", "suspend", "this", "throw", "true", "try", "typealias",
            "typeof", "val", "var", "vararg", "when", "where", "while"
        )

        private val JAVA_KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char",
            "class", "const", "continue", "default", "do", "double", "else", "enum",
            "extends", "false", "final", "finally", "float", "for", "goto", "if",
            "implements", "import", "instanceof", "int", "interface", "long", "native",
            "new", "null", "package", "private", "protected", "public", "return",
            "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "true", "try", "void", "volatile", "while"
        )

        private val PYTHON_KEYWORDS = setOf(
            "False", "None", "True", "and", "as", "assert", "async", "await", "break",
            "class", "continue", "def", "del", "elif", "else", "except", "finally",
            "for", "from", "global", "if", "import", "in", "is", "lambda", "nonlocal",
            "not", "or", "pass", "raise", "return", "try", "while", "with", "yield"
        )

        private val CPP_KEYWORDS = setOf(
            "alignas", "alignof", "and", "asm", "auto", "bitand", "bitor", "bool",
            "break", "case", "catch", "char", "class", "const", "constexpr", "continue",
            "default", "delete", "do", "double", "else", "enum", "explicit", "export",
            "extern", "false", "float", "for", "friend", "goto", "if", "include",
            "inline", "int", "long", "namespace", "new", "noexcept", "not", "nullptr",
            "operator", "or", "private", "protected", "public", "register", "return",
            "short", "signed", "sizeof", "static", "struct", "switch", "template",
            "this", "throw", "true", "try", "typedef", "typeid", "typename", "union",
            "unsigned", "using", "virtual", "void", "volatile", "while"
        )

        private val DART_KEYWORDS = setOf(
            "abstract", "as", "assert", "async", "await", "break", "case", "catch",
            "class", "const", "continue", "covariant", "default", "deferred", "do",
            "dynamic", "else", "enum", "export", "extends", "extension", "external",
            "factory", "false", "final", "finally", "for", "Function", "get", "hide",
            "if", "implements", "import", "in", "interface", "is", "late", "library",
            "mixin", "new", "null", "on", "operator", "part", "required", "rethrow",
            "return", "set", "show", "static", "super", "switch", "sync", "this",
            "throw", "true", "try", "typedef", "var", "void", "while", "with", "yield"
        )
    }

    private var language = "kotlin"
    private var isHighlighting = false
    private val errorLines = mutableSetOf<Int>()

    fun setLanguage(lang: String) {
        language = lang.lowercase()
    }

    fun detectLanguage(fileName: String) {
        language = when {
            fileName.endsWith(".kt") || fileName.endsWith(".kts") -> "kotlin"
            fileName.endsWith(".java") -> "java"
            fileName.endsWith(".py") -> "python"
            fileName.endsWith(".cpp") || fileName.endsWith(".c") || fileName.endsWith(".h") ||
                fileName.endsWith(".hpp") -> "cpp"
            fileName.endsWith(".dart") -> "dart"
            fileName.endsWith(".js") || fileName.endsWith(".jsx") -> "javascript"
            fileName.endsWith(".ts") || fileName.endsWith(".tsx") -> "typescript"
            fileName.endsWith(".xml") -> "xml"
            fileName.endsWith(".json") -> "json"
            fileName.endsWith(".gradle") || fileName.endsWith(".gradle.kts") -> "kotlin"
            fileName.endsWith(".yaml") || fileName.endsWith(".yml") -> "yaml"
            fileName.endsWith(".md") -> "markdown"
            fileName.endsWith(".html") -> "html"
            fileName.endsWith(".css") -> "css"
            else -> "text"
        }
    }

    fun addErrorLine(line: Int) {
        errorLines.add(line)
    }

    fun clearErrors() {
        errorLines.clear()
    }

    fun highlight(text: Editable) {
        if (isHighlighting) return
        isHighlighting = true

        try {
            // Remove existing spans
            val spans = text.getSpans(0, text.length, ForegroundColorSpan::class.java)
            for (span in spans) {
                text.removeSpan(span)
            }
            val bgSpans = text.getSpans(0, text.length, BackgroundColorSpan::class.java)
            for (span in bgSpans) {
                text.removeSpan(span)
            }
            val ulSpans = text.getSpans(0, text.length, UnderlineSpan::class.java)
            for (span in ulSpans) {
                text.removeSpan(span)
            }

            val content = text.toString()
            if (content.isEmpty()) {
                isHighlighting = false
                return
            }

            when (language) {
                "xml", "html" -> highlightXml(text, content)
                "json" -> highlightJson(text, content)
                else -> highlightCode(text, content)
            }

            // Apply error underlines
            highlightErrors(text, content)

        } finally {
            isHighlighting = false
        }
    }

    private fun highlightCode(text: Editable, content: String) {
        val keywords = when (language) {
            "kotlin" -> KOTLIN_KEYWORDS
            "java" -> JAVA_KEYWORDS
            "python" -> PYTHON_KEYWORDS
            "cpp" -> CPP_KEYWORDS
            "dart" -> DART_KEYWORDS
            "javascript", "typescript" -> KOTLIN_KEYWORDS // similar enough for basic highlighting
            else -> emptySet()
        }

        // Comments (single-line)
        val commentPattern = when (language) {
            "python" -> Pattern.compile("#[^\n]*")
            else -> Pattern.compile("//[^\n]*")
        }
        applyPattern(text, content, commentPattern, COLOR_COMMENT)

        // Multi-line comments
        if (language != "python") {
            val multiCommentPattern = Pattern.compile("/\\*[\\s\\S]*?\\*/")
            applyPattern(text, content, multiCommentPattern, COLOR_COMMENT)
        }

        // Strings (double-quoted)
        val stringPattern = Pattern.compile("\"(?:[^\"\\\\]|\\\\.)*\"")
        applyPattern(text, content, stringPattern, COLOR_STRING)

        // Strings (single-quoted)
        val singleStringPattern = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'")
        applyPattern(text, content, singleStringPattern, COLOR_STRING)

        // Triple-quoted strings (Kotlin/Python)
        if (language == "kotlin" || language == "python") {
            val triplePattern = Pattern.compile("\"\"\"[\\s\\S]*?\"\"\"")
            applyPattern(text, content, triplePattern, COLOR_STRING)
        }

        // Numbers
        val numberPattern = Pattern.compile("\\b\\d+(\\.\\d+)?[fFdDlL]?\\b")
        applyPattern(text, content, numberPattern, COLOR_NUMBER)

        // Hex numbers
        val hexPattern = Pattern.compile("\\b0[xX][0-9a-fA-F]+\\b")
        applyPattern(text, content, hexPattern, COLOR_NUMBER)

        // Keywords
        val keywordPattern = Pattern.compile("\\b(" + keywords.joinToString("|") + ")\\b")
        applyPattern(text, content, keywordPattern, COLOR_KEYWORD)

        // Annotations
        val annotationPattern = Pattern.compile("@\\w+")
        applyPattern(text, content, annotationPattern, COLOR_ANNOTATION)

        // Function calls
        val funcPattern = Pattern.compile("\\b([a-z]\\w*)\\s*\\(")
        val funcMatcher = funcPattern.matcher(content)
        while (funcMatcher.find()) {
            text.setSpan(
                ForegroundColorSpan(COLOR_FUNCTION),
                funcMatcher.start(1), funcMatcher.end(1),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Types (UpperCamelCase)
        val typePattern = Pattern.compile("\\b([A-Z][a-zA-Z0-9]+)\\b")
        applyPattern(text, content, typePattern, COLOR_TYPE)
    }

    private fun highlightXml(text: Editable, content: String) {
        // Tags
        val tagPattern = Pattern.compile("</?\\w[^>]*>")
        applyPattern(text, content, tagPattern, COLOR_KEYWORD)

        // Attribute names
        val attrPattern = Pattern.compile("\\b(\\w+)\\s*=")
        val attrMatcher = attrPattern.matcher(content)
        while (attrMatcher.find()) {
            text.setSpan(
                ForegroundColorSpan(COLOR_FUNCTION),
                attrMatcher.start(1), attrMatcher.end(1),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Attribute values
        val valPattern = Pattern.compile("\"[^\"]*\"")
        applyPattern(text, content, valPattern, COLOR_STRING)

        // Comments
        val commentPattern = Pattern.compile("<!--[\\s\\S]*?-->")
        applyPattern(text, content, commentPattern, COLOR_COMMENT)
    }

    private fun highlightJson(text: Editable, content: String) {
        // Keys
        val keyPattern = Pattern.compile("\"([^\"]+)\"\\s*:")
        val keyMatcher = keyPattern.matcher(content)
        while (keyMatcher.find()) {
            text.setSpan(
                ForegroundColorSpan(COLOR_FUNCTION),
                keyMatcher.start(1), keyMatcher.end(1),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // String values
        val valPattern = Pattern.compile(":\\s*\"([^\"]+)\"")
        val valMatcher = valPattern.matcher(content)
        while (valMatcher.find()) {
            text.setSpan(
                ForegroundColorSpan(COLOR_STRING),
                valMatcher.start(1), valMatcher.end(1),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }

        // Numbers
        val numPattern = Pattern.compile("\\b\\d+(\\.\\d+)?\\b")
        applyPattern(text, content, numPattern, COLOR_NUMBER)

        // Booleans & null
        val boolPattern = Pattern.compile("\\b(true|false|null)\\b")
        applyPattern(text, content, boolPattern, COLOR_KEYWORD)
    }

    private fun highlightErrors(text: Editable, content: String) {
        if (errorLines.isEmpty()) return

        val lines = content.split("\n")
        var offset = 0
        for ((index, line) in lines.withIndex()) {
            if (errorLines.contains(index + 1)) {
                val start = offset
                val end = offset + line.length
                if (end <= text.length) {
                    text.setSpan(
                        UnderlineSpan(),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                    text.setSpan(
                        BackgroundColorSpan(COLOR_ERROR_BG),
                        start, end,
                        Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
                    )
                }
            }
            offset += line.length + 1  // +1 for newline
        }
    }

    private fun applyPattern(text: Editable, content: String, pattern: Pattern, color: Int) {
        val matcher = pattern.matcher(content)
        while (matcher.find()) {
            text.setSpan(
                ForegroundColorSpan(color),
                matcher.start(), matcher.end(),
                Spannable.SPAN_EXCLUSIVE_EXCLUSIVE
            )
        }
    }

    fun createTextWatcher(): TextWatcher {
        return object : TextWatcher {
            private var isEditing = false

            override fun beforeTextChanged(s: CharSequence?, start: Int, count: Int, after: Int) {}
            override fun onTextChanged(s: CharSequence?, start: Int, before: Int, count: Int) {}

            override fun afterTextChanged(s: Editable?) {
                if (isEditing || s == null) return
                isEditing = true
                highlight(s)
                isEditing = false
            }
        }
    }
}
