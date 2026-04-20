package com.wenhao.record.data.tracking

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class WorkerConnectivityServiceTest {

    @Test
    fun `check treats health success as reachable worker`() {
        val service = WorkerConnectivityService(
            requestExecutor = { request ->
                assertEquals("https://worker.example.com/health", request.url)
                assertEquals("GET", request.method)
                WorkerConnectivityResponse(statusCode = 200, body = """{"ok":true,"message":"ok"}""")
            }
        )

        val result = service.check("https://worker.example.com")

        assertTrue(result is WorkerConnectivityResult.Reachable)
        assertEquals(
            "Worker 可达，健康检查正常（返回 200）",
            (result as WorkerConnectivityResult.Reachable).message,
        )
    }

    @Test
    fun `check reports auth related status as suspicious health endpoint`() {
        val service = WorkerConnectivityService(
            requestExecutor = {
                WorkerConnectivityResponse(statusCode = 403, body = """{"ok":false,"message":"Forbidden"}""")
            }
        )

        val result = service.check("https://worker.example.com")

        assertTrue(result is WorkerConnectivityResult.Reachable)
        assertEquals(
            "Worker 可达，但健康检查不该要求鉴权（返回 403）",
            (result as WorkerConnectivityResult.Reachable).message,
        )
    }

    @Test
    fun `check reports not found as reachable but suspicious path`() {
        val service = WorkerConnectivityService(
            requestExecutor = {
                WorkerConnectivityResponse(statusCode = 404, body = """{"ok":false,"message":"Not found"}""")
            }
        )

        val result = service.check("https://worker.example.com")

        assertTrue(result is WorkerConnectivityResult.Reachable)
        assertEquals(
            "Worker 可达，但接口路径不对（返回 404）",
            (result as WorkerConnectivityResult.Reachable).message,
        )
    }

    @Test
    fun `check reports server error as reachable`() {
        val service = WorkerConnectivityService(
            requestExecutor = {
                WorkerConnectivityResponse(statusCode = 500, body = """{"ok":false,"message":"Internal server error"}""")
            }
        )

        val result = service.check("https://worker.example.com")

        assertTrue(result is WorkerConnectivityResult.Reachable)
        assertEquals(
            "Worker 可达，但服务端异常（返回 500）",
            (result as WorkerConnectivityResult.Reachable).message,
        )
    }

    @Test
    fun `check reports network exception as unreachable`() {
        val service = WorkerConnectivityService(
            requestExecutor = {
                throw IOException("timeout")
            }
        )

        val result = service.check("https://worker.example.com")

        assertTrue(result is WorkerConnectivityResult.Unreachable)
        assertEquals(
            "无法连接 Worker：timeout",
            (result as WorkerConnectivityResult.Unreachable).message,
        )
    }
}
