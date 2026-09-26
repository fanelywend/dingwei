// 依赖仓库按环境切换：
//   本地（默认）  走阿里云镜像：本机实测 Gradle 官方源不可达（0 KB/s）、
//                 Maven Central 仅 ~74 KB/s、Google Maven ~2.4 MB/s，阿里云 ~5.6 MB/s。
//   CI / 海外     传 -PuseMirrors=false 或设环境变量 GRADLE_USE_MIRRORS=false 改用官方源：
//                 GitHub Actions runner 在海外，官方源更快更稳。
// （pluginManagement 必须是脚本第一个语句块，故此处计算逻辑在两个块内各写一次。）
pluginManagement {
    val useMirrors = gradle.startParameter.projectProperties["useMirrors"]?.toBoolean()
        ?: System.getenv("GRADLE_USE_MIRRORS")?.toBoolean()
        ?: true
    repositories {
        if (useMirrors) {
            maven { url = uri("https://maven.aliyun.com/repository/gradle-plugin") }
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
        gradlePluginPortal()
    }
}

dependencyResolutionManagement {
    val useMirrors = gradle.startParameter.projectProperties["useMirrors"]?.toBoolean()
        ?: System.getenv("GRADLE_USE_MIRRORS")?.toBoolean()
        ?: true
    repositoriesMode.set(RepositoriesMode.FAIL_ON_PROJECT_REPOS)
    repositories {
        if (useMirrors) {
            maven { url = uri("https://maven.aliyun.com/repository/google") }
            maven { url = uri("https://maven.aliyun.com/repository/public") }
        }
        google()
        mavenCentral()
    }
}

rootProject.name = "dingwei"
include(":app")
