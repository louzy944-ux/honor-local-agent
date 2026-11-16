# Hermes Agent Capability Audit and Development Backlog

Audit date: 2026-11-16
Audit branch: `codex/arabic-android-command-routing`

This document prevents duplicate implementation. A capability marked **present in source** is not automatically certified on the user's phone; device/runtime validation is tracked separately.

## Already present in the project (do not rebuild)

| Capability | Existing implementation | Validation boundary |
|---|---|---|
| Native Android UI control | `android_ui_tool`, Accessibility bridge, app launch/snapshot/actions | User confirmed opening Android Settings succeeded through a direct tool call. Natural-language Arabic routing is still being improved. |
| Host terminal / embedded Termux-style environment | `terminal_tool`, `NativeAndroidShellTool`, embedded host prefix | Source supports host shell commands; package baseline is immutable and tied to the signed APK. |
| Host package management | `linux_host_pkg_tool`, `HermesTermuxPackageManager` | Package discovery/update/install behavior is implemented with signed-APK trust boundaries. |
| Linux guest environment | `linux_sandbox_tool`, `HermesLinuxSandboxBridge` | Proot distro lifecycle and guest package management already exist. |
| Local durable memory | `hy_memory_tool`, `HermesHyMemoryBridge`, memory aliases | Local retain/recall/list/delete and promoted context already exist. |
| Android automation and scheduling | `android_automation_tool`, Tasker/trigger/widget/tile surfaces | Extensive automation framework already exists; don't create a second scheduler. |
| Device diagnostics | `android_device_diagnostics_tool`, `HermesDeviceDiagnosticsBridge` | Battery/resource/connectivity/sensor and advanced diagnostic reports are present in source. |
| Internet permission and network transport | Android manifest has `INTERNET` and `ACCESS_NETWORK_STATE`; OkHttp is used by native tools | This enables app networking, but is not by itself a dedicated web research/search feature. |
| Open a URL in a browser | Automation intent actions include `open_uri/open_url/open_browser` | Opens a URL; it does not fetch, search, extract, or cite web content for the model. |

## Missing or incomplete capabilities to implement

### Priority A — core project goals
1. **Dedicated web research tools:** safe `web_search` and `web_fetch` with source URLs, bounded content, redirect/host validation, and source-aware results. Existing browser launching and generic network permission are not substitutes.
2. **Managed port/server lifecycle:** start, inspect, stop, and report local services/ports. The current native shell explicitly rejects detached/background processes, so a persistent server manager is needed rather than just a shell command.
3. **APK/app inspection workflow:** orchestrate installed-APK metadata/export where Android permits, JADX/Apktool analysis, log collection, test execution, and a structured error report. Existing device diagnostics and log watchers do not equal a complete APK static-analysis pipeline.
4. **Arabic/Algerian natural-language routing:** extend safe tool selection and argument extraction beyond the Settings phrases already covered. Terminal commands must be explicitly parsed and scoped; never infer arbitrary destructive shell commands.

### Priority B — collaboration and self-improvement
5. **ChatGPT app bridge:** user-approved handoff of a bounded question/context to the ChatGPT app and a safe way to bring the response back. Existing browser URL launch and email composer are not this integration.
6. **Self-improvement workflow:** research a proposed capability, produce a patch on a separate branch, run tests/build, report evidence, and require user approval before merging/installing.
7. **Project knowledge log:** keep dated decisions, tested outcomes, source links, failures, and remaining tasks in version-controlled project documentation, in addition to the existing runtime memory.

## Validation and build blockers

- The Android manifest already declares Internet access; do not add a duplicate permission.
- The project has a Gradle wrapper, but `android/settings.gradle.kts` and `android/app/build.gradle.kts` require a prepared, verified Python 3.13 source runtime bundle under the Gradle user home (or an explicitly configured bundle). A generic CI workflow that simply runs Gradle will fail until that prerequisite is prepared.
- The current Arabic Settings routing change is in this branch and its unit test is present. Gradle tests/build have not yet been run; no APK is certified by this audit.
- Keep the existing main branch and installed APK unchanged until the new branch passes tests/build and is reviewed.

## Next implementation order

1. Finish and validate the current Arabic Settings routing change.
2. Add dedicated web search/fetch tools.
3. Add a managed local port/server lifecycle.
4. Add the APK inspection/reporting workflow.
5. Add the ChatGPT handoff and self-improvement approval loop.
6. Extend Arabic command coverage and keep this audit updated.

## Safety requirements

- Require explicit confirmation for destructive shell/package/file actions, public network exposure, and code changes that alter installed apps.
- Default local servers to loopback or LAN only; never expose a service publicly by default.
- Treat web pages, downloaded files, model output, and ChatGPT responses as untrusted input.
- Do not send private files, credentials, notifications, or personal data to external services without explicit user approval.
