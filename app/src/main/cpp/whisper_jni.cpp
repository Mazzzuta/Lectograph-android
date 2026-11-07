#include <jni.h>
#include <whisper.h>
#include <atomic>
#include <algorithm>
#include <cstring>
#include <vector>

struct Engine {
    whisper_context * context;
    std::atomic<bool> cancelled{false};
};

extern "C" JNIEXPORT jlong JNICALL
Java_ru_lectograph_app_transcription_WhisperEngine_load(JNIEnv * env, jobject, jstring path) {
    const char * file = env->GetStringUTFChars(path, nullptr);
    if (!file) return 0;
    auto params = whisper_context_default_params();
    params.use_gpu = false;
    auto * context = whisper_init_from_file_with_params(file, params);
    env->ReleaseStringUTFChars(path, file);
    if (!context) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Не удалось загрузить Whisper base");
        return 0;
    }
    return reinterpret_cast<jlong>(new Engine{context});
}

extern "C" JNIEXPORT void JNICALL
Java_ru_lectograph_app_transcription_WhisperEngine_cancelNative(JNIEnv *, jobject, jlong handle) {
    reinterpret_cast<Engine *>(handle)->cancelled = true;
}

extern "C" JNIEXPORT void JNICALL
Java_ru_lectograph_app_transcription_WhisperEngine_free(JNIEnv *, jobject, jlong handle) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    whisper_free(engine->context);
    delete engine;
}

extern "C" JNIEXPORT void JNICALL
Java_ru_lectograph_app_transcription_WhisperEngine_decode(JNIEnv * env, jobject, jlong handle, jfloatArray audio, jobject listener) {
    auto * engine = reinterpret_cast<Engine *>(handle);
    auto cls = env->GetObjectClass(listener);
    jmethodID progress = env->GetMethodID(cls, "onProgress", "(I)V");
    jmethodID segment = env->GetMethodID(cls, "onSegment", "(JJ[B)V");
    if (env->ExceptionCheck()) return;
    struct Callback { JNIEnv * env; jobject listener; jmethodID method; Engine * engine; } callback{env, listener, progress, engine};
    try {
        std::vector<float> samples(env->GetArrayLength(audio));
        env->GetFloatArrayRegion(audio, 0, static_cast<jsize>(samples.size()), samples.data());
        if (env->ExceptionCheck()) return;
        auto params = whisper_full_default_params(WHISPER_SAMPLING_GREEDY);
        params.n_threads = 4;
        params.language = "ru";
        params.translate = false;
        params.no_context = true;
        params.print_progress = false;
        params.print_realtime = false;
        params.print_timestamps = false;
        params.print_special = false;
        params.suppress_blank = true;
        params.suppress_nst = true;
        params.abort_callback = [](void * data) { return static_cast<Engine *>(data)->cancelled.load(); };
        params.abort_callback_user_data = engine;
        params.progress_callback = [](whisper_context *, whisper_state *, int value, void * data) {
            auto * c = static_cast<Callback *>(data);
            c->env->CallVoidMethod(c->listener, c->method, value);
            if (c->env->ExceptionCheck()) c->engine->cancelled = true;
        };
        params.progress_callback_user_data = &callback;
        int result = whisper_full(engine->context, params, samples.data(), static_cast<int>(samples.size()));
        if (env->ExceptionCheck()) return;
        if (engine->cancelled) {
            env->ThrowNew(env->FindClass("java/util/concurrent/CancellationException"), "Обработка отменена");
            return;
        }
        if (result != 0) {
            env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Ошибка распознавания Whisper");
            return;
        }
        for (int i = 0; i < whisper_full_n_segments(engine->context); ++i) {
            const char * text = whisper_full_get_segment_text(engine->context, i);
            auto length = static_cast<jsize>(std::strlen(text));
            jbyteArray bytes = env->NewByteArray(length);
            if (!bytes) return;
            env->SetByteArrayRegion(bytes, 0, length, reinterpret_cast<const jbyte *>(text));
            env->CallVoidMethod(listener, segment, whisper_full_get_segment_t0(engine->context, i) * 10,
                                whisper_full_get_segment_t1(engine->context, i) * 10, bytes);
            env->DeleteLocalRef(bytes);
            if (env->ExceptionCheck()) return;
        }
    } catch (const std::exception &) {
        env->ThrowNew(env->FindClass("java/lang/IllegalStateException"), "Недостаточно памяти для распознавания");
    }
}
