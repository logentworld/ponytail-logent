# ponytail-logent

Logent용 독립 플러그인이에요. 원본 [DietrichGebert/ponytail](https://github.com/DietrichGebert/ponytail)의
ponytail 스킬 본문과 `/ponytail` 명령을 코어에 내장하지 않고 `.lpg` 플러그인 하나로 제공해요.

- 플러그인 ID: `com.sleepysoong.logent.ponytail`
- 진입 클래스: `com.sleepysoong.logent.ponytail.PonytailPlugin`
- 플러그인 API: `com.sleepysoong.logent:logent-plugin-api:4.0.0`
- 저장소: https://github.com/logentworld/ponytail-logent (원본 포크)
- 원본 버전: v5.3.3 · 커밋 `96608e078b2d5ea607c5c3b8b5a54d1c1fba6846`

`logent-plugin.json`을 `scripts/plugin-pack.py build --definition logent-plugin.json`의
공통 정의로 사용해요. 플러그인 key는 `sleepysoong:ponytail`, `dependencies`는 `[]`예요.
`sleepysoong`는 명시한 publisher 별칭이며 GitHub 인증 계정이나 소유권을 뜻하지 않아요.
key와 의존성에서 생성한 manifest의 `requires`가 `.lpg`에 함께 들어가요.

## 무엇을 해요

- 게으른 시니어 개발자 모드인 ponytail 규칙을 시스템 프롬프트에 주입해요.
- `/ponytail` 슬래시 명령으로 대화별 on/off와 review·audit 실행을 맡아요.
- 원본 스킬 6개(`ponytail`, `ponytail-review`, `ponytail-audit`, `ponytail-debt`, `ponytail-gain`,
  `ponytail-help`)를 읽기 전용 스킬로 노출해요.

## 명령

```text
/ponytail on       Ponytail 모드를 켜고 enabled 설정을 저장해요.
/ponytail off      Ponytail 모드를 끄고 enabled 설정을 저장해요.
/ponytail review   ponytail-review 스킬을 읽고 현재 변경을 검토해요.
/ponytail audit    ponytail-audit 스킬을 읽고 저장소 전체를 감사해요.
/ponytail          현재 대화의 상태와 사용법을 보여 줘요.
```

인자 비교는 기존 코어와 같이 소문자 기준이며, 그 밖의 값은 상태 안내로 처리해요.
review·audit은 현재 대화에서 모드를 켠 뒤 `skill` 도구로 해당 스킬을 읽으라는 프롬프트를 모델에 보내요.

## 설정

| 키 | 종류 | 기본값 | 설명 |
|----|------|--------|------|
| `enabled` | BOOLEAN | `false` | Ponytail 규칙을 기본으로 적용해요. |

`/ponytail on|off`는 `enabled` 설정을 `PluginHost.writeSetting`으로 영구 저장하고 현재
대화에도 즉시 적용해요. 저장에 실패하면 오류를 알려 주고 모드를 바꾸지 않아요.
`/ponytail review|audit`은 설정을 바꾸지 않고 현재 대화에만 규칙을 켜며, 대화가 바뀌거나
초기화되면 그 재정의는 지워져요.

## 동작 계약

- `PluginHooks.systemPrompt`는 Ponytail이 켜진 대화에서만
  `plugins/ponytail/skills/ponytail/rules.md`를 시스템 프롬프트 끝에 덧붙여요.
  자산을 읽지 못하면 원래 프롬프트를 그대로 돌려줘요.
- `PluginHooks.sessionStart`는 다른 대화의 재정의를 정리하고,
  `PluginHooks.sessionReset`과 `onSessionReset`은 모든 재정의를 지워요.
- `promptSection`은 항상 켜져 있는 짧은 안내예요. 최소 구현 규칙과 review·audit 사용법만
  알려 주고, 실제 규칙 본문은 모드가 켜졌을 때만 주입해요.
- 활성 상태는 `enabled` 설정과 대화별 재정의만으로 판단해요. 코어의 옛 `ponytail` 훅
  플래그는 쓰지 않아요. 대화 재정의가 있으면 설정보다 우선해요.

## 자산 구조

```text
plugin/src/main/assets/plugins/ponytail/skills/ponytail/
├── SKILL.md      # ponytail
├── review.md     # ponytail-review
├── audit.md      # ponytail-audit
├── debt.md       # ponytail-debt
├── gain.md       # ponytail-gain
├── help.md       # ponytail-help
├── rules.md      # 시스템 프롬프트에 주입하는 규칙
└── LICENSE       # 원본 MIT 라이선스
```

원본 저장소의 디렉터리 구조(`skills/ponytail*/SKILL.md`)는 `README.upstream.md`와 함께
포크 이력에 그대로 보존돼 있어요. 이 플러그인의 자산은 Logent가 평평한 경로로 옮긴
사본이며, 본문은 번역·편집하지 않았어요. frontmatter의 한 줄 설명과 `homepage`·`license`는
Logent 사본 형식이에요.

## 빌드와 검증

Gradle 래퍼와 플러그인 API pin을 저장소에 고정했어요. `GITHUB_ACTOR`와 `GITHUB_TOKEN`
(`read:packages`)은 환경 변수로만 넘겨요.

```bash
# JVM 단위 테스트와 lint
./scripts/test.sh

# .lpg 패키지
./scripts/build.sh
```

## 라이선스

- Kotlin 플러그인 코드: GPL-3.0 (`plugin/LICENSE`)
- 원본 ponytail 스킬과 문서: MIT, Copyright (c) 2026 DietrichGebert (`LICENSE`,
  `plugin/src/main/assets/plugins/ponytail/skills/ponytail/LICENSE`)
- 출처와 변경 내역: [`NOTICE`](NOTICE)
