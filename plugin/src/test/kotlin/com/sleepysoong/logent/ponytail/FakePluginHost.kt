package com.sleepysoong.logent.ponytail

import com.sleepysoong.logent.plugin.PluginHost
import com.sleepysoong.logent.plugin.ProcessResult

/** 플러그인 테스트가 자산·설정 계약만 검증하도록 Android 의존성을 대신해요. */
internal class FakePluginHost(
    private val assetSource: (String) -> ByteArray = { error("자산을 읽으면 안 돼요: $it") },
    settings: Map<String, String> = emptyMap(),
    private val writeResult: (String, String) -> Result<Unit> = { _, _ -> Result.success(Unit) },
) : PluginHost {
    override val appVersion: String = "test"
    override val apkPath: String = "/dev/null"

    private val currentSettings = settings.toMutableMap()
    val writes = mutableListOf<Pair<String, String>>()

    override suspend fun shell(command: String, timeoutSeconds: Int): Result<ProcessResult> =
        Result.failure(UnsupportedOperationException("셸을 쓰지 않아요: $command"))

    override suspend fun ensureDesktop(): Result<Unit> = Result.success(Unit)

    override suspend fun readAsset(path: String): Result<ByteArray> = runCatching { assetSource(path) }

    override fun setting(key: String): String? = currentSettings[key]

    override fun writeSetting(key: String, value: String): Result<Unit> {
        writes += key to value
        return writeResult(key, value).onSuccess { currentSettings[key] = value }
    }

    override fun progress(message: String) = Unit
}
