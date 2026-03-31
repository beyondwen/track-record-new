你提供的 `AutoTrackStorage.kt` 代码写得非常严谨，线程安全的 `cacheLock`、防抖刷新策略（PersistPolicy）以及旧数据迁移逻辑都处理得非常专业。

但是，就像我们在排查 `HistoryStorage` 时遇到的问题一样，在这个文件里，你**遗留了一个导致内存疯狂抖动的“炸弹”**，以及一个**随时可能引发主线程 ANR 卡死的阻塞调用**。

如果你的 App 运行几个小时后手机开始发烫，绝对是因为下面这两个问题：

### 🚨 致命缺陷一：每秒钟都在发生的“万条数据深拷贝” (Memory Churn)

**位置：** `AutoTrackStorage.kt` 中的 `peekSession`、`loadSession` 和 `saveSession`。
**原理解析：**
为了防止外部修改缓存，你在返回和保存数据时做了极为严格的防御性拷贝：
```kotlin
// ❌ 极度危险的代码
fun peekSession(context: Context): AutoTrackSession? {
    ensureSessionCacheAsync(context)
    // 这里调用了 points.toList()！
    return sessionCache?.copy(points = sessionCache?.points?.toList().orEmpty()) 
}

fun saveSession(context: Context, session: AutoTrackSession) {
    // ...
    // 这里又调用了 points.toList()！
    val normalizedSession = session.copy(points = session.points.toList()) 
}
```

**为什么这会导致严重卡顿？**
1. 你的主页 UI (`DashboardController`) 大概率是**每秒钟**调用一次 `peekSession()` 来刷新界面的运动时间和距离。
2. 你的后台服务 (`BackgroundTrackingService`) 在运动时，是**每隔 2.5 秒**调用一次 `saveSession()`。
   如果用户骑行了 3 个小时，`points` 列表中会有 10,000 个坐标点。这意味着：**主线程每秒钟都要把这 10,000 个点重新 new 一遍 `ArrayList`；后台线程每 2 秒也要 new 一遍。**
   海量的对象分配会瞬间塞满年轻代内存，迫使系统疯狂触发 GC (垃圾回收)，导致手机严重发烫和全局掉帧。

**🛠️ 修复方案：彻底信任不可变集合，实现零拷贝 (Zero Copy)**
Kotlin 的 `List` 本身就是只读接口。只要你不主动在外部强转成 `MutableList` 进行修改，直接返回引用是绝对安全的：

```kotlin
// ✅ 修改 1：直接返回缓存对象，实现 O(1) 零成本读取
fun peekSession(context: Context): AutoTrackSession? {
    ensureSessionCacheAsync(context)
    return sessionCache 
}

fun loadSession(context: Context): AutoTrackSession? {
    ensureSessionCache(context)
    return sessionCache
}

// ✅ 修改 2：去掉 saveSession 中的 toList()
fun saveSession(context: Context, session: AutoTrackSession) {
    ensureSessionCache(context)
    val appContext = context.applicationContext
    
    // 移除 .copy(points = session.points.toList())，直接使用传入的 session
    val immediatePersistRequest = synchronized(cacheLock) {
        sessionCache = session
        sessionCacheInitialized = true
        // ... 后续代码将 normalizedSession 替换为 session
```

---

### 🚨 致命缺陷二：无意义的同步死锁 (ANR 隐患)

**位置：** `AutoTrackStorage.kt` 中的 `clearSession` 和 `ensureSessionCache`。
**原理解析：**
看这段代码：
```kotlin
private fun ensureSessionCache(context: Context) {
    // ...
    val loadedSession = ioExecutor.submit<AutoTrackSession?> {
        loadSessionFromDisk(context)
    }.get() // ❌ .get() 是阻塞调用！会卡死当前线程直到数据库读完。
}

fun clearSession(context: Context) {
    ensureSessionCache(context) // ❌ 清理数据前，居然强制去数据库读取了一次？
    // ...
}
```
**问题在哪？**
当用户在界面上点击“结束并清除行程”时，UI 层会调用 `clearSession()`。此时，`clearSession` 第一步竟然是调用 `ensureSessionCache`。
因为包含了 `.get()`，如果缓存没初始化，你的**主线程会被死死卡住**，去等磁盘把那几万个点读取到内存里。等好不容易读完（可能需要几百毫秒），下一步居然是直接把缓存清空并 `deleteSession`。
**这不仅白白浪费了大量时间和内存，还极易引发 ANR（应用无响应）。**

**🛠️ 修复方案：绕开无意义的读取**
对于 `clearSession`，我们根本不需要知道之前磁盘里存了什么，直接清空内存标记，并派发异步删除指令即可：

```kotlin
fun clearSession(context: Context) {
    // ✅ 1. 删掉 ensureSessionCache(context)！完全不需要！
    
    synchronized(cacheLock) {
        sessionCache = null
        sessionCacheInitialized = true // 标记为已初始化，防止后续再次去读取被删掉的空数据
        sessionCacheLoading = false
        pendingPersistDao = null
        pendingPersistPreviousSession = null
        pendingPersistSession = null
        lastPersistedSession = null
    }
    
    mainHandler.removeCallbacks(delayedFlushRunnable)
    
    // ✅ 2. 异步执行数据库清空，主线程瞬间返回，纵享丝滑
    ioExecutor.execute {
        val dao = TrackDatabase.getInstance(context).autoTrackDao()
        dao.deleteSessionPoints()
        dao.deleteSession()
    }
    TrackDataChangeNotifier.notifyDashboardChanged()
}
```

### 总结
把上面这两处修改掉之后，你的 `AutoTrackStorage` 就真正变成了一个**高性能、低功耗、纯异步**的完美缓存中枢了。无论是每秒钟读取状态，还是连续写入万条数据，都不会再让系统产生任何额外的开销！