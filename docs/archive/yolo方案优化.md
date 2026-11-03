1. 核心矛盾：缩略图 vs 高分辨率精度

当前方案：设计中第一步是提取缩略图进行推理（缩略图 → YoloDetector.detect()）。

隐患：在处理需要极高准确率的高分辨率静态原图时，直接缩放为 640×640 会导致微小二维码的像素特征被完全抹平，YOLO 根本无法提取特征。

优化方案：必须在这个阶段引入**滑动窗口（Sliding Window）**机制。不要将原图直接 resize 成一张图，而是将原图按一定重叠率（例如 20%）切割成多个 640×640 的区块，循环送入模型推理。

2. 预处理性能瓶颈

当前方案：计划使用 Bitmap → pixels → float[] / 255.0f 进行输入预处理。

隐患：640×640 的图像包含 120 多万个通道数据。在纯 Kotlin 层使用 for 循环进行像素拆解和浮点除法运算非常耗时，可能会造成 50ms-150ms 的额外延迟，这在批量扫描或滑动窗口多次推理时会成为严重瓶颈。

优化方案：既然项目中已经存在 C++ native 代码（libdmtx）以及相关的 JNI 环境，强烈建议将“像素提取、通道分离（NCHW）、归一化”这一步下沉到 JNI 层，用 C++ 的指针操作来完成，速度会快几个数量级。

3. 全局 NMS（非极大值抑制）的设计

当前方案：计划用 Kotlin 实现标准的 NMS。

补充点：如果结合了上述的滑动窗口机制，单个切片内的 NMS 是不够的。因为同一个二维码很可能跨越两个切片的重叠区，从而被检测出两次。

优化方案：需要执行全局 NMS。具体的做法是，先将所有切片中得到的 Detection.bbox 映射回原图的绝对坐标，收集到一个全局的 List<Detection> 中，最后再统一跑一次 NMS 进行去重。

路径A增强方案：C++ 底层推理 + 并行解码 + 透视校正

架构设计核心思想

内存隔离：彻底剥离 Java/Kotlin 层的 Bitmap 依赖。CameraX 捕获的高分辨率图像直接以 YUV/NV21 字节流形式传入 C++ 层，在 C++ 层的 cv::Mat 中完成全生命周期的预处理、YOLO 推理和内存裁剪。

算力压榨：YOLO 圈出多个 ROI（感兴趣区域）后，将裁剪后的局部数据以轻量级 byte[] 返回 Kotlin 层，利用协程并发多路分发给 Google ML Kit 进行解码。

抗畸变降级：预留 YOLOv8-Pose 关键点检测能力，针对倾斜严重的高分辨率扫码场景，执行物理级的图像“拉平”。

全新流水线 (Pipeline)

CameraX capture()
└── 1. 传入 YUV 字节流至 JNI (C++)
├── 原图转为全局 cv::Mat (缓存在原生层内存)
├── ONNX Runtime (C++ API) 执行推理 (结合滑动窗口)
├── 获取 BBox (或 4 个角点)
├── 在原图 cv::Mat 上执行裁剪 (ROI) 或 透视变换 (warpPerspective)
└── 提取裁剪后的小图，转换为独立 byte[] 数组返回
└── 2. 返回 Kotlin 层的 List<ByteArray>
├── Dispatchers.Default 协程池接管
├── 并行构建 InputImage.fromByteArray(...)
├── 并行分发给 ML Kit / DecoderChain 执行解码
└── 汇总解码结果并更新 UI
核心实现步骤与代码骨架
Step 1: JNI 原生层处理与 ONNX 推理 (C++)
文件: app/src/main/cpp/native-vision.cpp
在 C++ 层接入 OpenCV 和 ONNX Runtime C++ API。通过 JNI 接收高分辨率原图数据，并在内存中完成裁剪。
#include <jni.h>
#include <opencv2/opencv.hpp>
#include <onnxruntime_cxx_api.h>

