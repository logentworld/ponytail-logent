package com.sleepysoong.logent.ponytail

import com.sleepysoong.logent.plugin.PluginHookContext
import com.sleepysoong.logent.plugin.PluginHost
import com.sleepysoong.logent.plugin.PluginSettingKind
import com.sleepysoong.logent.plugin.PluginSlashContext
import com.sleepysoong.logent.plugin.PluginSlashResult
import com.sleepysoong.logent.plugin.ToolResult
import java.nio.file.Files
import java.nio.file.Path
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * ponytail 플러그인의 스킬 자산 목록, `/ponytail` 인자 규칙, 설정 기본값과
 * 시스템 프롬프트 주입을 검증해요.
 */
class PonytailPluginTest {
    private val assets: Path = Path.of(
        requireNotNull(System.getProperty("logent.ponytail.assets")) { "logent.ponytail.assets가 필요해요." },
    )

    private val skillFiles = linkedMapOf(
        "ponytail" to "SKILL.md",
        "ponytail-review" to "review.md",
        "ponytail-audit" to "audit.md",
        "ponytail-debt" to "debt.md",
        "ponytail-gain" to "gain.md",
        "ponytail-help" to "help.md",
    )

    @Test
    fun `플러그인 계약은 ponytail 스킬 6개를 원본 자산 경로로 노출한다`() {
        val plugin = PonytailPlugin()
        assertEquals("com.sleepysoong.logent.ponytail", plugin.id)
        assertEquals("Ponytail", plugin.name)
        assertEquals(BuildConfig.PLUGIN_VERSION, plugin.version)
        assertEquals(4, plugin.apiVersion)

        val skills = plugin.skills()
        assertEquals(skillFiles.keys.toList(), skills.map { it.name })
        skills.forEach { skill ->
            assertEquals(
                "plugins/ponytail/skills/ponytail/${skillFiles.getValue(skill.name)}",
                skill.assetPath,
            )
            assertTrue("스킬 설명이 비었어요: ${skill.name}", skill.description.isNotBlank())
            assertTrue("스킬 설명이 너무 길어요: ${skill.name}", skill.description.length <= 500)
        }
    }

    @Test
    fun `스킬 본문과 homepage·라이선스 원문이 자산에 보존돼 있다`() {
        skillFiles.forEach { (name, file) ->
            val path = assets.resolve("plugins/ponytail/skills/ponytail/$file")
            assertTrue("자산이 없어요: $path", Files.isRegularFile(path))
            val body = Files.readString(path)
            assertTrue("본문이 비었어요: $file", body.isNotBlank())
            assertTrue("frontmatter가 없어요: $file", body.startsWith("---\nname: $name\n"))
        }
        listOf("review.md", "audit.md", "debt.md", "gain.md", "help.md").forEach { file ->
            val body = Files.readString(assets.resolve("plugins/ponytail/skills/ponytail/$file"))
            assertTrue("homepage가 없어요: $file", body.contains("homepage: https://github.com/DietrichGebert/ponytail"))
            assertTrue("라이선스 표기가 없어요: $file", body.contains("license: MIT"))
        }
        val license = Files.readString(assets.resolve("plugins/ponytail/skills/ponytail/LICENSE"))
        assertTrue(license.startsWith("MIT License"))
        assertTrue(license.contains("DietrichGebert"))
        assertTrue(Files.readString(assets.resolve("plugins/ponytail/skills/ponytail/rules.md")).isNotBlank())
    }

    @Test
    fun `설정은 enabled BOOLEAN 하나이며 기본값은 꺼짐이다`() {
        val settings = PonytailPlugin().settings()
        assertEquals(1, settings.size)
        val setting = settings.single()
        assertEquals("enabled", setting.key)
        assertEquals(PluginSettingKind.BOOLEAN, setting.kind)
        assertEquals("false", setting.defaultValue)
        assertTrue(setting.title.isNotBlank())
        assertTrue(setting.description.contains("/ponytail on|off"))
    }

    @Test
    fun `기본 설정에서는 규칙을 시스템 프롬프트에 넣지 않는다`() = runBlocking {
        val plugin = PonytailPlugin()
        val host = FakePluginHost(assetSource = { error("기본값에서는 자산을 읽으면 안 돼요: $it") })
        assertEquals("base", plugin.hooks()!!.systemPrompt(hook(host), "base"))
    }

