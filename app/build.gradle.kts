// 「过词」 —— 高中 3500 词记忆 App 的 application 模块。
//
// AGP 9 注意事项（都不是风格选择）
//
// 1. 不应用 org.jetbrains.kotlin.android：AGP 9 内置 Kotlin 支持，应用它会直接
//    构建失败（"no longer required for Kotlin support since AGP 9.0"）。Kotlin
//    源码仍然放在 src/main/java 下，AGP 的默认源集包含它。
// 2. Compose 编译器插件必须显式应用：缺了会报 "Starting in Kotlin 2.0, the
//    Compose Compiler Gradle plugin is required when compose is enabled"。
// 3. AGP 9 换了两处 DSL：compileSdk {} + buildTypes { release { optimization {} } }。
//    写成 compileSdk = 37 / isMinifyEnabled 会失败。
plugins {
    alias(libs.plugins.android.application)
    alias(libs.plugins.kotlin.compose)
}

android {
    namespace = "com.guoci.vocab3500"

    // 本机 SDK 装有 platform 37 与 build-tools 37.0.0。
    compileSdk {
        version = release(37)
    }

    defaultConfig {
        applicationId = "com.guoci.vocab3500"

        // minSdk 26：这个 App 提供的一切（自适应图标、通知渠道、java.time）
        // 在 8.0 上都有；再往下要为 java.time 做 desugaring，不值得。
        minSdk = 26
        targetSdk = 37
        // 1.2.0：测验改用内置键盘，彻底绕开系统输入法的纠错与联想（防作弊）。
        versionCode = 3
        versionName = "1.2.0"
    }

    buildFeatures {
        compose = true
    }

    buildTypes {
        release {
            // R8 关掉：这个 App 没有需要瘦身的巨型依赖，而一条被裁掉的反射/资源
            // 规则会让分享 PDF 或 TTS 在 release 包里悄悄坏掉。体积换确定性。
            optimization {
                enable = false
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }

    packaging {
        resources {
            excludes += "/META-INF/{AL2.0,LGPL2.1}"
        }
    }
}

dependencies {
    // 纯逻辑：间隔表、拼写判定、连续天数、导出、默写单排版。
    // 它没有 Android 依赖，这正是它的测试能在 JVM 上跑的原因。
    implementation(project(":core"))

    implementation(platform(libs.androidx.compose.bom))
    implementation(libs.androidx.activity.compose)
    implementation(libs.androidx.compose.ui)
    implementation(libs.androidx.compose.ui.graphics)
    implementation(libs.androidx.compose.ui.tooling.preview)
    implementation(libs.androidx.compose.material3)

    debugImplementation(libs.androidx.compose.ui.tooling)
}