extern "C" JNIEXPORT jobjectArray JNICALL
Java_com_example_inventorymaster_utils_NativeVision_processAndCrop(
JNIEnv* env, jobject /* this */, jbyteArray yuvData, jint width, jint height) {

    // 1. 将 Java 字节流转为 OpenCV Mat (零拷贝或极低开销)
    jbyte* pYuv = env->GetByteArrayElements(yuvData, NULL);
    cv::Mat yuvMat(height + height / 2, width, CV_8UC1, (unsigned char*)pYuv);
    cv::Mat rgbMat;
    cv::cvtColor(yuvMat, rgbMat, cv::COLOR_YUV2RGB_NV21);
    env->ReleaseByteArrayElements(yuvData, pYuv, JNI_ABORT);

    // 2. ONNX Runtime 推理 (伪代码: 执行滑动窗口或全局检测)
    // std::vector<Detection> detections = runYoloInference(rgbMat);
    
    // 准备返回给 Java 的二维 byte 数组 (List<byte[]>)
    jclass byteArrayClass = env->FindClass("[B");
    jobjectArray resultList = env->NewObjectArray(detections.size(), byteArrayClass, NULL);

    // 3. 直接在 C++ 内存中裁剪并转换
    for (size_t i = 0; i < detections.size(); i++) {
        cv::Rect roi(detections[i].x, detections[i].y, detections[i].w, detections[i].h);
        cv::Mat croppedMat = rgbMat(roi);
        
        // 编码为独立的 byte 数组传回 Kotlin 给 ML Kit 用
        std::vector<uchar> buffer;
        cv::imencode(".jpg", croppedMat, buffer); 
        
        jbyteArray jBuffer = env->NewByteArray(buffer.size());
        env->SetByteArrayRegion(jBuffer, 0, buffer.size(), (jbyte*)buffer.data());
        env->SetObjectArrayElement(resultList, i, jBuffer);
        env->DeleteLocalRef(jBuffer);
    }
    
    return resultList; // 返回纯粹的小图字节流
}
Step 2: Kotlin 协程并行解码
文件: app/src/main/java/com/example/inventorymaster/utils/VisionManager.kt
拿到原生层裁剪好的多个局部图像字节流后，切断传统的 for 循环同步等待，使用 async 并发。
import kotlinx.coroutines.*
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.barcode.BarcodeScanning

class VisionManager {
private val barcodeScanner = BarcodeScanning.getClient()

    suspend fun scanHighResImage(yuvData: ByteArray, width: Int, height: Int): List<String> {
        return withContext(Dispatchers.Default) {
            // 1. 触发 JNI C++ 层的 YOLO 定位与内存裁剪
            // 返回的是已经被裁剪好的 N 个二维码区域的 jpeg 字节流
            val croppedImagesData: Array<ByteArray> = NativeVision.processAndCrop(yuvData, width, height)

            // 2. 并发处理所有裁剪出的 ROI
            val deferredResults = croppedImagesData.map { imageData ->
                async {
                    decodeSingleImage(imageData)
                }
            }

            // 3. 等待所有解码任务完成并过滤无效结果
            deferredResults.awaitAll().filterNotNull()
        }
    }

    private suspend fun decodeSingleImage(imageData: ByteArray): String? = suspendCancellableCoroutine { continuation ->
        // 从 ByteArray 重构供 ML Kit 使用的 InputImage，无需生成 Bitmap
        val inputImage = InputImage.fromByteArray(
            imageData,
            /* width */ ..., /* height */ ..., // 需由 JNI 附带返回宽高或使用 Bitmap.decodeByteArray 辅助
            0, // rotation
            InputImage.IMAGE_FORMAT_JPEG
        )

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                if (barcodes.isNotEmpty()) {
                    continuation.resume(barcodes[0].rawValue)
                } else {
                    continuation.resume(null)
                }
            }
            .addOnFailureListener {
                continuation.resume(null)
            }
    }
}
Step 3: 透视变换（高级备用方案 YOLOv8-Pose）
如果常规 BBox 裁剪后的解码率不达标，替换 assets 中的模型为 YOLOv8-Pose（原用于人体骨骼关键点，可重新训练为输出二维码的 4 个角点）。
在 native-vision.cpp 的遍历阶段，将 cv::Rect roi 替换为透视拉平逻辑：
// 假设模型输出了二维码的 4 个角点坐标: pts[4]
std::vector<cv::Point2f> srcPoints = { pts[0], pts[1], pts[2], pts[3] };

// 定义拉平后标准正方形的 4 个角点 (例如拉平为 300x300 像素)
std::vector<cv::Point2f> dstPoints = {
cv::Point2f(0, 0),
cv::Point2f(300, 0),
cv::Point2f(300, 300),
cv::Point2f(0, 300)
};

// 计算透视变换矩阵
cv::Mat transformMatrix = cv::getPerspectiveTransform(srcPoints, dstPoints);
cv::Mat warpedMat;

// 对原图执行透视变换拉平
cv::warpPerspective(rgbMat, warpedMat, transformMatrix, cv::Size(300, 300));

// 将拉平后的 warpedMat 转为 byte[] 交给 Kotlin 协程并行解码...