#include "command_executor.h"
#include <cstdio>
#include <cstdlib>
#include <array>
#include <memory>
#include <unistd.h>
#include <sys/stat.h>
#include <android/log.h>

#define LOG_TAG "CommandExecutor"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

std::string CommandExecutor::execute(const char* command, const char* workDir) {
    std::string result;

    // Change to working directory if specified
    char oldDir[1024];
    bool dirChanged = false;
    if (workDir && strlen(workDir) > 0) {
        if (getcwd(oldDir, sizeof(oldDir)) != nullptr) {
            if (chdir(workDir) == 0) {
                dirChanged = true;
            }
        }
    }

    // Execute command using popen
    std::array<char, 4096> buffer;
    std::unique_ptr<FILE, decltype(&pclose)> pipe(popen(command, "r"), pclose);

    if (!pipe) {
        LOGE("Failed to execute command: %s", command);
        result = "Error: Failed to execute command";
    } else {
        while (fgets(buffer.data(), buffer.size(), pipe.get()) != nullptr) {
            result += buffer.data();
        }
    }

    // Restore working directory
    if (dirChanged) {
        chdir(oldDir);
    }

    LOGI("Command executed: %s", command);
    return result;
}

std::string CommandExecutor::compileFile(const char* filePath, const char* outputPath,
                                          const char* language, const std::string& javaHome) {
    std::string cmd;
    std::string lang(language);

    if (lang == "java") {
        cmd = javaHome + "/bin/javac -d " + std::string(outputPath) + " " + std::string(filePath);
    } else if (lang == "kotlin") {
        // Use kotlinc if available
        cmd = "kotlinc " + std::string(filePath) + " -d " + std::string(outputPath);
    } else if (lang == "cpp" || lang == "c") {
        cmd = "g++ -std=c++17 -o " + std::string(outputPath) + " " + std::string(filePath);
    } else if (lang == "python") {
        cmd = "python3 -m py_compile " + std::string(filePath);
    } else {
        return "Error: Unsupported language: " + lang;
    }

    return execute(cmd.c_str(), "");
}
