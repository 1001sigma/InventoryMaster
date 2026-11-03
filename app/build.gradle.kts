import java.util.Properties
import org.jetbrains.kotlin.gradle.dsl.JvmTarget

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.3.12"
    kotlin("plugin.serialization") version "2.4.0" apply false
}

android {
    namespace = "com.example.inventorymaster"
    compileSdk = 35
    ndkVersion = "30.0.14904198"

    signingConfigs {
        // 签名凭据从 app/keystore.properties 读取（该文件已 gitignore，凭据不入库）
        val keystorePropsFile = file("keystore.properties")
        if (keystorePropsFile.exists()) {
            val keystoreProps = Properties().apply {
                keystorePropsFile.inputStream().use { load(it) }
            }
            create("release") {
                storeFile = file(keystoreProps.getProperty("storeFile", "inventorymaster.jks"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.example.inventorymaster"
        minSdk = 29
        targetSdk = 34
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"

        ndk {
            abiFilters += listOf("arm64-v8a", "armeabi-v7a")
        }

        externalNativeBuild {
            cmake {
                cppFlags += ""
                arguments += "-DANDROID_STL=c++_shared"
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = true
            signingConfig = signingConfigs.findByName("release")
            proguardFiles(
                getDefaultProguardFile("proguard-android-optimize.txt"),
                "proguard-rules.pro"
            )
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_11
        targetCompatibility = JavaVersion.VERSION_11
    }
    testOptions {
        unitTests {
            // 纯 JVM 单测中 android.util.Log 等框架方法返回默认值而非抛异常
            // （InventoryAiAgent 的日志路径，docs/AI_V3_REDESIGN.md §5）
            isReturnDefaultValues = true
        }
    }
    buildFeatures {
        compose = true
    }

    externalNativeBuild {
        cmake {
            path = file("src/main/cpp/CMakeLists.txt")
            version = "4.1.2"
        }
    }
}

// Kotlin 2.4：kotlinOptions DSL 已移除，jvmTarget 统一走 compilerOptions
kotlin {
    compilerOptions {
        jvmTarget.set(JvmTarget.JVM_11)
    }
}

composeCompiler {
    stabilityConfigurationFiles.addAll(
        rootProject.layout.projectDirectory.file("compose_compiler_config.conf")
    )
}

dependencies {

    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    implementation(libs.androidx.activity.compose)
    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.ui)
    implementation(libs.androidx.ui.graphics)
    implementation(libs.androidx.ui.tooling.preview)
    implementation(libs.androidx.material3)
    testImplementation(libs.junit)
    androidTestImplementation(libs.androidx.junit)
    androidTestImplementation(libs.androidx.espresso.core)
    androidTestImplementation(platform(libs.androidx.compose.bom))
    androidTestImplementation(libs.androidx.ui.test.junit4)
    debugImplementation(libs.androidx.ui.tooling)
    debugImplementation(libs.androidx.ui.test.manifest)
    // --- 数据库 Room ---
    // 2.7+：KSP2（KSP 2.3.x）兼容所需，2.6.1 在 KSP2 下报 "unexpected jvm signature V"
    val room_version = "2.7.2"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // 支持协程
    ksp("androidx.room:room-compiler:$room_version")     // 编译器
    // --- 后面会用到的 ViewModel 和 Navigation ---
    implementation("androidx.lifecycle:lifecycle-viewmodel-compose:2.6.2")
    implementation("androidx.navigation:navigation-compose:2.7.5")
    // --- Excel 处理库 (Apache POI) ---
    implementation("org.apache.poi:poi:5.2.3")
    implementation("org.apache.poi:poi-ooxml:5.2.3")
    // --- [新增] CameraX & ML Kit ---
    val cameraxVersion = "1.3.0"
    implementation("androidx.camera:camera-core:$cameraxVersion")
    implementation("androidx.camera:camera-camera2:$cameraxVersion")
    implementation("androidx.camera:camera-lifecycle:$cameraxVersion")
    implementation("androidx.camera:camera-view:$cameraxVersion")
    implementation("androidx.camera:camera-video:$cameraxVersion")
    // Google ML Kit 条码扫描
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // 图标扩展 (如果还没加的话)
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.compose.material:material-icons-extended:1.5.4") // 版本号跟随你的 compose 版本
    implementation("androidx.compose.material3:material3")
    //网络请求库 Retrofit + Gson 转换器
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    //Gson 库 (处理 JSON)
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.google.zxing:core:3.5.2")
    // 👇 [新增] ML Kit 文字识别 (通用拉丁字母版，支持英文/数字)
    // 如果你需要识别中文，请改用: 'com.google.mlkit:text-recognition-chinese:16.0.0'
    implementation("com.google.mlkit:text-recognition-chinese:16.0.1")

    // 👇 [新增] 配合相册选图，我们需要加载图片的库 (如果你还没有 coil 或 glide)
    implementation("io.coil-kt:coil-compose:2.4.0")
    // Jieba 分词 (Java 版)
    implementation ("com.huaban:jieba-analysis:1.0.2")
    // DataStore Preferences
    implementation("androidx.datastore:datastore-preferences:1.2.0")

    // 👇 3. 新增：kotlinx.serialization 的 JSON 核心解析库
    implementation("org.jetbrains.kotlinx:kotlinx-serialization-json:1.6.3")

    implementation ("com.itextpdf:itext7-core:7.2.5")

    // OkHttp (AI 云端 API 调用)
    implementation("com.squareup.okhttp3:okhttp:4.12.0")

    // ========== OpenCV 视觉模块 ==========
    // 官方 OpenCV Android SDK（Maven Central）
    implementation("org.opencv:opencv:4.10.0")

    // ========== ONNX Runtime (YOLO 推理) ==========
    implementation("com.microsoft.onnxruntime:onnxruntime-android:1.17.0")

    // ========== 本地大模型推理 (LiteRT-LM, Gemma 4 E2B / Qwen3-4B) ==========
    implementation("com.google.ai.edge.litertlm:litertlm-android:0.17.1")

    // ========== Markdown 渲染 ==========
    // 0.31.0 起表格单元格 weight(1f) 等分列宽（0.28.0 的 weight 填充块模型导致表头与数据错位）
    implementation("com.mikepenz:multiplatform-markdown-renderer-m3:0.31.0")
}