# Hermes Agent Capability Audit and Development Backlog

Audit date: 2026-11-16
Audit branch: `codex/arabic-android-command-routing`

This document prevents duplicate implementation. A capability marked **present in source** is not automatically certified on the user's phone; device/runtime validation is tracked separately.

## Already present in the project (do not rebuild)

| Capability | Existing implementation | Validation boundary |
|---|---|---|
| Native Android UI control | `android_ui_tool`, Accessibility bridge, app launch/snapshot/actions | User confirmed opening Android Settings succeeded through a direct tool call. Arabic routing now also covers screenshot, visible-label click, and Settings variants; new tests are pending CI. |
| Host terminal / embedded Termux-style environment | `terminal_tool`, `NativeAndroidShellTool`, embedded host prefix | Source supports host shell commands; package baseline is immutable and tied to the signed APK. |
| Host package management | `linux_host_pkg_tool`, `HermesTermuxPackageManager` | Package discovery/update/install behavior is implemented with signed-APK trust boundaries. |
| Linux guest environment | `linux_sandbox_tool`, `HermesLinuxSandboxBridge` | Proot distro lifecycle and guest package management already exist. |
| Local durable memory | `hy_memory_tool`, `HermesHyMemoryBridge`, memory aliases | Local retain/recall/list/delete and promoted context already exist. |
| Android automation and scheduling | `android_automation_tool`, Tasker/trigger/widget/tile surfaces | Extensive automation framework already exists; don't create a second scheduler. |
| Device diagnostics | `android_device_diagnostics_tool`, `HermesDeviceDiagnosticsBridge` | Battery/resource/connectivity/sensor and advanced diagnostic reports are present in source. |
| Internet permission and network transport | Android manifest has `INTERNET` and `ACCESS_NETWORK_STATE`; OkHttp is used by native tools; Python `tools/web_tools.py` and `tools/web_tools_extract.py` exist | Web research logic exists in the upstream Python agent, but a complete Android-native exposure, request scoping, and end-to-end source/citation test have not been verified. Do not create a duplicate Python web-search implementation. |
| Open a URL in a browser | Automation intent actions include `open_uri/open_url/open_browser` | Opens a URL; this is separate from model-facing web research. |

## Missing or incomplete capabilities to implement

### Priority A — core project goals
1. **Android web-research bridge:** reuse the existing Python `tools/web_tools.py` / extraction stack where compatible; expose safe search/fetch to the native model with source URLs, bounded content, redirect/host validation, and source-aware results. Do not duplicate the existing Python tool implementation.
2. **Managed port/server lifecycle:** `LlamaCppServerController` and `HermesLocalFileHttpServer` already manage specific app-owned servers. What's missing is a generic allowlisted service manager with start/inspect/stop, process ownership, health checks, logs, and cleanup; the native shell rejects detached/background processes.
3. **APK/app inspection workflow:** orchestrate installed-APK metadata/export where Android permits, JADX/Apktool analysis, log collection, test execution, and a structured error report. Existing device diagnostics and log watchers do not equal a complete APK static-analysis pipeline.
4. **Arabic/Algerian natural-language routing:** extend safe tool selection and argument extraction beyond the Settings phrases already covered. Terminal commands must be explicitly parsed and scoped; never infer arbitrary destructive shell commands.

### Priority B — collaboration and self-improvement
5. **ChatGPT handoff:** Android already has `RemoteChatConsentCoordinator` and `RemoteProcessingConsentStore` for provider-based remote AI processing, plus the Python `agent/chatgpt_web_tools.py` web-auth/tool layer. What remains unverified is a dedicated handoff to the separately installed ChatGPT Android app with bounded context and a return path; do not rebuild the existing remote-consent system.
6. **On-device self-improvement workflow:** use the existing Hermes coding/patch capabilities only behind a narrow proposal → separate Git branch → tests/build → evidence → explicit user approval → merge/install gate. A complete Android-facing approval-and-apply lifecycle is not yet validated.
7. **Project knowledge log:** keep dated decisions, tested outcomes, source links, failures, and remaining tasks in version-controlled project documentation, in addition to the existing runtime memory.

