package com.rvk.studio.utils

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.Filter
import android.widget.Filterable
import android.widget.PopupWindow
import android.widget.ListView
import android.widget.TextView
import android.widget.EditText

class AutoCompleteProvider(private val context: Context) {

    private val kotlinSuggestions = listOf(
        "fun ", "val ", "var ", "class ", "object ", "interface ", "data class ",
        "sealed class ", "enum class ", "abstract ", "override ", "private ", "public ",
        "internal ", "protected ", "companion object", "init {", "constructor(",
        "return ", "if (", "else {", "when (", "for (", "while (", "try {", "catch (",
        "finally {", "throw ", "import ", "package ", "suspend fun ", "inline fun ",
        "lateinit var ", "lazy {", "by lazy {", "coroutineScope {", "launch {",
        "async {", "withContext(", "runBlocking {", "println(", "listOf(", "mapOf(",
        "setOf(", "arrayOf(", "mutableListOf(", "mutableMapOf(", "toString()",
        "hashCode()", "equals(", "copy(", "apply {", "let {", "run {", "also {",
        "with(", "takeIf {", "takeUnless {", "forEach {", "map {", "filter {",
        "flatMap {", "reduce {", "fold(", "groupBy {", "sortedBy {", "distinctBy {",
        "first()", "last()", "firstOrNull()", "lastOrNull()", "find {",
        "any {", "all {", "none {", "count {", "sumOf {", "joinToString(",
        "@Composable", "@Preview", "@SuppressLint", "@JvmStatic", "@JvmOverloads",
        "Column {", "Row {", "Box {", "Text(", "Button(", "TextField(",
        "Scaffold {", "TopAppBar(", "BottomNavigation {", "LazyColumn {",
        "LazyRow {", "Card {", "Surface {", "Modifier.", "remember {",
        "mutableStateOf(", "derivedStateOf {", "LaunchedEffect(",
        "rememberCoroutineScope()", "collectAsState()", "viewModel()",
        "NavHost(", "composable(", "navigate(", "popBackStack()"
    )

    private val javaSuggestions = listOf(
        "public ", "private ", "protected ", "static ", "final ", "abstract ",
        "class ", "interface ", "extends ", "implements ", "void ", "int ",
        "String ", "boolean ", "long ", "double ", "float ", "char ",
        "new ", "return ", "if (", "else {", "for (", "while (",
        "switch (", "case ", "break;", "continue;", "try {", "catch (",
        "finally {", "throw new ", "throws ", "import ", "package ",
        "System.out.println(", "System.err.println(", "@Override",
        "@SuppressWarnings", "@Deprecated", "ArrayList<>(",
        "HashMap<>(", "LinkedList<>(", "Collections.", "Arrays.",
        "Optional.", "Stream.", ".stream()", ".collect(",
        ".filter(", ".map(", ".forEach(", ".reduce(",
        "StringBuilder(", "toString()", "equals(", "hashCode()"
    )

    private val pythonSuggestions = listOf(
        "def ", "class ", "import ", "from ", "return ", "if ", "elif ",
        "else:", "for ", "while ", "try:", "except ", "finally:", "raise ",
        "with ", "as ", "yield ", "lambda ", "pass", "break", "continue",
        "print(", "len(", "range(", "str(", "int(", "float(", "list(",
        "dict(", "set(", "tuple(", "type(", "isinstance(", "hasattr(",
        "getattr(", "setattr(", "super()", "self.", "__init__(",
        "__str__(", "__repr__(", "__len__(", "__getitem__(",
        "if __name__ == '__main__':", "async def ", "await ",
        ".append(", ".extend(", ".insert(", ".remove(", ".pop(",
        ".keys()", ".values()", ".items()", ".get(", ".update(",
        ".format(", ".strip()", ".split(", ".join(", ".replace(",
        ".startswith(", ".endswith(", ".lower()", ".upper()"
    )

    private val xmlSuggestions = listOf(
        "android:layout_width=\"match_parent\"",
        "android:layout_width=\"wrap_content\"",
        "android:layout_height=\"match_parent\"",
        "android:layout_height=\"wrap_content\"",
        "android:id=\"@+id/\"",
        "android:text=\"\"",
        "android:textSize=\"\"",
        "android:textColor=\"\"",
        "android:background=\"\"",
        "android:orientation=\"vertical\"",
        "android:orientation=\"horizontal\"",
        "android:gravity=\"center\"",
        "android:layout_gravity=\"center\"",
        "android:padding=\"\"",
        "android:layout_margin=\"\"",
        "android:visibility=\"visible\"",
        "android:visibility=\"gone\"",
        "android:src=\"@drawable/\"",
        "app:layout_constraintTop_toTopOf=\"parent\"",
        "app:layout_constraintBottom_toBottomOf=\"parent\"",
        "app:layout_constraintStart_toStartOf=\"parent\"",
        "app:layout_constraintEnd_toEndOf=\"parent\"",
        "xmlns:android=\"http://schemas.android.com/apk/res/android\"",
        "xmlns:app=\"http://schemas.android.com/apk/res-auto\"",
        "xmlns:tools=\"http://schemas.android.com/tools\""
    )

    fun getSuggestions(language: String, prefix: String): List<String> {
        if (prefix.length < 2) return emptyList()

        val suggestions = when (language.lowercase()) {
            "kotlin" -> kotlinSuggestions
            "java" -> javaSuggestions
            "python" -> pythonSuggestions
            "xml", "html" -> xmlSuggestions
            else -> kotlinSuggestions + javaSuggestions
        }

        return suggestions.filter {
            it.lowercase().startsWith(prefix.lowercase()) ||
                it.lowercase().contains(prefix.lowercase())
        }.take(10)
    }

    fun getCurrentWord(text: String, cursorPos: Int): String {
        if (cursorPos <= 0 || text.isEmpty()) return ""

        var start = cursorPos - 1
        while (start >= 0 && !text[start].isWhitespace() && text[start] != '(' &&
            text[start] != ')' && text[start] != '{' && text[start] != '}' &&
            text[start] != ';' && text[start] != ','
        ) {
            start--
        }
        start++

        return if (start < cursorPos) text.substring(start, cursorPos) else ""
    }
}
