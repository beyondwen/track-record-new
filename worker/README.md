# Worker

这是 `track-record` 项目的 Cloudflare Worker（点位、分析结果、历史与训练样本上传入口）。

已实现接口：

- `POST /samples/batch`
  - Bearer Token 鉴权（`UPLOAD_TOKEN`）
  - 请求体校验（`samples`、`eventId`、`timestampMillis`、`phase`、`finalDecision`、`features`）
- `POST /raw-points/batch`
  - 批量接收连续原始点位
- `POST /analysis/batch`
  - 批量接收轨迹分段和停留簇
- `POST /histories/batch`
  - 批量接收历史轨迹摘要
- `GET /app-config`
  - Bearer Token 鉴权（`UPLOAD_TOKEN`）
  - 返回当前允许下发给 App 的 `mapboxPublicToken`
- 所有接口统一通过 Cloudflare D1 幂等写入 SQLite（唯一键 + `INSERT OR IGNORE`）

## 成功响应语义

成功响应格式：

```json
{
  "ok": true,
  "insertedCount": 1,
  "dedupedCount": 2,
  "acceptedEventIds": [7, 8]
}
```

- `acceptedEventIds`：本次请求中出现过的唯一 `eventId` 列表。
- `dedupedCount`：按 `原始样本数 - insertedCount` 计算，包含批内重复和数据库已存在导致的去重。

## 运行前配置

1. 在 Cloudflare 创建 D1 数据库，并记录其 `database_id`。
2. 更新 `wrangler.jsonc` 的 `d1_databases` 配置：

```jsonc
{
  "d1_databases": [
    {
      "binding": "DB",
      "database_name": "track-record",
      "database_id": "YOUR_D1_DATABASE_ID"
    }
  ]
}
```

3. 配置上传 Token（不要写进仓库）：

```bash
cd worker
wrangler secret put UPLOAD_TOKEN
```

4. 配置给 App 下发的 Mapbox 公共 Token：

```bash
cd worker
wrangler secret put MAPBOX_PUBLIC_TOKEN
```

5. 在 D1 执行 `src/schema.sql` 创建表：

```bash
cd worker
wrangler d1 execute track-record --file=src/schema.sql
```

## 常用命令

在 `worker/` 目录下执行：

```bash
npm install --package-lock=false
npm run dev
npm test
npm run deploy
```
