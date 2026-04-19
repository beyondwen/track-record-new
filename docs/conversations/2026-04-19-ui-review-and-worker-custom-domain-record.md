# 2026-04-19 对话纪要：UI 审查修复与 Worker 自定义域名接入

## 1. 记录目的

本文用于整理 2026-04-19 这轮围绕「Compose UI 审查修复、真机验证、Worker 自定义域名接入、Cloudflare Access 放行」展开的对话过程，记录本次确认过的界面问题、实际修复、域名接入步骤、验证结果与当前可继续使用的状态。

## 2. 本次会话的核心结论

本轮会话最终收敛为以下几条结论：

1. 首页、历史页、关于页存在真实 UI 缺陷，且已经在代码中修复并通过真机截图复核。
2. 记录页顶部浮层、历史页顶部安全区、关于页系统栏与输入区安全区均已调整到可正常使用的状态。
3. 首页与状态弹窗里的旧语义残留已经清理，统一回到「持续采点 / 分析动态段与静止段」的表达。
4. Worker 自定义域名 `trackrecord.freedomjw.dpdns.org` 已经能直达 Worker，不再被 Cloudflare Access 拦截。
5. App 现在可以直接使用自定义域名作为 `Worker 地址`，并继续沿用 Worker 自己的 Bearer Token 鉴权。

## 3. UI 审查中确认的问题

本轮不是只看 Compose 代码，而是同时结合真机截图做核对。确认的问题包括：

### 3.1 记录页顶部浮层过于贴近状态栏

首页地图上的状态浮层和操作按钮在真机上与状态栏距离不足，视觉上有被压住的风险。

本轮修复方式：

- 提高地图视口顶部留白
- 给顶部叠层额外增加顶部偏移

关键文件：

- [MainComposeScreen.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/MainComposeScreen.kt)

### 3.2 关于页缺少完整系统栏与键盘安全区

关于页属于长表单页，之前缺少：

- `statusBarsPadding()`
- `navigationBarsPadding()`
- `imePadding()`

在真机上会导致顶部、底部和输入时的布局贴边问题。

本轮修复方式：

- 为根容器补全系统栏和键盘安全区
- 将页面重新组织为「地图配置 / Worker 上传 / 应用」三个区块

关键文件：

- [AboutComposeScreen.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/main/AboutComposeScreen.kt)

### 3.3 历史页已计算累计信息但未实际展示

历史页状态中原本已经有：

- `totalDistanceText`
- `totalDurationText`

但 UI 没有把它们展示出来。

本轮修复方式：

- 在 `HistoryHeroSection` 新增累计里程和累计时长卡片

关键文件：

- [HistoryComposeScreen.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/history/HistoryComposeScreen.kt)

### 3.4 历史页累计信息标题错误显示格式化占位符

真机截图显示历史页卡片标题直接渲染成了：

- `累计 %.1f 公里`
- `累计 %1$s`

这不是视觉问题，而是直接暴露给用户的资源绑定错误。

本轮修复方式：

- 将累计卡片的标题文案改为静态标签：
  - `累计里程`
  - `累计时长`

### 3.5 首页残留旧语义

真机截图里仍然出现了「智能记录状态」等旧表述，与当前产品定义不一致。

本轮修复方式：

- 将弹窗标题改为 `采点与分析状态`
- 将首页控制面板里的 `自动待机中` 改为 `低功耗采点中`
- 将 `后台采集中` 改为 `持续采点中`

关键文件：

- [strings_compose_map_overlay.xml](/Users/a555/StudioProjects/track-record-new/app/src/main/res/values/strings_compose_map_overlay.xml)
- [DashboardUiController.kt](/Users/a555/StudioProjects/track-record-new/app/src/main/java/com/wenhao/record/ui/dashboard/DashboardUiController.kt)

## 4. 真机验证结果

本轮会话中，通过 ADB 在真机上多次抓取截图进行核对。

### 4.1 记录页

验证截图表明：

- 顶部状态浮层与系统状态栏的间距已经恢复正常
- 首页层级关系基本合理
- 首页主状态文案已统一为持续采点语义

### 4.2 历史页

验证截图表明：

- 顶部标题区域不再贴住状态栏
- 累计里程与累计时长卡片已经正常显示
- 原先直接显示格式化占位符的问题已经修复

