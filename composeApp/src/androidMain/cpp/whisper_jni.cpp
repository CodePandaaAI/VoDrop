#include <jni.h>
#include <string>
#include <vector>
#include <android/log.h>
#include "whisper.h"

#define LOG_TAG "WhisperJNI"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" {

JNIEXPORT jlong JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_init(
        JNIEnv* env,
        jobject thiz,
        jstring modelPath
) {
    const char* path = env->GetStringUTFChars(modelPath, nullptr);
    LOGI("Loading model from: %s", path);

    struct whisper_context_params cparams = whisper_context_default_params();
    cparams.use_gpu = false;

    struct whisper_context* ctx = whisper_init_from_file_with_params(path, cparams);

    env->ReleaseStringUTFChars(modelPath, path);

    if (ctx == nullptr) {
        LOGE("Failed to initialize whisper context!");
        return 0L;
    }

    LOGI("Whisper model loaded successfully!");
    return reinterpret_cast<jlong>(ctx);
}

JNIEXPORT jstring JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_transcribe(
        JNIEnv* env,
        jobject thiz,
        jlong contextPtr,
        jfloatArray audioData
) {
    if (contextPtr == 0L) {
        LOGE("Context is null!");
        return env->NewStringUTF("");
    }

    struct whisper_context* ctx = reinterpret_cast<whisper_context*>(contextPtr);

    jfloat* samples = env->GetFloatArrayElements(audioData, nullptr);
    jsize numSamples = env->GetArrayLength(audioData);

    LOGI("Received %d float samples", numSamples);

    if (numSamples == 0 || samples == nullptr) {
        LOGE("Empty or null audio data!");
        if (samples != nullptr) {
            env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);
        }
        return env->NewStringUTF("");
    }

    float audioDuration = (float)numSamples / 16000.0f;
    LOGI("Audio duration: %.2f seconds", audioDuration);

    // ============================================
    // SPEED-OPTIMIZED SETTINGS
    // ============================================

    // GREEDY is faster than BEAM_SEARCH
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);

    // Basic settings
    params.print_realtime = false;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_special = false;
    params.translate = false;
    params.n_threads = 4;
    params.offset_ms = 0;
    params.single_segment = false;

    // ============================================
    // ACCURACY + SPEED OPTIMIZATIONS
    // ============================================

    // Set language explicitly (faster than auto-detect)
    params.language = "en";

    // Keep context for better accuracy within same recording
    params.no_context = false;

    // Greedy sampling - just pick best, no beam search overhead
    params.greedy.best_of = 1;

    // Low temperature = more accurate, less creative
    params.temperature = 0.0f;

    // Suppress blank/silence tokens for cleaner output
    params.suppress_blank = true;
    params.suppress_nst = true;  // Suppress non-speech tokens

    LOGI("Using GREEDY mode with %d threads, language=en", params.n_threads);

    whisper_reset_timings(ctx);

    LOGI("Starting whisper_full transcription...");

    int result = whisper_full(ctx, params, samples, numSamples);

    LOGI("whisper_full returned: %d", result);
    whisper_print_timings(ctx);

    env->ReleaseFloatArrayElements(audioData, samples, JNI_ABORT);

    if (result != 0) {
        LOGE("Transcription failed with code: %d", result);
        return env->NewStringUTF("");
    }

    // Get results
    std::string fullText;
    int numSegments = whisper_full_n_segments(ctx);
    LOGI("Got %d segments", numSegments);

    for (int i = 0; i < numSegments; ++i) {
        const char* segmentText = whisper_full_get_segment_text(ctx, i);
        if (segmentText != nullptr) {
            fullText += segmentText;
            LOGI("Segment %d: %s", i, segmentText);
        }
    }

    LOGI("Final transcription: %s", fullText.c_str());
    return env->NewStringUTF(fullText.c_str());
}

JNIEXPORT void JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_release(
        JNIEnv* env,
        jobject thiz,
        jlong contextPtr
) {
    if (contextPtr != 0L) {
        struct whisper_context* ctx = reinterpret_cast<whisper_context*>(contextPtr);
        whisper_free(ctx);
        LOGI("Whisper context released");
    }
}

JNIEXPORT jstring JNICALL
Java_com_liftley_vodrop_stt_WhisperJni_getSystemInfo(
        JNIEnv* env,
        jobject thiz
) {
    const char* sysinfo = whisper_print_system_info();
    LOGI("System info: %s", sysinfo);
    return env->NewStringUTF(sysinfo);
}

}