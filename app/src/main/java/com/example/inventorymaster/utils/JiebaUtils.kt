package com.example.inventorymaster.utils

import android.content.Context
import android.net.Uri
import android.util.Log
import com.huaban.analysis.jieba.JiebaSegmenter
import com.huaban.analysis.jieba.WordDictionary
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.nio.file.Paths

object JiebaUtils {

    // 懒加载单例
    private val segmenter by lazy { JiebaSegmenter() }
    private var isDictLoaded = false
    private val SIMPLE_SPLIT_REGEX = Regex("""[\s,，.。;；:：!！?？、|()\[\]{}<>"'`~]+""")

    /**
     * 初始化：将 assets 中的字典拷贝出来并加载
     * 建议在 Application 的 onCreate 中调用，或者在进入识别界面前调用
     */
    fun init(context: Context) {
        if (isDictLoaded) return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                // 1. 定义目标路径：/data/user/0/包名/cache/user_dict.txt
                val fileName = "user_dict.txt"
                val cacheFile = File(context.cacheDir, fileName)

                // 2. 检查文件是否存在，不存在则从 assets 拷贝
                // (每次启动都覆盖一次也可以，防止你更新了字典但手机没更新)
                context.assets.open(fileName).use { inputStream ->
                    cacheFile.outputStream().use { outputStream ->
                        inputStream.copyTo(outputStream)
                    }
                }

                // 3. 加载自定义字典
                // WordDictionary 需要的是一个 Path 对象
                val path = Paths.get(cacheFile.absolutePath)
                WordDictionary.getInstance().loadUserDict(path)

                isDictLoaded = true
                Log.d("JiebaUtils", "自定义词典加载成功！路径: ${cacheFile.absolutePath}")

            } catch (e: Exception) {
                e.printStackTrace()
                Log.e("JiebaUtils", "自定义词典加载失败: ${e.message}")
                // 失败也不影响，只是只能用默认分词了
            }
        }
    }

    /**
     * 分词方法
     */
    suspend fun cut(text: String,useSimpleMode: Boolean = false): List<String> {
        return withContext(Dispatchers.Default) {
            // === 分支 1：简单模式 (正则切分) ===
            if (useSimpleMode) {
                // 1. split(REGEX): 根据上面定义的符号列表进行切割。
                //    例如 "LOT: 12345" 会被切成 ["LOT", "", "12345"] (中间的空字符串是因为冒号后面有个空格)
                // 2. filter { it.isNotBlank() }: 过滤掉那些切出来的空字符串。
                // 3. map { it.trim() }: 把切出来的每个词两头的隐形空格再洗一遍，确保干净。
                return@withContext text.split(SIMPLE_SPLIT_REGEX)
                    .filter { it.isNotBlank() } // 只要有内容的
                    .map { it.trim() }          // 去掉首尾残留空格
            }

            // === 分支 2：专业模式 (Jieba 分词) ===
            try {
                // 只有这里才会用到 segmenter，没加载字典也没事，会用默认的
                val tokens = segmenter.process(text, JiebaSegmenter.SegMode.SEARCH)
                tokens.map { it.word.trim() }
                    .filter { it.length > 1 } // 过滤掉单字
                    .distinct() // 去重
            } catch (e: Exception) {
                e.printStackTrace()
                // 如果 Jieba 崩了，自动降级用空格切分，保证 APP 不闪退
                text.split(" ").filter { it.isNotBlank() }
            }
        }
    }

    /**
     * 更新词典（从外部 Uri 导入）
     */
    suspend fun updateDict(context: Context, uri: Uri) {
        withContext(Dispatchers.IO) {
            try {
                // 1. 找到目标文件位置
                val targetFile = File(context.cacheDir, "user_dict.txt")

                // 2. 将用户选中的文件内容，覆盖写入到目标文件
                context.contentResolver.openInputStream(uri)?.use { input ->
                    targetFile.outputStream().use { output ->
                        input.copyTo(output)
                    }
                }

                // 3. 强制重新加载
                reload(targetFile)

                Log.d("JiebaUtils", "词典更新成功！")
            } catch (e: Exception) {
                e.printStackTrace()
                throw e // 抛出异常给 UI 层处理
            }
        }
    }

    /**
     * 重新加载逻辑
     */
    private fun reload(file: File) {
        // Jieba 没有提供“卸载”方法，但 loadUserDict 是增量加载的。
        // 如果想彻底替换，最好是重新初始化 segmenter，或者重启 APP。
        // 这里我们采用最简单的策略：再次加载新词（新词覆盖旧词权重）
        val path = Paths.get(file.absolutePath)
        WordDictionary.getInstance().loadUserDict(path)
        isDictLoaded = true
    }
}
