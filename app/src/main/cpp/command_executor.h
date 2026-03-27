#ifndef COMMAND_EXECUTOR_H
#define COMMAND_EXECUTOR_H

#include <string>

class CommandExecutor {
public:
    static std::string execute(const char* command, const char* workDir);
    static std::string compileFile(const char* filePath, const char* outputPath,
                                    const char* language, const std::string& javaHome);
};

#endif // COMMAND_EXECUTOR_H
