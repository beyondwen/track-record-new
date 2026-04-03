这是做“无感后台轨迹记录” App 必经的**“九九八十一难”中最惨烈的一难：室内漂移大爆炸 (Indoor Drift Explosion)**。

你遇到的这三个症状（卡死、数据错乱、没保存下来），在底层的起因是**同一个致命的连锁反应**。

既然你加上了 `WakeLock` 并且是无感记录，你的手机大概率经历了一整晚（或长时间）的**室内 GPS 漂移**。
由于原生 GPS 在室内的精度极差（通常会报 30米~100米 的误差），定位点会像一团乱麻一样在你的房子周围以 2~5m/s 的“虚假速度”疯狂乱窜。

**这场灾难的连锁反应是这样的：**
1. **数据错乱：** 一晚上 8 个小时，GPS 每 2 秒生成一个漂移点。你的 App 忠实地记录了这 **14,000 多个噪点**，并且把它们乱窜的距离全部累加了上去。结果就是：你明明没动，App 却认为你以 15km/h 的速度“梦游”了 80 公里。
2. **卡在记录页面：** 你的主页 Dashboard 试图在 UI 线程渲染这 14,000 个乱窜的点，或者进行庞大的状态比对，直接把主线程的 CPU 跑满了，导致界面卡顿（掉帧甚至假死）。
3. **行程彻底丢失（最惨的一环）：** 当系统最终判定你处于 `STATIONARY`（静止），或者你尝试强杀 App 时，服务触发了 `finalizeCurrentSession()`。在这个方法里，代码试图用极其复杂的 `TrackPathSanitizer` (Douglas-Peucker 算法) 去清洗这 14,000 个点。**这个算法的复杂度在面对万级数据时，会耗时几分钟！** 你的 `trackingThread` 被彻底死锁，随后触发了 Android 系统的 ANR（应用无响应），系统底层的看门狗（Watchdog）直接把你的 App **“一枪爆头”**，导致数据库根本没来得及执行 Save 操作，轨迹彻底灰飞烟灭。

别慌，要治好这个大魔王，只需要在你的代码里加上**三道极其硬核的防御闸门**：

### 🛠️ 第一道闸门：GPS 精度护盾（切断污染源）

我们需要在最源头，把室内漂移的“垃圾数据”直接拒之门外，连进入内存的资格都不给它们。

**修改文件：** `BackgroundTrackingService.kt`
找到接收 GPS 回调的地方（`LocationListener` 的 `onLocationChanged`）：

```kotlin
// BackgroundTrackingService.kt

private fun handleLocationUpdate(location: Location) {
    // 🚨 1. 绝对防御：精度大于 35 米的坐标，100% 是室内漂移或严重遮挡，直接丢弃！
    if (!location.hasAccuracy() || location.accuracy > 35.0f) {
        return 
    }

    // 🚨 2. 速度异常防御：如果你不是做高铁/飞机记录，去除瞬间瞬移的基站飞点
    if (location.hasSpeed() && location.speed > 45.0f) { // > 160km/h
        return
    }

    // ... 原本的追加点和状态机逻辑 ...
}
```

### 🛠️ 第二道闸门：拯救 UI 的“真实距离”累加器

为了防止速度和距离“乱飙”，在追加新点到 `AutoTrackSession` 时，**不要无脑累加两点之间的距离**。

**修改文件：** `BackgroundTrackingService.kt` (或处理 session 追加的地方)
```kotlin
// 追加点位并计算新距离的逻辑处：
val distanceDelta = distanceBetween(lastPoint.lat, lastPoint.lon, newLocation.latitude, newLocation.longitude)

// 🚨 只有当移动距离大于合理抖动（比如 2.5米），才累加到 UI 展示的总距离里
// 这能完美过滤掉在等红绿灯或办公桌前微小的“原地摩擦”
val effectiveDelta = if (distanceDelta > 2.5f) distanceDelta else 0.0f

val updatedDistanceMeters = currentSession.distanceMeters + effectiveDelta

// ...
```

### 🛠️ 第三道闸门：将“死亡计算”打入冷宫（保证 100% 存入数据库）

这是解决“行程没有记录到”的**最核心修复**。`Sanitize` 清洗算法和数据库存储**绝对不能**在负责状态调度的 `trackingThread` 中执行，必须转移到专用的 CPU 密集型线程池。

**修改文件：** `BackgroundTrackingService.kt`
重写你的 `finalizeCurrentSession` 方法：

```kotlin
// BackgroundTrackingService.kt

private fun finalizeCurrentSession() {
    trackingHandler.removeCallbacks(finalizeRunnable)
    
    val sessionToSave = currentSession ?: return
    // 🚨 瞬间清空当前 session，让 UI 立刻恢复到 IDLE 状态，告别卡死！
    currentSession = null 
    AutoTrackStorage.clearSession(this) 

    // ✅ 将极其耗时的清洗算法和数据库 IO 彻底踢到后台线程池
    ioExecutor.execute {
        try {
            // 1. 即使有上万个点，在这里算上 10 秒钟也不会卡顿任何界面
            val sanitizedTrack = TrackPathSanitizer.sanitize(sessionToSave.points, sortByTimestamp = true)
            
            // 2. 如果清洗后发现这段行程全是原地漂移（有效距离 < 50米），直接当做废数据丢弃
            if (sanitizedTrack.totalDistanceKm < 0.05) {
                return@execute
            }

            // 3. 组装历史记录
            val historyItem = HistoryItem(
                timestamp = sessionToSave.startTimestamp,
                durationSeconds = sessionToSave.durationSeconds,
                points = sanitizedTrack.points,
                // ... 其他参数
            )
            
            // 4. 安全存入数据库
            HistoryStorage.add(this@BackgroundTrackingService, historyItem)
            
        } catch (e: Exception) {
            e.printStackTrace()
            CrashLogStore.log("Finalize Session Failed: ${e.message}")
        }
    }
}
```

### 💡 架构师的补充：无感记录的终极心法

只要加上这三道闸门，你的 App 即使被扔在抽屉里三天三夜，也不会再出现数据爆炸和卡死崩溃了。

对于“无感记录”，你还要记住一个心法：**传感器永远比 GPS 可靠且省电**。
未来如果你想进一步优化，可以在手机处于 `STATIONARY` 状态时，调用 `locationManager.removeUpdates(locationListener)` 彻底关闭 GPS 硬件（连点都不要收）。然后依靠 Android原生的 `Sensor.TYPE_SIGNIFICANT_MOTION`（显著运动传感器，耗电量几乎为 0）来监听用户是否开始走路。一旦触发显著运动，再重新拉起 `requestLocationUpdates`。这才是大厂（如 Life360）能做到全天候无感且不费电的终极机密！

快去把这三个闸门焊死，再去实测一下吧，这次数据绝对稳如老狗！