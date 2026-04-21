package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WorkerAppConfigServiceTest {

    @Test
    fun `load requests app-config endpoint and parses mapbox token`() {
        val service = WorkerAppConfigService(
            requestExecutor = { request ->
                assertEquals("GET", request.method)
                assertEquals("https://worker.example.com/app-config", request.url)
                assertEquals("Bearer token-123", request.headers["Authorization"])
                assertEquals("application/json", request.headers["Accept"])
                UploadHttpResponse(
                    statusCode = 200,
                    body = """{"ok":true,"mapboxPublicToken":"pk.worker-token"}""",
                )
            },
        )

        val result = service.load(validConfig())

        assertTrue(result is WorkerAppConfigResult.Success)
        assertEquals("pk.worker-token", (result as WorkerAppConfigResult.Success).mapboxPublicToken)
    }

    @Test
    fun `load returns auth failure when worker rejects token`() {
        val service = WorkerAppConfigService(
            requestExecutor = {
                UploadHttpResponse(
                    statusCode = 403,
                    body = """{"ok":false,"message":"Forbidden"}""",
                )
            },
        )

        val result = service.load(validConfig())

        assertTrue(result is WorkerAppConfigResult.Failure)
        assertEquals("鉴权失败，请检查上传令牌", (result as WorkerAppConfigResult.Failure).message)
    }

    @Test
    fun `load returns worker message when token payload is invalid`() {
        val service = WorkerAppConfigService(
            requestExecutor = {
                UploadHttpResponse(
                    statusCode = 200,
                    body = """{"ok":true,"mapboxPublicToken":"   "}""",
                )
            },
        )

        val result = service.load(validConfig())

        assertTrue(result is WorkerAppConfigResult.Failure)
        assertEquals("Worker 未返回有效的 Mapbox Token", (result as WorkerAppConfigResult.Failure).message)
    }

    @Test
    fun `load returns network failure on io exception`() {
        val service = WorkerAppConfigService(
            requestExecutor = {
                throw IOException("timeout")
            },
        )

        val result = service.load(validConfig())

        assertTrue(result is WorkerAppConfigResult.Failure)
        assertEquals("读取 Mapbox 配置失败，请检查网络后重试", (result as WorkerAppConfigResult.Failure).message)
    }

    private fun validConfig(): TrainingSampleUploadConfig {
        return TrainingSampleUploadConfig(
            workerBaseUrl = "https://worker.example.com",
            uploadToken = "token-123",
        )
    }
}
