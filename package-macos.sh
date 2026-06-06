#!/bin/bash

echo "========================================="
echo "批量重命名工具 - macOS 打包脚本"
echo "========================================="
echo ""

# 1. 检查 Maven 是否安装
if ! command -v mvn &> /dev/null && [ ! -f "./mvnw" ]; then
    echo "❌ 错误: 未找到 Maven 或 Maven Wrapper"
    echo "请确保在项目根目录运行此脚本"
    exit 1
fi

# 2. 检查并安装 JDK
if ! command -v java &> /dev/null; then
    echo "⚠️  未找到 Java"
    echo " 正在检查 Homebrew..."
    
    if ! command -v brew &> /dev/null; then
        echo "❌ 错误: 未找到 Homebrew"
        echo "请先安装 Homebrew: /bin/bash -c \"\$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)\""
        echo "或手动安装 JDK 17"
        exit 1
    fi
    
    echo " 正在安装 JDK 17..."
    brew install openjdk@17
    
    if [ $? -ne 0 ]; then
        echo "❌ JDK 安装失败"
        echo "请手动安装: brew install openjdk@17"
        exit 1
    fi
    
    echo "✅ JDK 17 安装成功"
    echo ""
fi

JAVA_VERSION=$(java -version 2>&1 | head -1 | cut -d'"' -f2 | cut -d'.' -f1)
if [ "$JAVA_VERSION" -lt "17" ]; then
    echo "⚠️  Java 版本过低: $JAVA_VERSION"
    echo "📦 正在安装 JDK 17..."
    
    if ! command -v brew &> /dev/null; then
        echo " 错误: 需要 Homebrew 来安装 JDK"
        echo "请先安装 Homebrew 或手动安装 JDK 17+"
        exit 1
    fi
    
    brew install openjdk@17
    
    if [ $? -ne 0 ]; then
        echo "❌ JDK 安装失败"
        exit 1
    fi
    
    echo "✅ JDK 17 安装成功"
    echo ""
fi

echo "✅ Java 版本: $(java -version 2>&1 | head -1)"
echo ""

# 3. 编译项目
echo "📦 正在编译项目..."
if [ -f "./mvnw" ]; then
    chmod +x ./mvnw
    ./mvnw clean package -DskipTests
else
    mvn clean package -DskipTests
fi

if [ $? -ne 0 ]; then
    echo "❌ 编译失败"
    exit 1
fi

echo "✅ 编译成功"
echo ""

# 4. 下载 JavaFX SDK
echo "📥 正在下载 JavaFX SDK..."
JAVAFX_URL="https://download2.gluonhq.com/openjfx/17.0.2/openjfx-17.0.2_osx-x64_bin-sdk.zip"
JAVAFX_ZIP="javafx-sdk.zip"

if [ ! -f "$JAVAFX_ZIP" ]; then
    curl -L "$JAVAFX_URL" -o "$JAVAFX_ZIP"
else
    echo "JavaFX SDK 已存在，跳过下载"
fi

if [ ! -d "javafx-sdk-17.0.2" ]; then
    unzip -o "$JAVAFX_ZIP"
else
    echo "JavaFX SDK 已解压，跳过解压"
fi

JAVAFX_PATH="./javafx-sdk-17.0.2/lib"
echo "✅ JavaFX SDK 路径: $JAVAFX_PATH"
echo ""

# 5. 复制 JavaFX native 库（解决 No toolkit found 错误）
echo "🔧 正在准备 native 库..."
mkdir -p target/lib
cp javafx-sdk-17.0.2/lib/*.dylib target/lib/
echo "✅ Native 库已复制到 target/lib/"
echo ""

# 6. 打包应用
echo "📦 正在打包 DMG..."
jpackage --input target \
         --name "BatchRenameTool" \
         --main-jar batchfile_app-0.0.1-SNAPSHOT.jar \
         --main-class com.rt.batchfile_app.BatchfileAppApplication \
         --dest target/dist \
         --type dmg \
         --app-version 1.0.0 \
         --vendor RT \
         --module-path "$JAVAFX_PATH" \
         --add-modules java.base,java.desktop,java.xml,jdk.unsupported,javafx.base,javafx.controls,javafx.fxml,javafx.graphics \
         --java-options "-Dfile.encoding=UTF-8" \
         --java-options "-Dprism.allowhidpi=false" \
         --verbose

if [ $? -ne 0 ]; then
    echo "❌ 打包失败"
    exit 1
fi

echo ""
echo "========================================="
echo "✅ 打包成功！"
echo "========================================="
echo ""
echo "📁 DMG 文件位置: target/dist/BatchRenameTool-1.0.0.dmg"
echo ""
echo " 安装步骤："
echo "1. 双击 DMG 文件"
echo "2. 将 BatchRenameTool 拖拽到 Applications 文件夹"
echo "3. 首次运行：右键点击应用 → 选择'打开' → 再次点击'打开'"
echo ""
