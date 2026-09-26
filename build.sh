#!/usr/bin/env bash
#
# 项目构建入口。本机 JDK 与 Gradle 都不在 PATH 里，统一在这里设置。
#
# 用法：
#   ./build.sh :app:assembleDebug         构建 debug APK
#   ./build.sh :app:assembleRelease       构建 release APK
#   ./build.sh clean                      清理
#   ./build.sh :app:lintDebug             静态检查
#
set -euo pipefail

# 本机 Temurin JDK 17（AGP 8.6 要求 JDK 17+）
export JAVA_HOME=/home/orlando/jdk17
export PATH="$JAVA_HOME/bin:$PATH"

PROJECT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
GRADLE_BIN="$PROJECT_DIR/.tools/gradle-8.9/bin/gradle"

if [[ ! -x "$GRADLE_BIN" ]]; then
  echo "找不到 Gradle：$GRADLE_BIN" >&2
  echo "下载方式：curl -fL -o /tmp/gradle.zip https://mirrors.cloud.tencent.com/gradle/gradle-8.9-bin.zip" >&2
  echo "         unzip -q /tmp/gradle.zip -d $PROJECT_DIR/.tools/" >&2
  exit 1
fi

exec "$GRADLE_BIN" -p "$PROJECT_DIR" "$@"
