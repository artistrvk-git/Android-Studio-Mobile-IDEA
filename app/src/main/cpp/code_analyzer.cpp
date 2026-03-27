#include "code_analyzer.h"
#include <sstream>
#include <vector>
#include <algorithm>
#include <android/log.h>

#define LOG_TAG "CodeAnalyzer"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)

struct AnalysisError {
    int line;
    std::string message;
    std::string severity; // "error", "warning", "info"
};

static std::vector<std::string> splitLines(const std::string& text) {
    std::vector<std::string> lines;
    std::istringstream stream(text);
    std::string line;
    while (std::getline(stream, line)) {
        lines.push_back(line);
    }
    return lines;
}

static std::string trim(const std::string& str) {
    size_t first = str.find_first_not_of(" \t\r\n");
    if (first == std::string::npos) return "";
    size_t last = str.find_last_not_of(" \t\r\n");
    return str.substr(first, last - first + 1);
}

static std::string toJson(const std::vector<AnalysisError>& errors) {
    std::ostringstream json;
    json << "{\"errors\":[";
    for (size_t i = 0; i < errors.size(); i++) {
        if (i > 0) json << ",";
        json << "{\"line\":" << errors[i].line
             << ",\"message\":\"" << errors[i].message
             << "\",\"severity\":\"" << errors[i].severity << "\"}";
    }
    json << "]}";
    return json.str();
}

std::string CodeAnalyzer::analyze(const char* code, const char* language) {
    std::string src(code);
    std::string lang(language);
    std::vector<AnalysisError> errors;
    auto lines = splitLines(src);

    int braceCount = 0;
    int parenCount = 0;
    int bracketCount = 0;

    for (size_t i = 0; i < lines.size(); i++) {
        int lineNum = static_cast<int>(i + 1);
        std::string trimmed = trim(lines[i]);

        // Skip empty lines and comments
        if (trimmed.empty()) continue;
        if (trimmed.substr(0, 2) == "//" || trimmed.substr(0, 1) == "#") continue;
        if (trimmed.substr(0, 2) == "/*") continue;
        if (trimmed.substr(0, 1) == "*") continue;

        // Count brackets
        for (char c : lines[i]) {
            switch (c) {
                case '{': braceCount++; break;
                case '}': braceCount--; break;
                case '(': parenCount++; break;
                case ')': parenCount--; break;
                case '[': bracketCount++; break;
                case ']': bracketCount--; break;
            }
        }

        if (braceCount < 0) {
            errors.push_back({lineNum, "Unexpected closing brace", "error"});
            braceCount = 0;
        }
        if (parenCount < 0) {
            errors.push_back({lineNum, "Unexpected closing parenthesis", "error"});
            parenCount = 0;
        }

        // Language-specific checks
        if (lang == "java") {
            // Check for missing semicolons on statements
            if (!trimmed.empty() &&
                trimmed.back() != ';' && trimmed.back() != '{' && trimmed.back() != '}' &&
                trimmed.back() != ',' && trimmed.back() != '(' && trimmed.back() != ')' &&
                trimmed.substr(0, 6) != "import" && trimmed.substr(0, 7) != "package" &&
                trimmed.substr(0, 1) != "@" &&
                trimmed.find("class ") == std::string::npos &&
                trimmed.find("interface ") == std::string::npos &&
                trimmed.find("if ") == std::string::npos && trimmed.find("if(") == std::string::npos &&
                trimmed.find("else") == std::string::npos &&
                trimmed.find("for ") == std::string::npos && trimmed.find("for(") == std::string::npos &&
                trimmed.find("while ") == std::string::npos &&
                trimmed.find("try") == std::string::npos &&
                trimmed.find("catch") == std::string::npos &&
                trimmed.find("finally") == std::string::npos &&
                trimmed.find("switch") == std::string::npos &&
                trimmed.find("case ") == std::string::npos &&
                trimmed.find("default:") == std::string::npos &&
                (trimmed.find("=") != std::string::npos || trimmed.find("return") != std::string::npos)) {
                errors.push_back({lineNum, "Possible missing semicolon", "warning"});
            }
        }

        if (lang == "python") {
            if ((trimmed.substr(0, 4) == "def " || trimmed.substr(0, 6) == "class " ||
                 trimmed.substr(0, 3) == "if " || trimmed.substr(0, 5) == "elif " ||
                 trimmed.substr(0, 4) == "for " || trimmed.substr(0, 6) == "while " ||
                 trimmed.substr(0, 4) == "try:" || trimmed.substr(0, 5) == "else:") &&
                trimmed.back() != ':' && trimmed.find("#") == std::string::npos) {
                errors.push_back({lineNum, "Missing colon at end of statement", "error"});
            }
        }
    }

    // Check for unclosed brackets at end of file
    if (braceCount > 0) {
        errors.push_back({static_cast<int>(lines.size()), "Unclosed brace(s)", "error"});
    }
    if (parenCount > 0) {
        errors.push_back({static_cast<int>(lines.size()), "Unclosed parenthesis", "error"});
    }
    if (bracketCount > 0) {
        errors.push_back({static_cast<int>(lines.size()), "Unclosed bracket(s)", "error"});
    }

    return toJson(errors);
}
