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
    # Intel Mac 的 Homebrew 路径
    if [ -f /usr/local/bin/brew ]; then
        eval $(/usr/local/bin/brew shellenv)
    elif [ -f /opt/homebrew/bin/brew ]; then
        eval $(/opt/homebrew/bin/brew shellenv)
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
    
    # 配置 JDK 环境变量
    if [ -f /usr/libexec/java_home ]; then
        export JAVA_HOME=$(/usr/libexec/java_home -v 17)
        echo "✅ JAVA_HOME 已设置: $JAVA_HOME"
    fi
    
    echo "✅ JDK 17 安装成功"
    echo ""
fi

# 检查 Java 版本（兼容处理）
JAVA_VERSION_OUTPUT=$(java -version 2>&1)
JAVA_VERSION=$(echo "$JAVA_VERSION_OUTPUT" | head -1 | cut -d'"' -f2 | cut -d'.' -f1)

if [ -z "$JAVA_VERSION" ] || ! [[ "$JAVA_VERSION" =~ ^[0-9]+$ ]]; then
    echo "⚠️  无法检测 Java 版本，假设已安装"
else
    if [ "$JAVA_VERSION" -lt "17" ]; then
        echo "⚠️  Java 版本过低: $JAVA_VERSION"
        echo "📦 正在安装 JDK 17..."
        brew install openjdk@17
        
        if [ $? -ne 0 ]; then
            echo "❌ JDK 安装失败"
            exit 1
        fi
        
        echo "✅ JDK 17 安装成功"
        echo ""
    fi
fi

echo "✅ Java 版本检查通过"
echo ""

# 4. 检查 jpackage 是否可用
if ! command -v jpackage &> /dev/null; then
    echo "❌ 未找到 jpackage 工具"
    echo "jpackage 需要 JDK 14 或更高版本"
    echo "请确保已正确安装 JDK 17"
    exit 1
fi
echo "✅ jpackage 工具已就绪"
echo ""

# 4. 编译项目
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

# 5. 下载 JavaFX SDK
JAVAFX_URL="https://download2.gluonhq.com/openjfx/17.0.2/openjfx-17.0.2_osx-x64_bin-sdk.zip"
JAVAFX_BACKUP_URL="https://github.com/openjfx/javafx-sdk/releases/download/17.0.2/openjfx-17.0.2_osx-x64_bin-sdk.zip"
JAVAFX_ZIP="javafx-sdk.zip"

if [ ! -f "$JAVAFX_ZIP" ]; then
    echo "📥 正在下载 JavaFX SDK..."
    if ! curl -L "$JAVAFX_URL" -o "$JAVAFX_ZIP"; then
        echo "⚠️  官方源下载失败，尝试备用源..."
        if ! curl -L "$JAVAFX_BACKUP_URL" -o "$JAVAFX_ZIP"; then
            echo "❌ JavaFX SDK 下载失败"
            echo "请手动下载并解压到项目根目录"
            exit 1
        fi
    fi
else
    echo "✅ JavaFX SDK 已存在，跳过下载"
fi

if [ ! -d "javafx-sdk-17.0.2" ]; then
    unzip -o "$JAVAFX_ZIP"
else
    echo "✅ JavaFX SDK 已解压，跳过解压"
fi

JAVAFX_PATH="./javafx-sdk-17.0.2/lib"
echo "✅ JavaFX SDK 路径: $JAVAFX_PATH"
echo ""

# 6. 清理旧的打包产物
echo "🧹 正在清理旧的打包产物..."
rm -rf target/dist target/jpackage-input
mkdir -p target/jpackage-input
echo "✅ 清理完成"
echo ""

# 7. 准备打包文件(只复制必要的 JAR)
echo "📂 正在准备打包文件..."
cp target/batchfile_app-0.0.1-SNAPSHOT.jar target/jpackage-input/
echo "✅ 文件已复制到 target/jpackage-input/"
echo ""

# 8. 检查图标文件
ICON_ARGS=""
if [ -f "src/main/resources/icon.icns" ]; then
    ICON_ARGS="--icon src/main/resources/icon.icns"
    echo "✅ 使用图标: icon.icns"
elif [ -f "src/main/resources/icon.png" ]; then
    # jpackage 支持 PNG 但建议转换为 icns
    ICON_ARGS="--icon src/main/resources/icon.png"
    echo "⚠️  建议将 PNG 转换为 icns 格式以获得最佳效果"
else
    echo "⚠️  未找到图标文件，将使用默认图标"
fi
echo ""

# 9. 打包应用
echo "📦 正在打包 DMG..."

# 构建 jpackage 命令
JPACKAGE_CMD="jpackage --input target/jpackage-input \\\n         --name BatchRenameTool \\\n         --main-jar batchfile_app-0.0.1-SNAPSHOT.jar \\\n         --main-class com.rt.batchfile_app.BatchfileAppApplication \\\n         --dest target/dist \\\n         --type dmg \\\n         --app-version 1.0.0 \\\n         --vendor RT \\\n         --module-path \"$JAVAFX_PATH\" \\\n         --add-modules java.base,java.desktop,java.xml,jdk.unsupported,javafx.base,javafx.controls,javafx.fxml,javafx.graphics \\\n         --java-options \"-Dfile.encoding=UTF-8\" \\\n         --java-options \"-Dprism.allowhidpi=false\" \\\n         --verbose"

# 如果有图标文件，添加图标参数
if [ -n "$ICON_ARGS" ]; then
    JPACKAGE_CMD="$JPACKAGE_CMD $ICON_ARGS"
fi

# 执行打包
eval $JPACKAGE_CMD

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
