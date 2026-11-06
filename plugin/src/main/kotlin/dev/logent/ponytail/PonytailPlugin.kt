package dev.logent.ponytail

import dev.logent.plugin.LogentPlugin
import dev.logent.plugin.PluginHookContext
import dev.logent.plugin.PluginHooks
import dev.logent.plugin.PluginHost
import dev.logent.plugin.PluginSetting
import dev.logent.plugin.PluginSettingKind
import dev.logent.plugin.PluginSkill
import dev.logent.plugin.PluginSlashCommand
import dev.logent.plugin.PluginSlashContext
import dev.logent.plugin.PluginSlashResult
import java.util.concurrent.ConcurrentHashMap

/**
 * ponytail 스킬 6개와 `/ponytail` 명령을 코어 없이 제공해요.
 *
 * 스킬 본문은 플러그인 자산(`plugins/ponytail/skills/ponytail/...`)에서 읽어요. `/ponytail on|off`는
 * `enabled` 설정을 영구 저장하고 대화별 재정의도 함께 세워요. `enabled`가 켜져 있으면
 * 새 대화부터 기본으로 적용되고, 규칙 본문은 활성 대화의 시스템 프롬프트에만 붙어요.
 */
class PonytailPlugin : LogentPlugin {
    override val id: String = "dev.logent.ponytail"
    override val name: String = "Ponytail"
    override val version: String = BuildConfig.PLUGIN_VERSION

    /** 대화별 재정의예요. review·audit처럼 설정을 바꾸지 않는 대화 한정 적용을 맡아요. */
    private val sessionOverrides = ConcurrentHashMap<String, Boolean>()

    override fun skills(): List<PluginSkill> = SKILLS

    override fun settings(): List<PluginSetting> = listOf(
        PluginSetting(
            key = SETTING_ENABLED,
            title = "Ponytail 모드",
            description = "Ponytail 규칙을 기본으로 적용해요. /ponytail on|off로 대화별로 바꿀 수 있어요.",
            kind = PluginSettingKind.BOOLEAN,
            defaultValue = "false",
        ),
    )

    override fun promptSection(): String = """
        Ponytail 플러그인: 모드 켜기는 /ponytail on, 끄기는 /ponytail off예요. 기본값은 꺼짐이에요.
        모드가 켜져 있으면 플러그인이 ponytail 규칙을 시스템 프롬프트에 붙여요.
        변경 diff의 품질 검토는 /ponytail review, 저장소 전체 감사는 /ponytail audit로 시작하세요.
    """.trimIndent()

    override fun slashCommands(): List<PluginSlashCommand> = listOf(
        PluginSlashCommand(
            name = "ponytail",
            usage = "[on | off | review | audit]",
            description = "Ponytail 모드를 켜거나 review·audit 프롬프트를 보내요.",
            handler = ::handleCommand,
        ),
    )

    override fun hooks(): PluginHooks = object : PluginHooks {
        override suspend fun systemPrompt(context: PluginHookContext, prompt: String): String {
            if (!isActive(context.sessionId, context.host)) return prompt
            val rules = context.host.readAsset(RULES_ASSET).getOrElse { return prompt }
            val text = rules.toString(Charsets.UTF_8).trim()
            if (text.isEmpty()) return prompt
            return prompt.trimEnd() + "\n\n## Ponytail 모드 규칙\n" + text
        }

        override suspend fun sessionStart(context: PluginHookContext) {
            // 한 번에 한 대화만 활성이라는 코어 계약에 맞춰 다른 대화의 재정의를 정리해요.
            sessionOverrides.keys.toList().forEach { sessionId ->
                if (sessionId != context.sessionId) sessionOverrides.remove(sessionId)
            }
        }

        override fun sessionReset() {
            sessionOverrides.clear()
        }
    }

    override fun onSessionReset() {
        sessionOverrides.clear()
    }

