// ---------------------------------------------------------------------------
// 为什么镜像排在前面
//
// 本机网络上 Maven Central 会被拦截：直连 repo.maven.apache.org 时证书 SAN 对不上
// （返回的是 117.55.193.154 这类地址），每一个本地缓存里没有的依赖都会失败，而且失败得
// 很慢。阿里云镜像在这里是忠实副本，先把它们放在前面，让构建不依赖被篡改的链路。
// 兜底的 google()/mavenCentral() 仍然保留在镜像之后。
// ---------------------------------------------------------------------------

pluginManagement {
    repositories {
        maven("https://maven.aliyun.com/repository/gradle-plugin")
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")

        google {
            content {
                includeGroupByRegex("com\\.android.*")
                includeGroupByRegex("com\\.google.*")
                includeGroupByRegex("androidx.*")
            }
        }
        mavenCentral()
        gradlePluginPortal()
    }

    // Kotlin JVM 插件的默认版本。
    //
    // :core 是纯 Kotlin/JVM 模块，它的 plugins 块里不写版本。不能像 :app 那样靠
    // AGP 把 Kotlin 插件带上构建 classpath —— Gradle 解析 :core 的 plugins 块时
    // :app 还没解析，会报 "Plugin [id: 'org.jetbrains.kotlin.jvm'] was not found
    // ... must include a version number"。在这里声明一次默认版本即解决。
    //
    // 必须写在 pluginManagement 里面，不能写到下面的顶层 plugins 块里：那样是
    // 把插件应用到 Settings 对象，会报 "Unexpected plugin type"。
    plugins {
        id("org.jetbrains.kotlin.jvm") version "2.4.0"
    }
}

// 解析 gradle/gradle-daemon-jvm.properties 里声明的 JDK 25 toolchain。
// 没有它，Gradle 在没装对应 JDK 的机器上会直接拒绝启动。
plugins {
    id("org.gradle.toolchains.foojay-resolver-convention") version "1.0.0"
}

dependencyResolutionManagement {
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        maven("https://maven.aliyun.com/repository/google")
        maven("https://maven.aliyun.com/repository/public")

        google()
        mavenCentral()
    }
}

rootProject.name = "guoci"

include(":app")

// 纯 Kotlin/JVM 的记忆逻辑：间隔表、拼写判定、连续天数、导出、默写单排版。
// 它不依赖任何 Android 类，所以测试在每次构建时都能在 JVM 上跑完，
// 而不用等一台插着的数据线连着的手机。
include(":core")
