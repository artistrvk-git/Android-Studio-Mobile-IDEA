#ifndef CODE_ANALYZER_H
#define CODE_ANALYZER_H

#include <string>

class CodeAnalyzer {
public:
    static std::string analyze(const char* code, const char* language);
};

#endif // CODE_ANALYZER_H
