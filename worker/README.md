# Worker

这是 `track-record` 项目的 Cloudflare Worker 骨架目录。当前只包含工程初始化与运行配置，业务代码后续再补在 `worker/src/` 下。

注意：

- 当前还没有创建 `src/index.ts`，所以 `npm run dev` 和 `npm run deploy` 需要等下一任务补上入口后才会真正可用。
- 这里先列出计划中的 MySQL 环境变量，只表示后续实现会用到这些配置，不代表当前已经确认最终直连方案。

## 环境变量

Worker 运行时需要以下环境变量：

- `UPLOAD_TOKEN`
- `MYSQL_HOST`
- `MYSQL_PORT`
- `MYSQL_DATABASE`
- `MYSQL_USER`
- `MYSQL_PASSWORD`

## 常用命令

在 `worker/` 目录下执行：

```bash
npm install
npm run dev
npm test
npm run deploy
```

## 说明

- `npm run dev`：启动本地 Worker 开发服务
- `npm test`：运行 Vitest 测试
- `npm run deploy`：部署到 Cloudflare Workers
