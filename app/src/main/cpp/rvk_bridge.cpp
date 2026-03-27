#include <jni.h>
#include <string>
#include <cstdlib>
#include <cstdio>
#include <android/log.h>
#include "command_executor.h"
#include "code_analyzer.h"

#define LOG_TAG "RVKBridge"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

static std::string g_sdkPath;
static std::string g_javaHomePath;
static bool g_initialized = false;

extern "C" {

JNIEXPORT jboolean JNICALL
Java_com_rvk_studio_bridge_NativeBridge_nativeInit(
    JNIEnv *env, jobject thiz, jstring sdkPath, jstring javaHomePath) {

    const char *sdk = env->GetStringUTFChars(sdkPath, nullptr);
    const char *javaHome = env->GetStringUTFChars(javaHomePath, nullptr);

    g_sdkPath = std::string(sdk);
    g_javaHomePath = std::string(javaHome);

    env->ReleaseStringUTFChars(sdkPath, sdk);
    env->ReleaseStringUTFChars(javaHomePath, javaHome);

    // Set environment variables
    setenv("ANDROID_HOME", g_sdkPath.c_str(), 1);
    setenv("ANDROID_SDK_ROOT", g_sdkPath.c_str(), 1);
    setenv("JAVA_HOME", g_javaHomePath.c_str(), 1);

    // Update PATH
    std::string currentPath = getenv("PATH") ? getenv("PATH") : "/usr/bin:/bin";
    std::string newPath = g_javaHomePath + "/bin:" + g_sdkPath + "/platform-tools:" + currentPath;
    setenv("PATH", newPath.c_str(), 1);

    g_initialized = true;
    LOGI("Native bridge initialized. SDK: %s, JAVA_HOME: %s", g_sdkPath.c_str(), g_javaHomePath.c_str());

    return JNI_TRUE;
}

JNIEXPORT jstring JNICALL
Java_com_rvk_studio_bridge_NativeBridge_nativeExecuteCommand(
    JNIEnv *env, jobject thiz, jstring command, jstring workDir) {

    const char *cmd = env->GetStringUTFChars(command, nullptr);
    const char *dir = env->GetStringUTFChars(workDir, nullptr);

    std::string result = CommandExecutor::execute(cmd, dir);

    env->ReleaseStringUTFChars(command, cmd);
    env->ReleaseStringUTFChars(workDir, dir);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvk_studio_bridge_NativeBridge_nativeCompileFile(
    JNIEnv *env, jobject thiz, jstring filePath, jstring outputPath, jstring language) {

    const char *file = env->GetStringUTFChars(filePath, nullptr);
    const char *output = env->GetStringUTFChars(outputPath, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    std::string result = CommandExecutor::compileFile(file, output, lang, g_javaHomePath);

    env->ReleaseStringUTFChars(filePath, file);
    env->ReleaseStringUTFChars(outputPath, output);
    env->ReleaseStringUTFChars(language, lang);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvk_studio_bridge_NativeBridge_nativeAnalyzeCode(
    JNIEnv *env, jobject thiz, jstring code, jstring language) {

    const char *src = env->GetStringUTFChars(code, nullptr);
    const char *lang = env->GetStringUTFChars(language, nullptr);

    std::string result = CodeAnalyzer::analyze(src, lang);

    env->ReleaseStringUTFChars(code, src);
    env->ReleaseStringUTFChars(language, lang);

    return env->NewStringUTF(result.c_str());
}

JNIEXPORT jstring JNICALL
Java_com_rvk_studio_bridge_NativeBridge_nativeGetSdkVersion(
    JNIEnv *env, jobject thiz) {
    std::string version = "RVK Bridge v1.0.0 | SDK: " + g_sdkPath;
    return env->NewStringUTF(version.c_str());
}

JNIEXPORT void JNICALL
Java_com_rvk_studio_bridge_NativeBridge_nativeCleanup(
    JNIEnv *env, jobject thiz) {
    g_initialized = false;
    LOGI("Native bridge cleanup complete");
}

} // extern "C"
