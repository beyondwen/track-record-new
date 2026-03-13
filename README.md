# HelloWorld Android 项目

这是一个使用 Kotlin 编写的简单 Android Hello World 应用。

## 项目位置
`D:\data\AndroidWork\TrackRecord`

## 项目结构

```
TrackRecord/
├── app/
│   ├── build.gradle
│   ├── proguard-rules.pro
│   └── src/main/
│       ├── AndroidManifest.xml
│       ├── java/com/example/helloworld/
│       │   └── MainActivity.kt
│       └── res/
│           ├── layout/activity_main.xml
│           ├── values/colors.xml
│           ├── values/strings.xml
│           └── values/themes.xml
├── build.gradle
├── gradle.properties
├── gradle/
│   └── wrapper/
│       └── gradle-wrapper.properties
├── gradlew
├── gradlew.bat
├── local.properties
└── settings.gradle
```

## 使用方法

### 方法一：使用 Android Studio 打开
1. 打开 Android Studio
2. 选择 "Open an Existing Project"
3. 选择 `D:\data\AndroidWork\TrackRecord` 文件夹
4. 等待 Gradle 同步完成
5. 运行项目

### 方法二：使用命令行构建
1. 确保已安装 Java JDK 8 或更高版本
2. 下载 gradle-wrapper.jar 文件到 `gradle/wrapper/` 目录
3. 运行构建命令：
   - Windows: `gradlew.bat assembleDebug`
   - Mac/Linux: `./gradlew assembleDebug`

## 配置说明

- **compileSdk**: 34
- **minSdk**: 24
- **targetSdk**: 34
- **Kotlin 版本**: 1.9.0
- **Android Gradle Plugin**: 8.1.0
- **Gradle**: 8.0

## 技术栈

- Kotlin
- AndroidX
- Material Components
- ConstraintLayout
