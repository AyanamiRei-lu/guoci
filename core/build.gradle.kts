import org.jetbrains.kotlin.gradle.dsl.JvmTarget

// :core —— 记忆逻辑，纯 Kotlin/JVM。
//
// 为什么单独一个模块：间隔表、拼写判定、连续天数、CSV 导出、默写单排版这些规则
// 是这个 App 里唯一"错了没人会发现"的部分。放在 :app 里它们要靠一台连着的手机
// 才能验；放在这里，`.\gradlew.bat :core:test` 在每次构建时都能跑完，
// 而且不加载任何 Android 类。
//
// 插件故意不带版本：AGP 9 已经把 Kotlin Gradle 插件放进构建 classpath，
// 指定版本会失败（"already on the classpath with an unknown version"），
// 不指定则用 :app 也在用的同一个 Kotlin 编译器，两个模块因此不会漂移到不同版本。
plugins {
    id("org.jetbrains.kotlin.jvm")
}

java {
    sourceCompatibility = JavaVersion.VERSION_17
    targetCompatibility = JavaVersion.VERSION_17
}

kotlin {
    compilerOptions {
        // 必须与 :app 一致，否则两个模块无法链接。
        jvmTarget.set(JvmTarget.JVM_17)
    }
}

dependencies {
    testImplementation(libs.junit)
}

tasks.withType<Test>().configureEach {
    testLogging {
        // 不打开的话，一个自己跳过的测试和一个通过的测试长得一模一样。
        events("skipped", "failed", "passed")
        showStandardStreams = false
    }
}
