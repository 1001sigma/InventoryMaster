package com.example.inventorymaster.utils

object LibDmtxNative {
    init {
        System.loadLibrary("dmtx_wrapper")
    }

    external fun nativeDecode(pixels: ByteArray, width: Int, height: Int): Array<String>
}
