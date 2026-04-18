# Worker

这是 `track-record` 项目的 Cloudflare Worker（训练样本上传入口）。

已实现接口：

- `POST /samples/batch`
  - Bearer Token 鉴权（`UPLOAD_TOKEN`）
  - 请求体校验（`samples`、`eventId`、`timestampMillis`、`phase`、`finalDecision`、`features`）
  - 通过 Cloudflare Hyperdrive + `mysql2` 幂等写入 MySQL（`event_id` 唯一键 + `INSERT IGNORE`）

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

1. 在 Cloudflare 创建 Hyperdrive，并记录其 ID。
2. 更新 `wrangler.jsonc` 的 `hyperdrive` 配置：

```jsonc
{
  "hyperdrive": [
    {
      "binding": "HYPERDRIVE",
      "id": "YOUR_HYPERDRIVE_ID"
    }
  ]
}
```

3. 配置上传 Token（不要写进仓库）：

```bash
cd worker
wrangler secret put UPLOAD_TOKEN
```

4. 在 MySQL 执行 `src/schema.sql` 创建表。

## 常用命令

在 `worker/` 目录下执行：

```bash
npm install --package-lock=false
npm run dev
npm test
npm run deploy
```