    private suspend fun handleCommand(args: String, context: PluginSlashContext): PluginSlashResult {
        return when (args.lowercase()) {
            "on" -> setEnabled(enabled = true, context)
            "off" -> setEnabled(enabled = false, context)
            "review", "audit" -> {
                sessionOverrides[context.sessionId] = true
                context.reply(
                    "Ponytail ${args}를 시작해요. 변경 범위와 저장소를 읽고, " +
                        "문제·근거·수정·건너뛸 위험을 정리해 주세요.",
                )
                PluginSlashResult(
                    prompt = "skill 도구로 ponytail-${args}를 읽고 그 절차대로 현재 변경을 검토해 주세요. " +
                        "발견한 문제는 심각도와 근거(file:line)를 붙여 주세요.",
                )
            }
            else -> {
                val state = if (isActive(context.sessionId, context.host)) "켜짐" else "꺼짐"
                context.reply("Ponytail은 현재 ${state}이에요. /ponytail on|off|review|audit")
                PluginSlashResult()
            }
        }
    }

    /** `enabled`를 영구 저장한 뒤에만 대화 플래그를 바꿔요. 저장 실패는 숨기지 않아요. */
    private suspend fun setEnabled(enabled: Boolean, context: PluginSlashContext): PluginSlashResult {
        val failure = context.host.writeSetting(SETTING_ENABLED, enabled.toString()).exceptionOrNull()
        if (failure != null) {
            val detail = failure.message?.takeIf { it.isNotBlank() } ?: failure.javaClass.simpleName
            context.reply("Ponytail 설정을 저장하지 못했어요: $detail", true)
            return PluginSlashResult()
        }
        sessionOverrides[context.sessionId] = enabled
        context.reply(
            if (enabled) {
                "Ponytail 모드를 켰어요. 다음 응답부터 최소 구현 규칙을 적용해요."
            } else {
                "Ponytail 모드를 껐어요."
            },
        )
        return PluginSlashResult()
    }

    /**
     * 대화 재정의가 `enabled` 설정보다 우선해요. 규칙은 이 두 값만 보고 켜고 끄며,
     * 코어의 옛 `ponytail` 훅 플래그는 더 이상 쓰지 않아요.
     */
    internal fun isActive(
        sessionId: String,
        host: PluginHost?,
    ): Boolean {
        sessionOverrides[sessionId]?.let { return it }
        return host?.setting(SETTING_ENABLED) == "true"
    }

    private companion object {
        const val SETTING_ENABLED = "enabled"
        const val RULES_ASSET = "plugins/ponytail/skills/ponytail/rules.md"
        const val SKILLS_ROOT = "plugins/ponytail/skills/ponytail"

        val SKILLS = listOf(
            PluginSkill(
                name = "ponytail",
                description = "가장 작은 완전한 변경으로 문제를 푸는 게으른 시니어 개발자 모드예요.",
                assetPath = "$SKILLS_ROOT/SKILL.md",
            ),
            PluginSkill(
                name = "ponytail-review",
                description = "변경 diff의 버그·보안·부하·누락 테스트·속도와 지울 것을 검토해요.",
                assetPath = "$SKILLS_ROOT/review.md",
            ),
            PluginSkill(
                name = "ponytail-audit",
                description = "저장소 전체의 버그·보안·부하·누락 테스트·속도와 지울 것을 감사해요.",
                assetPath = "$SKILLS_ROOT/audit.md",
            ),
            PluginSkill(
                name = "ponytail-debt",
                description = "미뤄 둔 shortcut 주석을 모아 갚아야 할 기술 부채 목록을 만들어요.",
                assetPath = "$SKILLS_ROOT/debt.md",
            ),
            PluginSkill(
                name = "ponytail-gain",
                description = "ponytail의 절감 효과를 코드·비용·속도 점수판으로 보여 줘요.",
                assetPath = "$SKILLS_ROOT/gain.md",
            ),
            PluginSkill(
                name = "ponytail-help",
                description = "ponytail의 모드·스킬·명령을 짧게 안내해요.",
                assetPath = "$SKILLS_ROOT/help.md",
            ),
        )
    }
}
