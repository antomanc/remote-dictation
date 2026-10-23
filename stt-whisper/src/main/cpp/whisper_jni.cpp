// JNI bridge for WhisperEngine: loads the model from the APK's assets and transcribes one
// utterance at a time.
#include <android/asset_manager_jni.h>
#include <android/log.h>
#include <jni.h>
#include <malloc.h>

#include <string>

#include "whisper.h"

namespace {

constexpr float kNoSpeechThreshold = 0.6f;

size_t assetRead(void *ctx, void *output, size_t size) {
    int read = AAsset_read(static_cast<AAsset *>(ctx), output, size);
    return read > 0 ? static_cast<size_t>(read) : 0;
}

bool assetEof(void *ctx) { return AAsset_getRemainingLength64(static_cast<AAsset *>(ctx)) <= 0; }

void assetClose(void *ctx) { AAsset_close(static_cast<AAsset *>(ctx)); }

}  // namespace

extern "C" JNIEXPORT jlong JNICALL
Java_dev_remotedictation_stt_whisper_WhisperEngine_nativeLoad(JNIEnv *env, jclass, jobject assetManager,
                                                             jstring assetName) {
    // A previously loaded engine (Vosk) frees its model as many small blocks that the allocator
    // keeps; hand them back to the system before taking ~150 MB on a stick with 900 MB of RAM.
    mallopt(M_PURGE, 0);

    const char *name = env->GetStringUTFChars(assetName, nullptr);
    AAsset *asset = AAssetManager_open(AAssetManager_fromJava(env, assetManager), name, AASSET_MODE_STREAMING);
    env->ReleaseStringUTFChars(assetName, name);
    if (asset == nullptr) return 0;

    whisper_model_loader loader = {asset, assetRead, assetEof, assetClose};
    whisper_context_params params = whisper_context_default_params();
    params.use_gpu = false;
    return reinterpret_cast<jlong>(whisper_init_with_params(&loader, params));
}

extern "C" JNIEXPORT void JNICALL
Java_dev_remotedictation_stt_whisper_WhisperEngine_nativeFree(JNIEnv *, jclass, jlong context) {
    whisper_free(reinterpret_cast<whisper_context *>(context));
}

/**
 * Returns the transcript as UTF-8 (empty when Whisper thinks nobody spoke), or null on failure.
 * Bytes rather than a jstring: NewStringUTF expects modified UTF-8.
 */
extern "C" JNIEXPORT jbyteArray JNICALL
Java_dev_remotedictation_stt_whisper_WhisperEngine_nativeTranscribe(JNIEnv *env, jclass, jlong context,
                                                                   jfloatArray samples, jint threads,
                                                                   jint audioCtx, jint maxTokens) {
    auto *ctx = reinterpret_cast<whisper_context *>(context);

    whisper_full_params params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
    params.n_threads = threads;
    params.audio_ctx = audioCtx;   // encode only as much of the 30 s window as the clip needs
    params.max_tokens = maxTokens; // a shortened window can make Whisper repeat itself until stopped
    params.single_segment = true;
    params.no_context = true;
    params.no_timestamps = true;
    params.temperature_inc = 0.0f; // no fallback retries: they cost seconds on the stick
    params.suppress_nst = true;
    params.print_progress = false;
    params.print_realtime = false;
    params.print_special = false;
    params.print_timestamps = false;

    jsize count = env->GetArrayLength(samples);
    jfloat *pcm = env->GetFloatArrayElements(samples, nullptr);
    int status = whisper_full(ctx, params, pcm, count);
    env->ReleaseFloatArrayElements(samples, pcm, JNI_ABORT);
    if (status != 0) {
        __android_log_print(ANDROID_LOG_ERROR, "RemoteDictation", "whisper_full failed: %d", status);
        return nullptr;
    }

    std::string text;
    for (int i = 0; i < whisper_full_n_segments(ctx); i++) {
        if (whisper_full_get_segment_no_speech_prob(ctx, i) > kNoSpeechThreshold) continue;
        text += whisper_full_get_segment_text(ctx, i);
    }
    jbyteArray result = env->NewByteArray(static_cast<jsize>(text.size()));
    env->SetByteArrayRegion(result, 0, static_cast<jsize>(text.size()), reinterpret_cast<const jbyte *>(text.data()));
    return result;
}