    @Test
    fun `promptSection은 모드 명령과 review·audit 안내만 담는다`() {
        val section = requireNotNull(PonytailPlugin().promptSection())
        assertTrue(section.contains("/ponytail on"))
        assertTrue(section.contains("/ponytail off"))
        assertTrue(section.contains("/ponytail review"))
        assertTrue(section.contains("/ponytail audit"))
        assertFalse(section.contains("표준 라이브러리"))
        assertFalse(section.contains("file:line"))
        assertTrue(section.length <= 12_000)
    }

    @Test
    fun `slash 명령 메타데이터는 기존 코어 규칙과 같다`() {
        val command = PonytailPlugin().slashCommands().single()
        assertEquals("ponytail", command.name)
        assertEquals("[on | off | review | audit]", command.usage)
        assertEquals("Ponytail 모드를 켜거나 review·audit 프롬프트를 보내요.", command.description)
    }

    @Test
    fun `on과 off는 대화 플래그를 바꾸고 규칙 주입을 켜고 끈다`() = runBlocking {
        val plugin = PonytailPlugin()
        val command = plugin.slashCommands().single()
        val host = rulesHost()

        val onContext = SlashRecorder(host)
        val on = command.execute("on", onContext)
        assertEquals(PluginSlashResult(), on)
        assertEquals(
            listOf("Ponytail 모드를 켰어요. 다음 응답부터 최소 구현 규칙을 적용해요." to false),
            onContext.replies,
        )
        assertEquals(listOf("enabled" to "true"), host.writes)
        assertEquals("true", host.setting("enabled"))
        assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", plugin.hooks()!!.systemPrompt(hook(host), "base"))

        val offContext = SlashRecorder(host)
        val off = command.execute("off", offContext)
        assertEquals(PluginSlashResult(), off)
        assertEquals(listOf("Ponytail 모드를 껐어요." to false), offContext.replies)
        assertEquals(listOf("enabled" to "true", "enabled" to "false"), host.writes)
        assertEquals("false", host.setting("enabled"))
        assertEquals("base", plugin.hooks()!!.systemPrompt(hook(host), "base"))
    }

    @Test
    fun `설정 저장에 실패하면 오류를 알리고 모드를 바꾸지 않는다`() = runBlocking {
        val plugin = PonytailPlugin()
        val offHost = FakePluginHost(
            assetSource = { path ->
                assertEquals(RULES_ASSET, path)
                RULES.toByteArray()
            },
            settings = mapOf("enabled" to "true"),
            writeResult = { _, _ -> Result.failure(IllegalStateException("디스크 오류")) },
        )
        val off = SlashRecorder(offHost)
        plugin.slashCommands().single().execute("off", off)
        assertEquals(listOf("Ponytail 설정을 저장하지 못했어요: 디스크 오류" to true), off.replies)
        assertEquals(listOf("enabled" to "false"), offHost.writes)
        assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", plugin.hooks()!!.systemPrompt(hook(offHost), "base"))

        val onPlugin = PonytailPlugin()
        val onHost = FakePluginHost(
            assetSource = { error("저장에 실패했으므로 모드가 꺼진 채여야 해요: $it") },
            writeResult = { _, _ -> Result.failure(IllegalStateException("권한 없음")) },
        )
        val on = SlashRecorder(onHost)
        onPlugin.slashCommands().single().execute("on", on)
        assertEquals(listOf("Ponytail 설정을 저장하지 못했어요: 권한 없음" to true), on.replies)
        assertEquals(listOf("enabled" to "true"), onHost.writes)
        assertEquals("base", onPlugin.hooks()!!.systemPrompt(hook(onHost), "base"))
    }

    @Test
    fun `review와 audit은 기존 인자 규칙대로 프롬프트를 보내고 모드를 켠다`() = runBlocking {
        listOf("review", "audit").forEach { argument ->
            val plugin = PonytailPlugin()
            val host = rulesHost()
            val recorder = SlashRecorder(host)
            val result = plugin.slashCommands().single().execute(argument, recorder)

            assertEquals(
                "skill 도구로 ponytail-${argument}를 읽고 그 절차대로 현재 변경을 검토해 주세요. " +
                    "발견한 문제는 심각도와 근거(file:line)를 붙여 주세요.",
                result.prompt,
            )
            assertEquals(
                listOf(
                    "Ponytail ${argument}를 시작해요. 변경 범위와 저장소를 읽고, " +
                        "문제·근거·수정·건너뛸 위험을 정리해 주세요." to false,
                ),
                recorder.replies,
            )
            assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", plugin.hooks()!!.systemPrompt(hook(host), "base"))
        }
    }

