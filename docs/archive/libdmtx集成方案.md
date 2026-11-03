总体架构规划

我们将采用 JNI/NDK + 现有责任链 的架构：

底层引擎： 将 libdmtx 的 C 源码通过 Android NDK 编译为 .so 动态链接库。

JNI 桥梁： 编写一段极简的 C++ 代码，接收 OpenCV 传过来的灰度图像像素数组，调用 libdmtx 解码，并将字符串返回给 Java/Kotlin。

Kotlin 包装层： 新建 LibDmtxDecoder 类实现 BarcodeDecoder 接口。

管线注册： 在 VisionManager 中将其注册为 ML Kit 失败后的第二道防线。

第一阶段：底层 C/C++ 库集成 (NDK)

因为 libdmtx 原生是 C 代码，我们需要在 Android Studio 中启用 NDK（如果你还没有的话）。

准备源码： 从 GitHub 下载 libdmtx 的核心源码（主要是 dmtx.c 和 dmtx.h 等文件）。

配置 CMake： 在你的 Android 项目 app/src/main/cpp/ 目录下创建 CMakeLists.txt，将 libdmtx 编译进来。

编写 JNI 接口： 创建一个 libdmtx_wrapper.cpp，暴露一个对外的解码函数。由于你项目中大量使用了 OpenCV，最优雅的方式是直接传入 OpenCV Mat 的底层内存指针或 Byte 数组。

JNI 核心逻辑伪代码：
extern "C" JNIEXPORT jstring JNICALL
Java_com_example_inventorymaster_utils_LibDmtxNative_decodeNative(
JNIEnv* env, jobject, jbyteArray pixels, jint width, jint height) {

    // 1. 将 byte 数组转为 C 数组
    jbyte* bufferPtr = env->GetByteArrayElements(pixels, NULL);
    
    // 2. 初始化 libdmtx 图像结构
    DmtxImage *img = dmtxImageCreate((unsigned char*)bufferPtr, width, height, DmtxPack8bppK);
    
    // 3. 配置解码参数 (可以设置超时时间，防止在烂图上死循环)
    DmtxDecode *dec = dmtxDecodeCreate(img);
    // ... 配置诸如 dmtxDecodeSetProp(dec, DmtxPropEdgeMin, 10) 等参数 ...
    
    // 4. 执行解码
    DmtxRegion *reg = dmtxRegionFindNext(dec, NULL);
    jstring resultStr = NULL;
    if (reg != NULL) {
        DmtxMessage *msg = dmtxDecodeMatrixRegion(dec, reg, DmtxUndefined);
        if (msg != NULL) {
            resultStr = env->NewStringUTF((char*)msg->output);
            dmtxMessageDestroy(&msg);
        }
        dmtxRegionDestroy(&reg);
    }
    
    // 5. 释放资源
    dmtxDecodeDestroy(&dec);
    dmtxImageDestroy(&img);
    env->ReleaseByteArrayElements(pixels, bufferPtr, 0);
    
    return resultStr;
}
第二阶段：Kotlin 包装层实现

在 Kotlin 侧，我们需要建立 JNI 的 Native 方法声明，并实现你项目中的 BarcodeDecoder 接口。

1. 创建 Native 调用类：
   package com.example.inventorymaster.utils

object LibDmtxNative {
init {
System.loadLibrary("dmtx_wrapper") // 对应你在 CMake 中定义的库名
}

    // 传入单通道灰度图的 byte 数组
    external fun decodeNative(pixels: ByteArray, width: Int, height: Int): String?
}
2. 实现 Decoder 接口：

新建 LibDmtxDecoder.kt：
package com.example.inventorymaster.utils

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.imgproc.Imgproc

class LibDmtxDecoder : BarcodeDecoder {

    override val engineName = "libdmtx"

    override suspend fun decode(bitmap: Bitmap): List<DecodedBarcode> {
        // libdmtx 处理速度较慢，千万不要传 4K 原图！
        // 确保传入的是 OpenCV 已经裁剪并增强好的小尺寸 ROI
        
        // 1. 将 Bitmap 转为单通道灰度 Mat (libdmtx 处理灰度图最快)
        val mat = Mat()
        Utils.bitmapToMat(bitmap, mat)
        val gray = Mat()
        Imgproc.cvtColor(mat, gray, Imgproc.COLOR_RGBA2GRAY)
        
        // 2. 提取连续的像素数据
        val size = (gray.total() * gray.channels()).toInt()
        val buffer = ByteArray(size)
        gray.get(0, 0, buffer)
        
        // 3. 调用底层 C++ 解码
        val result = LibDmtxNative.decodeNative(buffer, gray.cols(), gray.rows())
        
        // 4. 释放 OpenCV 资源
        mat.release()
        gray.release()
        
        // 5. 封装为你定义的返回格式
        return if (!result.isNullOrEmpty()) {
            listOf(
                DecodedBarcode(
                    value = result,
                    format = 256, // 可以自定义一个常量代表 DataMatrix
                    boundingBox = null, // libdmtx 找框比较复杂，我们主要依赖它的解码结果，框可以用外层 OpenCV 传来的
                    confidence = 1f
                )
            )
        } else {
            emptyList()
        }
    }
}
第三阶段：注册进入责任链

这里就能体现出你之前设计的 DecoderChain 架构有多么优越了。你只需要在 VisionManager 实例化 DecoderChain 的地方，将 LibDmtxDecoder 追加到列表中即可：
// VisionManager.kt

class VisionManager(
private val locator: BarcodeLocator = BarcodeLocator(),
// 核心修改：在责任链中加入 LibDmtxDecoder
private val decoderChain: DecoderChain = DecoderChain(
listOf(
MlKitDecoder(),       // 梯队 1：极其快速，解决 95% 场景
LibDmtxDecoder()      // 梯队 2：ML Kit 失败后的兜底，专攻反光/形变的硬骨头
)
)
) { ... }
由于你的 DecoderChain 里的 decodeWithFallback 方法是遍历 decoders 并返回首个成功的结果，只有当 ML Kit 解码失败时（比如遇到极度模糊的塑料袋），libdmtx 才会被唤醒去死磕这张烂图。

⚠️ 避坑指南（关键注意事项）

绝对禁止喂全图： libdmtx 的算法复杂度比 ML Kit 高得多，如果把 4K 原图直接丢给它，手机可能会卡死好几秒。它必须只作为 OpenCV 切出来的“局部小图（ROI）”的解码器。

超时控制： 在 C++ 层的代码中，libdmtx 提供了超时参数设置。建议将其设置为 200ms 或 300ms。如果在这个时间内解不出来，就直接放弃，不要让它阻塞主线程