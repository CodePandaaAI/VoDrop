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
        jbyteArray pcmData
) {
    if (contextPtr == 0L) {
        LOGE("Context is null!");
        return env->NewStringUTF("");
    }

    struct whisper_context* ctx = reinterpret_cast<whisper_context*>(contextPtr);

    jsize len = env->GetArrayLength(pcmData);
    LOGI("Received %d bytes of audio data", len);

    if (len == 0) {
        LOGE("Empty audio data!");
        return env->NewStringUTF("");
    }

    jbyte* bytes = env->GetByteArrayElements(pcmData, nullptr);
    if (bytes == nullptr) {
        LOGE("Failed to get byte array elements!");
        return env->NewStringUTF("");
    }

    // Convert to samples
    int numSamples = len / 2;
    LOGI("Converting to %d float samples...", numSamples);

    std::vector<float> samples(numSamples);
    for (int i = 0; i < numSamples; ++i) {
        int16_t sample = ((int16_t)(uint8_t)bytes[2*i]) |
                ((int16_t)(uint8_t)bytes[2*i + 1] << 8);
        samples[i] = sample / 32768.0f;
    }

    env->ReleaseByteArrayElements(pcmData, bytes, JNI_ABORT);

    float audioDuration = (float)numSamples / 16000.0f;
    LOGI("Audio duration: %.2f seconds", audioDuration);

    // Setup parameters - simplified
    struct whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.language = "en";
    params.translate = false;
    params.no_context = true;
    params.single_segment = true;
    params.print_progress = false;
    params.print_timestamps = false;
    params.print_realtime = false;
    params.print_special = false;
    params.n_threads = 1;  // Single thread for safety

    LOGI("Starting whisper_full transcription with %d samples...", numSamples);

    int result = whisper_full(ctx, params, samples.data(), samples.size());

    LOGI("whisper_full returned: %d", result);

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

}