    @Test
    fun `알 수 없는 인자와 빈 인자는 현재 상태와 사용법을 알려 준다`() = runBlocking {
        val plugin = PonytailPlugin()
        val command = plugin.slashCommands().single()
        val host = rulesHost()

        val empty = SlashRecorder(host)
        val emptyResult = command.execute("", empty)
        assertNull(emptyResult.prompt)
        assertEquals(listOf("Ponytail은 현재 꺼짐이에요. /ponytail on|off|review|audit" to false), empty.replies)

        command.execute("on", SlashRecorder(host))
        val unknown = SlashRecorder(host)
        command.execute("Lite", unknown)
        assertEquals(listOf("Ponytail은 현재 켜짐이에요. /ponytail on|off|review|audit" to false), unknown.replies)
    }

    @Test
    fun `enabled 설정이 규칙 주입을 켠다`() = runBlocking {
        val plugin = PonytailPlugin()
        val hooks = plugin.hooks()!!

        val settingOn = rulesHost(mapOf("enabled" to "true"))
        assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", hooks.systemPrompt(hook(settingOn), "base"))

        val settingOff = rulesHost(mapOf("enabled" to "false"))
        assertEquals("base", hooks.systemPrompt(hook(settingOff), "base"))
    }

    @Test
    fun `on은 enabled 설정을 저장해 다른 대화에도 적용한다`() = runBlocking {
        val plugin = PonytailPlugin()
        val host = rulesHost()
        val hooks = plugin.hooks()!!

        plugin.slashCommands().single().execute("on", SlashRecorder(host, sessionId = "session-a"))
        assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", hooks.systemPrompt(hook(host, sessionId = "session-b"), "base"))
        plugin.onSessionReset()
        assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", hooks.systemPrompt(hook(host, sessionId = "session-a"), "base"))
    }

    @Test
    fun `review의 대화 재정의는 세션별로 격리되고 초기화 때 지워진다`() = runBlocking {
        val plugin = PonytailPlugin()
        val command = plugin.slashCommands().single()
        val host = rulesHost()
        val hooks = plugin.hooks()!!

        command.execute("review", SlashRecorder(host, sessionId = "session-a"))
        assertTrue(host.writes.isEmpty())
        assertEquals("base\n\n## Ponytail 모드 규칙\n$RULES", hooks.systemPrompt(hook(host, sessionId = "session-a"), "base"))
        assertEquals("base", hooks.systemPrompt(hook(host, sessionId = "session-b"), "base"))

        plugin.onSessionReset()
        assertEquals("base", hooks.systemPrompt(hook(host, sessionId = "session-a"), "base"))
    }

    @Test
    fun `규칙 자산을 읽지 못하면 원래 프롬프트를 보존한다`() = runBlocking {
        val plugin = PonytailPlugin()
        val command = plugin.slashCommands().single()
        val host = FakePluginHost(assetSource = { error("자산 없음") }, settings = mapOf("enabled" to "true"))

        assertEquals("base", plugin.hooks()!!.systemPrompt(hook(host), "base"))
        val result = command.execute("review", SlashRecorder(host))
        assertEquals("base", plugin.hooks()!!.systemPrompt(hook(host), "base"))
        assertTrue(requireNotNull(result.prompt).contains("ponytail-review"))
    }

    private class SlashRecorder(
        override val host: PluginHost,
        override val sessionId: String = "session-1",
    ) : PluginSlashContext {
        val replies = mutableListOf<Pair<String, Boolean>>()
        override val workspace: String = "/work"

        override suspend fun reply(text: String, isError: Boolean) {
            replies += text to isError
        }

        override suspend fun runTool(name: String, argumentsJson: String): ToolResult =
            throw UnsupportedOperationException("도구를 쓰지 않아요: $name")
    }

    private fun hook(
        host: PluginHost,
        sessionId: String = "session-1",
    ) = PluginHookContext(
        sessionId = sessionId,
        workspace = "/work",
        model = "test-model",
        provider = "test",
        flags = emptySet(),
        host = host,
    )

    private fun rulesHost(settings: Map<String, String> = emptyMap()): FakePluginHost =
        FakePluginHost(
            assetSource = { path ->
                assertEquals(RULES_ASSET, path)
                RULES.toByteArray()
            },
            settings = settings,
        )

    private companion object {
        const val RULES_ASSET = "plugins/ponytail/skills/ponytail/rules.md"
        const val RULES = "최소 변경 규칙이에요."
    }
}
