#!/bin/bash

echo "========================================="
echo "批量重命名工具 - macOS 打包脚本"
echo "========================================="
echo ""

# 1. 检查并安装 Homebrew
if ! command -v brew &> /dev/null; then
    echo "⚠️  未找到 Homebrew"
    echo " 正在安装 Homebrew..."
    echo ""
    
    # 尝试从多个源安装
    if /bin/bash -c "$(curl -fsSL https://raw.githubusercontent.com/Homebrew/install/HEAD/install.sh)"; then
        echo "✅ Homebrew 安装成功"
    else
        echo "⚠️  官方源安装失败，尝试国内镜像源..."
        if /bin/bash -c "$(curl -fsSL https://gitee.com/ineo6/homebrew-install/raw/master/install.sh)"; then
            echo "✅ Homebrew 安装成功（国内镜像）"
        else
            echo "❌ Homebrew 安装失败"
            echo "请手动安装: https://brew.sh"
            exit 1
        fi
    fi
    
    echo ""
    # 将 brew 添加到 PATH（Apple Silicon Mac 需要）
    if [ -f /opt/homebrew/bin/brew ]; then
        eval $(/opt/homebrew/bin/brew shellenv)
    elif [ -f /usr/local/bin/brew ]; then
        eval $(/usr/local/bin/brew shellenv)
    fi
    
    echo "✅ Homebrew 已就绪"
    echo ""
fi

# 2. 检查 Maven 是否安装
if ! command -v mvn &> /dev/null && [ ! -f "./mvnw" ]; then
    echo " 错误: 未找到 Maven 或 Maven Wrapper"
    echo "请确保在项目根目录运行此脚本"
    exit 1
fi

# 3. 检查并安装 JDK
if ! command -v java &> /dev/null; then
    echo "⚠️  未找到 Java"
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
    echo "️  Java 版本过低: $JAVA_VERSION"
    echo "📦 正在安装 JDK 17..."
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
