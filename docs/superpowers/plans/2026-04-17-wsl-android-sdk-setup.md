# WSL Android SDK 配置实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:subagent-driven-development（推荐）或 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 在当前 WSL 环境中安装独立的 Android SDK、JDK 17 和必要的命令行工具，使 TrackRecord 项目可以在 WSL 内独立执行 Android 构建与基础调试命令。

**架构：** 采用 Linux 原生 Android SDK 目录（建议 `~/Android/Sdk`），通过 Android command-line tools 安装 `platform-tools`、`platforms;android-36`、`build-tools;36.0.0` 等组件，并将 `ANDROID_HOME`、`ANDROID_SDK_ROOT` 与相关 PATH 写入用户 shell 配置文件。最后用 `java`、`sdkmanager`、`adb` 和 Gradle 进行验证。

**技术栈：** WSL 2、Ubuntu/Linux shell、OpenJDK 17、Android SDK command-line tools、sdkmanager、Gradle Wrapper

---

## 文件结构

- 修改：`~/.bashrc` — 持久化 Android SDK 与 JDK 相关环境变量
- 修改（如存在且为主要 shell 配置）：`~/.profile` 或 `~/.zshrc` — 补充登录 shell 场景的环境变量加载
- 创建：`$HOME/Android/Sdk/` — Android SDK 根目录
- 创建：`$HOME/Android/Sdk/cmdline-tools/latest/` — command-line tools 解压位置
- 创建：`$HOME/Android/Sdk/licenses/` — Android SDK 许可证缓存
- 使用：`/home/wenha/project/AndroidWork/track-record-new/gradlew` — 验证当前项目的构建环境

### 任务 1：安装 JDK 17

**文件：**
- 修改：系统包管理状态（apt）
- 验证：shell 中的 `java` 与 `javac`

- [ ] **步骤 1：确认系统包管理可用**

```bash
sudo apt-get update
```

预期：成功刷新软件源，无权限或源错误需要先解决。

- [ ] **步骤 2：安装 OpenJDK 17**

```bash
sudo apt-get install -y openjdk-17-jdk unzip
```

预期：安装完成，系统可用 `java`、`javac` 与 `unzip`。

- [ ] **步骤 3：验证 JDK 安装结果**

```bash
java -version && javac -version
```

预期：输出中包含 `17`。

- [ ] **步骤 4：Commit**

本任务只涉及系统环境安装，不产生仓库文件变更，跳过 commit。

### 任务 2：安装 Android command-line tools

**文件：**
- 创建：`$HOME/Android/Sdk/`
- 创建：`$HOME/Android/Sdk/cmdline-tools/latest/`

- [ ] **步骤 1：创建 SDK 目录结构**

```bash
mkdir -p "$HOME/Android/Sdk/cmdline-tools"
```

预期：目录创建成功。

- [ ] **步骤 2：下载 Android command-line tools 压缩包**

```bash
cd "$HOME/Android/Sdk/cmdline-tools" && curl -fLo commandlinetools-linux.zip "https://dl.google.com/android/repository/commandlinetools-linux-13114758_latest.zip"
```

预期：下载完成，生成 `commandlinetools-linux.zip`。

- [ ] **步骤 3：解压并整理目录为 latest**

```bash
cd "$HOME/Android/Sdk/cmdline-tools" && unzip -q commandlinetools-linux.zip -d temp && rm -rf latest && mv temp/cmdline-tools latest && rm -rf temp commandlinetools-linux.zip
```

预期：生成 `$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager`。

- [ ] **步骤 4：验证 sdkmanager 文件存在**

```bash
ls -la "$HOME/Android/Sdk/cmdline-tools/latest/bin/sdkmanager"
```

预期：能看到目标文件。

- [ ] **步骤 5：Commit**

本任务只涉及用户主目录环境文件，不产生仓库文件变更，跳过 commit。

### 任务 3：配置环境变量

**文件：**
- 修改：`~/.bashrc`
- 修改：`~/.profile` 或 `~/.zshrc`（仅当用户主要通过对应 shell 使用 WSL 时）

- [ ] **步骤 1：向 `~/.bashrc` 追加 Android 环境变量**

