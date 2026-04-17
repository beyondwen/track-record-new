# 2026-04-17 目录清理、README 更新与 WSL Android SDK 配置记录

## 背景

本次对话主要完成了 3 件事：

1. 盘点项目根目录中哪些文件可以删除。
2. 更新已经明显过时的 `README.md`。
3. 在 WSL 环境中配置可用的 Java 17 与 Android SDK，并验证项目可正常构建。

## 一、目录清理

### 1. 用户需求

用户先询问当前目录中哪些文件可以删除，随后要求按「中等」口径给出建议，并最终让我直接执行删除。

### 2. 清理判断结果

按「中等」口径，清理分为两类：

#### 可直接删除的缓存与构建产物

- `.gradle/`
- `.kotlin/`
- `build/`
- `app/build/`
- `.qwen/`
- `.wsl-tools/`
- `.codex`
- `D:\data\GradleCache/`

#### 高概率可删的临时文件与研究产物

- `HttpProbe.java`
- `codex.txt`
- `autoresearch-lessons.md`
- `autoresearch-state.json`
- `autoresearch-state.prev.json`
- `research-results.tsv`
- `research-results.prev.tsv`

### 3. 实际删除结果

已实际删除以上文件和目录。

### 4. 保留项

以下文件被保留：

- `design-history-mobile.png`
- `design-shot-mobile.png`
- `design-shot.png`

保留原因：这几项更像设计参考图，在「中等」口径下不建议直接删除。

## 二、README 更新

### 1. 问题判断

在清理完成后，用户要求检查 `README.md` 是否应该更新。

经检查，原 README 存在明显过时问题：

- 仍写成 `HelloWorld Android 项目`
- 项目路径仍是旧的 Windows 路径
- 目录结构还是模板工程结构
- SDK、Kotlin、AGP 版本与当前项目不一致
- 技术栈描述未反映 Compose、Room、Mapbox 等现状

### 2. 更新内容

我将 `README.md` 重写为符合当前 `TrackRecord` 项目状态的版本，主要包括：

- 将项目名称改为 `TrackRecord`
- 更新项目定位：Android 轨迹记录应用
- 更新主要技术栈：Kotlin、Jetpack Compose、Room、Mapbox
- 更新构建与平台信息：JDK 17、API 24 / 36
- 按当前代码结构重写目录说明
- 补充本地配置说明：`MAPBOX_ACCESS_TOKEN` 与签名配置
- 增加常用构建命令与相关文档入口

## 三、WSL Android SDK 配置

### 1. 初始检查结论

用户随后询问 WSL 中是否已有 Android SDK。

检查结果显示，初始状态下 WSL 中没有可直接使用的 Android SDK：

- 未设置 `ANDROID_HOME`
- 未设置 `ANDROID_SDK_ROOT`
- `sdkmanager` 与 `adb` 不可用
- 常见 SDK 目录为空

### 2. 方案选择

用户随后要求「把 WSL 里的安卓 SDK 配好」。

在方案讨论中，给出两种方向：

- 在 WSL 中安装独立 Android SDK
- 让 WSL 复用 Windows 已有 Android SDK

最终选定方案为：

**在 WSL 中使用独立目录安装和配置 Android SDK，但优先复用宿主机 D 盘中已有的 Linux 版 JDK 包与 Android SDK 组件，以减少额外下载。**

### 3. JDK 配置过程

#### 发现可复用 JDK 包

在宿主机目录 `/mnt/d/data/wsl-java/` 下发现：

- `OpenJDK17U-jdk_x64_linux_hotspot_17.0.17_10.tar.gz`

确认这是 Linux 版 JDK 压缩包，可以直接在 WSL 中使用。

#### 实际处理

- 将压缩包复制到 `~/local/java/`
- 解压得到目录：`/home/wenha/local/java/jdk-17.0.17+10`
- 验证 `java` 与 `javac` 可执行
- 将以下配置写入 `~/.bashrc`：

```bash
export JAVA_HOME="$HOME/local/java/jdk-17.0.17+10"
export PATH="$JAVA_HOME/bin:$PATH"
```

### 4. Android SDK 配置过程

#### 发现可复用 Android SDK 内容

在宿主机目录 `/mnt/d/data/AndroidSDK/` 下发现已有：

- `cmdline-tools`
- `platform-tools`
- `platforms`
- `build-tools`
- `licenses`

#### 实际处理

- 将 `cmdline-tools` 复制到 `~/Android/Sdk/`
- 将 `platform-tools`、`platforms`、`build-tools`、`licenses` 复制到 `~/Android/Sdk/`
- 将以下配置写入 `~/.bashrc`：

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export PATH="$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

### 5. 验证结果

对 WSL 中的 Java 与 Android SDK 做了多轮验证。

#### Java 验证

- `java -version`：通过
- `javac -version`：通过

版本信息：

- OpenJDK `17.0.17`

#### Android SDK 验证

- `sdkmanager --version`：通过
- `adb version`：通过

已确认存在的 SDK 组件：

- `~/Android/Sdk/cmdline-tools/latest`
- `~/Android/Sdk/platform-tools`
- `~/Android/Sdk/platforms/android-36`
- `~/Android/Sdk/build-tools/35.0.0`
- `~/Android/Sdk/build-tools/36.0.0`
- `~/Android/Sdk/licenses`

#### Gradle 与项目构建验证

对项目执行了以下验证：

```bash
./gradlew -version
./gradlew help
./gradlew assembleDebug
```

验证结果：

- Gradle 可识别 Java 17
- Gradle 可识别 Android SDK
- `./gradlew help` 成功
- `./gradlew assembleDebug` 成功，退出码为 `0`

这说明 `TrackRecord` 项目已经可以在当前 WSL 环境下完成 Debug 构建。

## 四、当前状态总结

截至本次对话结束，当前状态如下：

### 已完成

- 已完成项目目录清理
- 已更新 `README.md`
- 已在 WSL 中配置好 Java 17
- 已在 WSL 中配置好 Android SDK
- 已验证项目可在 WSL 中执行 `assembleDebug`

### 关键路径

- JDK：`/home/wenha/local/java/jdk-17.0.17+10`
- Android SDK：`/home/wenha/Android/Sdk`

### Shell 配置

`~/.bashrc` 已包含：

- `JAVA_HOME`
- `ANDROID_HOME`
- `ANDROID_SDK_ROOT`
- 对应 PATH 配置

## 五、后续可选动作

如需继续完善 WSL Android 开发体验，后续可继续做：

1. 验证 `adb devices` 是否能在 WSL 中看到设备。
2. 测试是否能直接从 WSL 安装 APK 到手机。
3. 将 WSL 环境配置说明补充到项目文档中，方便后续复用。
