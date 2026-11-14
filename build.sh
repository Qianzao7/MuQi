#!/usr/bin/env bash
# 幕启 MuQi — 一键编译 debug APK（Linux / macOS）
set -euo pipefail
cd "$(dirname "$0")"

if [ ! -f local.properties ]; then
    echo "[!] 缺少 local.properties"
    echo "    cp local.properties.example local.properties"
    echo "    然后把 sdk.dir 改成你本机的 Android SDK 路径"
    exit 1
fi

if [ -n "${JAVA_HOME:-}" ]; then
    echo "[i] JAVA_HOME=$JAVA_HOME"
else
    echo "[!] 未设置 JAVA_HOME，将使用 PATH 中的 java（需要 JDK 17+）"
fi

echo "[i] 开始编译 debug APK（首次运行会自动下载 Gradle 9.8.2，需联网）..."
./gradlew assembleDebug --console=plain

APK="app/build/outputs/apk/debug/app-debug.apk"
if [ -f "$APK" ]; then
    echo "[✓] 构建成功：$APK"
    ls -lh "$APK"
else
    echo "[x] 未找到产物 $APK"
    exit 1
fi