## Validation and build blockers

- The Android manifest already declares Internet access; do not add a duplicate permission.
- The project has a Gradle wrapper, but `android/settings.gradle.kts` and `android/app/build.gradle.kts` require a prepared, verified Python 3.13 source runtime bundle under the Gradle user home (or an explicitly configured bundle). A generic CI workflow that simply runs Gradle will fail until that prerequisite is prepared.
- The current Arabic Settings routing change is in this branch and its unit test is present. Gradle tests/build have not yet been run; no APK is certified by this audit.
- Keep the existing main branch and installed APK unchanged until the new branch passes tests/build and is reviewed.

## Current progress and next implementation order

1. **Arabic routing expansion — implemented, CI pending:** added colloquial Arabic Settings variants, screenshot requests, visible-label tap/click, device/battery status, top-app usage, Wi-Fi, Bluetooth, and sensor command phrases. Added unit tests for screenshot, Wi-Fi scan, and a label-bound tap. This does not yet certify execution on the physical HONOR device.
2. **Android web-research bridge:** inspect and reuse the existing Python web tools; expose them through a tightly scoped native tool bridge and add tests for source URLs, limits, redirects, and untrusted page content.
3. **Generic managed server lifecycle:** build on existing process-controller patterns; add allowlisted start/inspect/stop, loopback default, health checks, log capture, and guaranteed cleanup.
4. **APK inspection/reporting:** connect installed/downloaded APK access, metadata and manifest inspection, static-analysis tools where available, logs, tests, and a structured report.
5. **ChatGPT handoff:** build on existing remote processing consent; only add the separate Android-app handoff if Android intent/API constraints support a reliable return channel.
6. **Self-improvement gate:** branch-only patches, reproducible tests/build evidence, explicit approval before merge/install, rollback path.
7. **Device validation and APK:** wait for all CI checks, fix failures, produce and inspect the debug APK, then provide the artifact and ask for a physical-device acceptance test.

## External open-source research references

These projects were reviewed as design references; no source code was copied into this repository in this pass:

- `https://github.com/ansaribilal14/jarvis` — local-first agent loop, observe/act/verify, plan-first multi-step skills, action logs, and explicit safety boundaries. Its README identifies an MIT license; check third-party notices before reusing any dependency or asset.
- `https://github.com/SE-ABOSALIM/Android_AI_Assistant` — Arabic/multilingual intent classification combined with deterministic rules, argument validation, and Accessibility APIs. Its documented backend requires FastAPI/PyTorch/PostgreSQL/Redis, so importing that stack wholesale would be inappropriate for this 8 GB phone.
- `https://github.com/pluginslab/android-agent-bridge` — authenticated local MCP bridge, accessibility tree inspection, semantic actions, wait-for-node, batch execution, and per-step verification. Treat its LAN server as a reference only; our default must remain loopback unless the user explicitly enables LAN access.

## Validation and build blockers

- The Android manifest already declares Internet access; do not add a duplicate permission.
- The project has a Gradle wrapper, but Android CI must prepare the verified Python 3.13 runtime bundle before Gradle. The previous run successfully prepared this bundle, but it was cancelled before Gradle because a newer commit superseded it.
- LiteRT-LM was updated from `0.18.1` to `0.19.1` after the previous CI's version check blocked the build. The latest Android workflow must verify the pin and continue to Gradle.
- The latest Arabic changes are on this branch; unit tests and APK build have not yet completed. No APK is certified by this audit.
- Keep the main branch and installed APK unchanged until the new branch passes tests/build and is reviewed.

## Safety requirements

- Require explicit confirmation for destructive shell/package/file actions, public network exposure, and code changes that alter installed apps.
- Default local servers to loopback or LAN only; never expose a service publicly by default.
- Treat web pages, downloaded files, model output, and ChatGPT responses as untrusted input.
- Do not send private files, credentials, notifications, or personal data to external services without explicit user approval.
