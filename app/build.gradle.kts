import java.util.Properties

plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

// release 签名：仓库根存在 keystore.properties（gitignore，用户本地自建，口令不入库不经手
// 任何外部流程）时用其密钥出包；缺失时回退 debug 签名。storeFile 支持相对仓库根或绝对路径。
val keystorePropsFile = rootProject.file("keystore.properties")
val keystoreProps = Properties().apply {
    if (keystorePropsFile.exists()) keystorePropsFile.inputStream().use { load(it) }
}

android {
    namespace = "com.moji.liquidglass"
    compileSdk {
        version = release(37) {
            minorApiLevel = 1
        }
    }

    signingConfigs {
        if (keystorePropsFile.exists()) {
            create("release") {
                storeFile = rootProject.file(keystoreProps.getProperty("storeFile"))
                storePassword = keystoreProps.getProperty("storePassword")
                keyAlias = keystoreProps.getProperty("keyAlias")
                keyPassword = keystoreProps.getProperty("keyPassword")
            }
        }
    }

    defaultConfig {
        applicationId = "com.moji.liquidglass"
        minSdk = 33
        targetSdk = 37
        versionCode = 117
        versionName = "2.9.88"
    }

    buildTypes {
        release {
            signingConfig = signingConfigs.getByName(
                if (keystorePropsFile.exists()) "release" else "debug"
            )
            // 正式版：启用 R8 代码收缩 + 资源收缩
            // keep 规则见 src/main/keepRules/rules.keep（Xposed 入口必须保名）
            optimization {
                enable = true
            }
        }
    }
    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    buildFeatures {
        compose = true
    }
    lint {
        abortOnError = false
        checkReleaseBuilds = false
    }
}

dependencies {
    // libxposed 现代 API：仅编译期可见（严禁打包——LSPosed 检测到 APK 内含 API 类会拒绝加载模块）
    compileOnly(libs.libxposed.api)
    // service 库必须打包：XposedProvider 声明在本应用 manifest 中，缺失会启动崩溃
    implementation(libs.libxposed.service)
    // 经典 Xposed API 桩：仅编译期可见（同上，严禁打包）
    compileOnly(files("libs/xposed-api-82-stub.jar"))
    // 液态玻璃效果库（参考项目 Kyant0/AndroidLiquidGlass 的发布产物）
    implementation(libs.kyant.backdrop)
    // shapes 在 backdrop 的 POM 中是 runtime 作用域，编译期需要显式声明（Capsule 形状）
    implementation(libs.kyant.shapes)

    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.foundation)
    implementation(libs.androidx.compose.animation)
    implementation(libs.androidx.compose.material3)
    implementation(libs.androidx.compose.material.icons.extended)
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.core.ktx)
    implementation(libs.androidx.lifecycle.runtime.ktx)
    // COUI-Kit：管理器本体 UI（核心 + Material 3 适配）
    implementation(libs.coui)
    implementation(libs.coui.icons)
    implementation(libs.coui.material3)
}
