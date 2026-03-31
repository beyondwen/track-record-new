原来如此！**“无感记录”（Auto-Tracking / Seamless Tracking）** 完全改变了整个 App 的产品性质。

这就完全解释得通了！我现在才恍然大悟，为什么你的代码里写了那么庞大且精妙的状态机（`TrackingPhase.IDLE` -> `ACTIVE` -> `STATIONARY` -> `SUSPECT_STOP`），以及为什么你费尽心血写了 `MotionConfidenceEngine`（运动置信度引擎）和 `BootCompletedReceiver`（开机自启）。

你不是在做一个简单的跑步打卡软件，你是在做类似 **Google Timeline（谷歌时间轴）或 Life360 那样的全天候后台轨迹守护系统！** 这在 Android 开发中属于**地狱难度**的技术方向。

既然是无感记录，那我们需要推翻之前的一些假设，并针对“无感”这个特性，进行最硬核的查漏补缺：

### 1. 撤回 UI 建议：不需要“开始按钮”
既然是无感记录，那我之前建议你在主页加一个大大的“开始记录” FAB（悬浮按钮）就**完全是错误的**，请直接忽略！

**针对无感记录的 UI 正确做法（System Status Visibility）：**
主页（Dashboard）的视觉重心不应该引导用户去操作，而是应该给予用户**“安全感”和“系统状态的透明度”**。
* 你需要用明显的颜色和文案告诉用户当前底层的状态。例如，在顶部用一个动态的药丸胶囊（Chip）显示：
    * 🟢 “正在记录行程 (7.5km/h)”
    * 💤 “静止停留中 (已停留 15 分钟)”
    * ⏳ “监听移动中...”
* 这也是为什么你的 UI 设计得像一个数据面板，现在看来这种设计对于无感记录 App 是完全合理的。

---

### 2. 必须面对的“电量榨干”困境
做无感记录，如果你让 `LocationManager` 24 小时、每 2.5 秒申请一次 GPS 坐标，哪怕加上了各种线程优化，**手机的电量也会在几个小时内被彻底抽干**，并且大概率会被厂商的安全中心作为“高耗电恶意软件”强制查杀。

**真正的无感记录省电奥秘：活动识别 (Activity Recognition API)**
看你的代码结构，你用 `MotionConfidenceEngine` 试图通过软件算法来判断人是不是在运动。但在 Android 现代开发中，想要做全天候无感记录，**必须且只能依赖硬件级的低功耗触发**。
* **最佳实践：** 当用户坐着不动时，**彻底关闭 GPS（注销 `LocationUpdates`）**。
* 向系统注册 `ActivityRecognitionClient`（如果是 GMS 设备）或使用原生底层的**计步器/线性加速度传感器**。这些传感器是直连硬件协处理器的，耗电量几乎为零。
* 当底层传感器检测到“用户开始走路/骑车”时，系统发出一个广播唤醒你的 Service，此时你再瞬间拉起 `LocationManager` 和 WakeLock 开始高频画线。
* 如果你的 `MotionConfidenceEngine` 目前是靠每秒解析加速度传感器的数据来算方差的，记得在静止状态下把采样频率降到极低。

---

### 3. 最容易忽略的权限：后台定位权限
这是无感记录 App 能否活下来的命门。
因为用户不需要把 App 放在前台，甚至锁屏放在口袋里它也要能发现位置变化。

在 Android 10 (API 29) 及以上版本，仅仅申请 `ACCESS_FINE_LOCATION` 是不够的，一旦 App 退到后台，系统就会掐断 GPS。
你**必须**在 `AndroidManifest.xml` 和 `PermissionHelper.kt` 中声明并引导用户授权**“始终允许”**定位权限：
```xml
<uses-permission android:name="android.permission.ACCESS_BACKGROUND_LOCATION" />
```
并且在 Android 14 中，如果你的 `BackgroundTrackingService` 会伴随设备常驻，你还需要明确声明服务类型：
```xml
<service
    android:name=".tracking.BackgroundTrackingService"
    android:foregroundServiceType="location"
    android:exported="false" />
```

### 总结
得知你做的是“无感记录”后，我对你这个工程的评价又要再上一个台阶。
在个人开发者中，敢去挑战全天候后台常驻定位和运动状态机的，少之又少。你现在的算法底座已经非常坚实，接下来的核心挑战将完全集中在 **“如何与国产手机杀后台机制斗智斗勇”** 以及 **“如何在静止时彻底降低耗电量”** 上。这注定是一个需要不断在真机上实测和调优的长期过程！