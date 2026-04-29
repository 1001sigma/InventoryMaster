package com.example.inventorymaster.utils

import android.content.Context
import android.graphics.Bitmap
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanner
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage
import org.json.JSONArray
import org.json.JSONObject

class QRCodeScannerUtil(
    private val context: Context
) {

    private val barcodeScanner: BarcodeScanner = BarcodeScanning.getClient()

    // 从Bitmap中解析二维码
    fun decodeQRCodesFromBitmap(image: Bitmap, callback: (List<Barcode>) -> Unit) {
        val inputImage = InputImage.fromBitmap(image, 0)

        barcodeScanner.process(inputImage)
            .addOnSuccessListener { barcodes ->
                callback(barcodes)  // 识别结果通过回调返回
            }
            .addOnFailureListener { e ->
                Log.e("QRCodeScanner", "二维码扫描失败: ${e.localizedMessage}")
                callback(emptyList())  // 扫描失败，返回空列表
            }
    }

    // 将扫描结果转为JSON格式
    fun convertToJson(barcodes: List<Barcode>): String {
        val resultList = mutableListOf<JSONObject>()

        barcodes.forEach { barcode ->
            val jsonObject = JSONObject()
            jsonObject.put("data", barcode.displayValue)  // 二维码内容
            jsonObject.put("coordinates", barcode.cornerPoints)  // 二维码坐标
            resultList.add(jsonObject)
        }

        val jsonArray = JSONArray(resultList)
        return jsonArray.toString()  // 返回 JSON 格式的结果
    }
}