### 4.3 关于页

验证截图表明：

- 顶部安全区正常
- 中部表单区块布局稳定
- 下半屏按钮与底部安全区正常

## 5. Worker 自定义域名接入过程

用户本轮提出希望把 Worker 地址改为自定义域名，并给出：

- `trackrecord.freedomjw.dpdns.org`

### 5.1 初始检查结果

最开始直接访问该域名时，返回的是 `302` 跳转到 Cloudflare Access 登录页，而不是 Worker 自己的 JSON。

这说明：

- 域名已经解析到 Cloudflare
- 但当前子域名仍被 Cloudflare Access 保护
- 手机 App 不能直接使用，因为 App 并不会完成 Access 登录流程

### 5.2 处理结论

本轮明确采取的方案是：

- 对 `trackrecord.freedomjw.dpdns.org` 这一个子域名单独放行
- 不再让它经过 Cloudflare Access 登录
- 继续使用 Worker 内部原有的 Bearer Token 鉴权

### 5.3 配置文件同步

为了避免后续重新部署时丢失域名配置，本轮已将自定义域名同步写回本地 Worker 配置：

- [wrangler.jsonc](/Users/a555/StudioProjects/track-record-new/worker/wrangler.jsonc)

新增配置如下：

```jsonc
"routes": [
  {
    "pattern": "trackrecord.freedomjw.dpdns.org",
    "custom_domain": true
  }
]
```

## 6. 自定义域名的最终验证结果

Cloudflare Access 放行后，再次对该域名做了实际请求验证。

### 6.1 验证请求 1

请求：

```bash
curl -i https://trackrecord.freedomjw.dpdns.org/nonexistent
```

结果：

- 返回 `HTTP 404`
- 返回体为 Worker JSON：

```json
{"ok":false,"message":"Not found"}
```

这说明请求已经直达 Worker，而不再跳转到 Access 登录页。

### 6.2 验证请求 2

请求：

```bash
curl -i -X POST https://trackrecord.freedomjw.dpdns.org/raw-points/batch \
  -H 'Content-Type: application/json' \
  --data '{}'
```

结果：

- 返回 `HTTP 401`
- 返回体为：

```json
{"ok":false,"message":"Missing or malformed Authorization header"}
```

这说明：

- 域名已正确命中 Worker
- 当前剩余鉴权仅为 Worker 自己的 Bearer Token
- 这正是 App 预期的接入方式

## 7. App 侧当前应使用的配置

手机端在「关于 > Worker 上传」中，应填写：

```text
https://trackrecord.freedomjw.dpdns.org
```

注意：

- 只填写基础地址
- 不要自己追加 `/raw-points/batch`
- App 会自行拼接：
  - `/raw-points/batch`
  - `/analysis/batch`
  - `/histories/batch`

同时仍需确保：

- `上传 Token` 与 Worker Secret `UPLOAD_TOKEN` 保持一致

## 8. 当前系统状态

截至本轮会话结束，可以确认：

1. Compose UI 的这轮主要真机问题已经修复。
2. 最新 Debug 包已重新编译并安装到真机。
3. Worker 自定义域名已经可以直接给 App 使用。
4. Worker 仍通过 Bearer Token 鉴权，不需要额外接入 Cloudflare Access Service Token。
5. 当前链路已经满足继续试用和继续迭代的条件。

## 9. 本轮直接执行过的关键验证

本轮已实际执行并确认结果的验证包括：

- `bash ./gradlew :app:compileDebugKotlin`
- `bash ./gradlew :app:assembleDebug`
- `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- `curl -i https://trackrecord.freedomjw.dpdns.org/nonexistent`
- `curl -i -X POST https://trackrecord.freedomjw.dpdns.org/raw-points/batch -H 'Content-Type: application/json' --data '{}'`

## 10. 后续继续排查时的优先顺序

如果后续上传仍有异常，优先排查顺序应为：

1. App 中 `Worker 地址` 是否填写为 `https://trackrecord.freedomjw.dpdns.org`
2. App 中 `上传 Token` 是否与 Worker 的 `UPLOAD_TOKEN` 一致
3. Worker 线上日志中是否存在 D1 写入错误
4. D1 表结构是否与当前 Worker 代码保持一致
