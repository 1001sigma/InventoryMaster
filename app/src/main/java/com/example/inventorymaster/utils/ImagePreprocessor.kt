package com.example.inventorymaster.utils

import org.opencv.core.Core
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc

/**
 * 图像手术刀
 * 负责 CLAHE 局部均衡、USM 锐化、透视校正
 */
object ImagePreprocessor {

    /**
     * 条码增强管线：灰度 → CLAHE → USM 锐化
     * @param src 输入 Mat（BGR 或 BGRA）
     * @return 增强后的灰度 Mat（调用方需 release）
     */
    fun enhanceForBarcode(src: Mat): Mat {
        // 1. 灰度化
        val gray = Mat()
        if (src.channels() == 4) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGRA2GRAY)
        } else if (src.channels() == 3) {
            Imgproc.cvtColor(src, gray, Imgproc.COLOR_BGR2GRAY)
        } else {
            src.copyTo(gray)
        }

        // 2. CLAHE 局部直方图均衡 — 对抗光照不均
        val clahe = Imgproc.createCLAHE(2.0, Size(8.0, 8.0))
        val enhanced = Mat()
        clahe.apply(gray, enhanced)
        gray.release()

        // 3. Unsharp Mask (USM) 锐化 — 增强条码边缘
        val blurred = Mat()
        Imgproc.GaussianBlur(enhanced, blurred, Size(0.0, 0.0), 3.0)
        val sharpened = Mat()
        Core.addWeighted(enhanced, 1.5, blurred, -0.5, 0.0, sharpened)
        enhanced.release()
        blurred.release()

        return sharpened
    }

    /**
     * 双线性插值放大（替代 nearest-neighbor）
     * @param src 输入 Mat
     * @param scaleFactor 放大倍数
     * @return 放大后的 Mat（调用方需 release）
     */
    fun resizeWithInterpolation(src: Mat, scaleFactor: Double): Mat {
        val dst = Mat()
        Imgproc.resize(
            src, dst,
            Size(src.cols() * scaleFactor, src.rows() * scaleFactor),
            0.0, 0.0,
            Imgproc.INTER_CUBIC  // 双三次插值，边缘更平滑
        )
        return dst
    }

    /**
     * 透视校正：将倾斜的矩形条码校正为正面视角
     * @param src 输入 Mat
     * @param points 四个顶点（左上→右上→右下→左下）
     * @return 校正后的 Mat（调用方需 release）
     */
    fun perspectiveCorrect(src: Mat, points: Array<Point>): Mat {
        require(points.size == 4) { "需要4个顶点" }

        // 计算目标矩形尺寸
        val width = maxOf(
            distance(points[0], points[1]),
            distance(points[2], points[3])
        ).toInt()
        val height = maxOf(
            distance(points[0], points[3]),
            distance(points[1], points[2])
        ).toInt()

        val srcPoints = MatOfPoint2f(*points)
        val dstPoints = MatOfPoint2f(
            Point(0.0, 0.0),
            Point(width.toDouble(), 0.0),
            Point(width.toDouble(), height.toDouble()),
            Point(0.0, height.toDouble())
        )

        val M = Imgproc.getPerspectiveTransform(srcPoints, dstPoints)
        val warped = Mat()
        Imgproc.warpPerspective(src, warped, M, Size(width.toDouble(), height.toDouble()))
        M.release()
        srcPoints.release()
        dstPoints.release()

        return warped
    }

    private fun distance(a: Point, b: Point): Double =
        Math.hypot(a.x - b.x, a.y - b.y)
}
