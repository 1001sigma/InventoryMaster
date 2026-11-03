#include <jni.h>
#include <dmtx.h>
#include <android/log.h>
#include <cstring>

#define LOG_TAG "LibDmtxNative"
#define LOGI(...) __android_log_print(ANDROID_LOG_INFO, LOG_TAG, __VA_ARGS__)
#define LOGE(...) __android_log_print(ANDROID_LOG_ERROR, LOG_TAG, __VA_ARGS__)

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_inventorymaster_utils_LibDmtxNative_nativeDecode(
    JNIEnv *env, jobject thiz,
    jbyteArray pixels, jint width, jint height) {

    jbyte* bufferPtr = env->GetByteArrayElements(pixels, NULL);
    if (bufferPtr == NULL) {
        LOGE("Failed to get byte array elements");
        return NULL;
    }

    DmtxImage *img = dmtxImageCreate(
        (unsigned char*)bufferPtr, width, height,
        DmtxPack8bppK);
    if (img == NULL) {
        LOGE("Failed to create DmtxImage");
        env->ReleaseByteArrayElements(pixels, bufferPtr, 0);
        return NULL;
    }

    DmtxDecode *dec = dmtxDecodeCreate(img, 1);
    if (dec == NULL) {
        LOGE("Failed to create DmtxDecode");
        dmtxImageDestroy(&img);
        env->ReleaseByteArrayElements(pixels, bufferPtr, 0);
        return NULL;
    }

    dmtxDecodeSetProp(dec, DmtxPropEdgeMin, 10);
    dmtxDecodeSetProp(dec, DmtxPropEdgeMax, 30);

    jclass stringClass = env->FindClass("java/lang/String");
    jobjectArray results = env->NewObjectArray(0, stringClass, NULL);
    int count = 0;
    int capacity = 4;

    DmtxRegion *reg;
    while ((reg = dmtxRegionFindNext(dec, NULL)) != NULL) {
        DmtxMessage *msg = dmtxDecodeMatrixRegion(dec, reg, DmtxUndefined);
        if (msg != NULL && msg->output != NULL) {
            if (count >= capacity) {
                capacity *= 2;
                jobjectArray newArray = env->NewObjectArray(capacity, stringClass, NULL);
                for (int i = 0; i < count; i++) {
                    jobject obj = env->GetObjectArrayElement(results, i);
                    env->SetObjectArrayElement(newArray, i, obj);
                    env->DeleteLocalRef(obj);
                }
                env->DeleteLocalRef(results);
                results = newArray;
            }

            jstring resultStr = env->NewStringUTF((const char*)msg->output);
            env->SetObjectArrayElement(results, count, resultStr);
            env->DeleteLocalRef(resultStr);
            count++;

            dmtxMessageDestroy(&msg);
        }
        dmtxRegionDestroy(&reg);
    }

    LOGI("libdmtx decoded %d DataMatrix codes", count);

    dmtxDecodeDestroy(&dec);
    dmtxImageDestroy(&img);
    env->ReleaseByteArrayElements(pixels, bufferPtr, 0);

    return results;
}
