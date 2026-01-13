plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.android)
    alias(libs.plugins.kotlin.compose)
    id("com.google.devtools.ksp") version "2.0.21-1.0.28"
}

android {
    namespace = "com.example.inventorymaster"
    compileSdk = 35

    defaultConfig {
        applicationId = "com.example.inventorymaster"
        minSdk = 29
        targetSdk = 35
        versionCode = 1
        versionName = "1.0"

        testInstrumentationRunner = "androidx.test.runner.AndroidJUnitRunner"
    }

    buildTypes {
        release {
            isMinifyEnabled = false
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
    kotlinOptions {
        jvmTarget = "11"
    }
    buildFeatures {
        compose = true
    }
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
    val room_version = "2.6.1"
    implementation("androidx.room:room-runtime:$room_version")
    implementation("androidx.room:room-ktx:$room_version") // 支持协程
    ksp("androidx.room:room-compiler:$room_version")     // 编译器

    // --- JSON 解析 Gson ---
    implementation("com.google.code.gson:gson:2.10")

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
    // Google ML Kit 条码扫描
    implementation("com.google.mlkit:barcode-scanning:17.2.0")
    // 图标扩展 (如果还没加的话)
    implementation("androidx.compose.material:material-icons-extended:1.5.4")
    implementation("androidx.lifecycle:lifecycle-runtime-compose:2.8.4")
    implementation("androidx.compose.material:material-icons-extended:1.5.4") // 版本号跟随你的 compose 版本
    //网络请求库 Retrofit + Gson 转换器
    implementation("com.squareup.retrofit2:retrofit:2.9.0")
    implementation("com.squareup.retrofit2:converter-gson:2.9.0")

    //Gson 库 (处理 JSON)
    implementation("com.google.code.gson:gson:2.10.1")

    implementation("com.google.zxing:core:3.5.2")
    // 👇 [新增] ML Kit 文字识别 (通用拉丁字母版，支持英文/数字)
    // 如果你需要识别中文，请改用: 'com.google.mlkit:text-recognition-chinese:16.0.0'
    implementation("com.google.mlkit:text-recognition-chinese:16.0.0")

    // 👇 [新增] 配合相册选图，我们需要加载图片的库 (如果你还没有 coil 或 glide)
    implementation("io.coil-kt:coil-compose:2.4.0")
}