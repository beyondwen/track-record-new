# 当天展示缓存实现计划

> **面向 AI 代理的工作者：** 必需子技能：使用 superpowers:executing-plans 逐任务实现此计划。步骤使用复选框（`- [ ]`）语法来跟踪进度。

**目标：** 将记录页地图从原始采集队列切到“当天展示缓存”，跨自然日自动清理缓存，同时保留原始队列用于清洗和上传。

**架构：** `raw_location_point` 继续作为采集队列；新增轻量 `TodayTrackDisplayCache` 管理当天展示点。采集点写入 raw 队列后同步追加当天缓存；记录页只读取当天缓存；跨天时清空缓存但不删除 raw 队列。

**技术栈：** Kotlin、Android SharedPreferences、JUnit、Room 既有 raw 队列。

---

### 任务 1：当天缓存模型

**文件：**
- 创建：`app/src/main/java/com/wenhao/record/data/tracking/TodayTrackDisplayCache.kt`
- 测试：`app/src/test/java/com/wenhao/record/data/tracking/TodayTrackDisplayCacheTest.kt`

- [ ] **步骤 1：编写失败测试**

测试追加当天点、跨天清理、限制最大点数。

- [ ] **步骤 2：运行测试验证失败**

运行：`sh ./gradlew :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodayTrackDisplayCacheTest`
预期：FAIL，类不存在。

- [ ] **步骤 3：实现最少缓存代码**

用 SharedPreferences 存储 `dayStartMillis` 和 JSON 点列表；`append` 跨天时先清理；`loadToday` 只返回当天；最多保留 2048 点。

- [ ] **步骤 4：运行测试验证通过**

运行同上，预期 PASS。

### 任务 2：采集写入缓存

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/tracking/BackgroundTrackingService.kt`

- [ ] **步骤 1：在 raw 入库成功后追加缓存**

在 `appendRawPoint` 的 IO 任务中，`pointStorage.appendRawPoint(rawPoint)` 后调用 `TodayTrackDisplayCache.append(applicationContext, rawPoint)`。

- [ ] **步骤 2：不改变清洗/上传队列**

保留 `raw_location_point` 写入和 Worker kick 逻辑。

### 任务 3：记录页读取缓存

**文件：**
- 修改：`app/src/main/java/com/wenhao/record/ui/main/MainActivity.kt`

- [ ] **步骤 1：替换活动轨迹读取来源**

`refreshActiveSessionTrack` 从 `continuousPointStorage.loadCurrentSessionPoints` 改为 `TodayTrackDisplayCache.loadToday`。

- [ ] **步骤 2：空闲跨天也清理**

在 `refreshDashboardContent` 开头调用 `TodayTrackDisplayCache.clearIfExpired`。

### 任务 4：验证

**文件：**
- 修改测试 fake 如需要。

- [ ] **步骤 1：运行新增测试**

`sh ./gradlew :app:testDebugUnitTest --tests com.wenhao.record.data.tracking.TodayTrackDisplayCacheTest`

- [ ] **步骤 2：运行编译**

`sh ./gradlew :app:assembleDebug`

- [ ] **步骤 3：安装手机**

`adb install -r app/build/outputs/apk/debug/app-debug.apk`