```bash
export ANDROID_HOME="$HOME/Android/Sdk"
export ANDROID_SDK_ROOT="$HOME/Android/Sdk"
export JAVA_HOME="/usr/lib/jvm/java-17-openjdk-amd64"
export PATH="$JAVA_HOME/bin:$ANDROID_HOME/cmdline-tools/latest/bin:$ANDROID_HOME/platform-tools:$PATH"
```

预期：配置块写入 `~/.bashrc`，且不破坏已有配置。

- [ ] **步骤 2：重新加载 shell 配置**

```bash
source "$HOME/.bashrc"
```

预期：当前 shell 能读取新的环境变量。

- [ ] **步骤 3：验证环境变量**

```bash
echo "$JAVA_HOME" && echo "$ANDROID_HOME" && command -v sdkmanager && command -v adb
```

预期：`JAVA_HOME` 和 `ANDROID_HOME` 指向预期路径；此时 `sdkmanager` 应可用，`adb` 可能尚未安装。

- [ ] **步骤 4：Commit**

本任务只涉及用户环境配置，不产生仓库文件变更，跳过 commit。

### 任务 4：安装 Android SDK 组件

**文件：**
- 创建：`$HOME/Android/Sdk/platform-tools/`
- 创建：`$HOME/Android/Sdk/platforms/android-36/`
- 创建：`$HOME/Android/Sdk/build-tools/36.0.0/`
- 创建：`$HOME/Android/Sdk/licenses/`

- [ ] **步骤 1：接受 SDK 许可证**

```bash
yes | sdkmanager --licenses
```

预期：所有许可证被接受。

- [ ] **步骤 2：安装项目所需基础组件**

```bash
sdkmanager "platform-tools" "platforms;android-36" "build-tools;36.0.0"
```

预期：3 个组件安装成功。

- [ ] **步骤 3：按需补充常见组件**

```bash
sdkmanager "cmdline-tools;latest"
```

预期：如仓库元数据允许，该组件状态正常；若提示已安装，可忽略。

- [ ] **步骤 4：验证 `adb` 与已安装包列表**

```bash
adb version && sdkmanager --list_installed
```

预期：`adb` 输出版本，安装列表中包含 `platform-tools`、`platforms;android-36`、`build-tools;36.0.0`。

- [ ] **步骤 5：Commit**

本任务只涉及本机 SDK 安装，不产生仓库文件变更，跳过 commit。

### 任务 5：验证 TrackRecord 项目构建

**文件：**
- 使用：`/home/wenha/project/AndroidWork/track-record-new/gradlew`
- 参考：`/home/wenha/project/AndroidWork/track-record-new/app/build.gradle`

- [ ] **步骤 1：验证 Gradle 可读取 SDK 与 JDK**

```bash
cd "/home/wenha/project/AndroidWork/track-record-new" && ./gradlew -version
```

预期：Gradle 输出 JVM 17 信息，不再报 `java: command not found`。

- [ ] **步骤 2：运行最小构建验证**

```bash
cd "/home/wenha/project/AndroidWork/track-record-new" && ./gradlew help
```

预期：Gradle 能完成配置阶段，不报 Android SDK 缺失。

- [ ] **步骤 3：运行调试构建验证**

```bash
cd "/home/wenha/project/AndroidWork/track-record-new" && ./gradlew assembleDebug
```

预期：若本地配置完整且网络正常，Debug 构建成功；若缺少 `MAPBOX_ACCESS_TOKEN` 或其他外部条件，需记录具体报错并补充对应配置。

- [ ] **步骤 4：记录额外依赖项**

```text
若构建失败，记录是缺少 local.properties、Mapbox token、Android license、网络访问还是其他仓库权限问题。
```

- [ ] **步骤 5：Commit**

若未修改仓库文件，跳过 commit；若为方便 WSL 使用而更新仓库文档（例如 README），单独创建一个文档 commit。

## 自检

- 规格覆盖度：已覆盖 JDK 安装、SDK 安装、环境变量、许可证、SDK 组件与项目验证。
- 占位符扫描：已将需要执行的命令、路径与预期结果写明，无 TODO 或“后续实现”。
- 类型一致性：统一使用 `~/Android/Sdk` 作为 SDK 根目录，统一使用 Android 36 / JDK 17。
