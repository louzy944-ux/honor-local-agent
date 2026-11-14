# HONOR local-agent integration profile

This profile records the intended device configuration for the HONOR Android
build. It does not claim that a model or external Termux command bridge has
passed physical-device qualification.

## Existing capabilities to reuse

The Android application already contains:

- A local GGUF import path and a llama.cpp backend.
- A loopback OpenAI-compatible API client.
- Native Android device tools and permission-gated accessibility services.
- An embedded Termux-style Linux environment used by the app's own runtime.
- A separate Termux CLI/runtime in the repository.

Do not replace these systems with a second shell or a second model runtime.

## SmolLM3 3B GGUF

The user's existing model source is:

- Repository: `bartowski/HuggingFaceTB_SmolLM3-3B-GGUF`
- Suggested quantization for initial testing: `HuggingFaceTB_SmolLM3-3B-Q4_K_M.gguf`
- Published file size: approximately 1.92 GB.
- Published SHA-256: `519732558d5fa7420ab058e1b776dcfe73da78013c2fe59c7ca43c325ef89132`

This is a GGUF artifact for llama.cpp, not a LiteRT-LM model. Import it through
**Settings > Models > Import model file**. Do not mark it release-certified
or mobile-recommended until the exact artifact has passed the app's runtime
completion checks on the target HONOR device.

The file's model card describes eight supported languages. Arabic quality and
tool-calling reliability are not certified by that language count; test both
separately before depending on Arabic instructions or autonomous actions.

## Connecting a Termux-hosted llama.cpp server

If the user already runs llama-server in Termux, keep it bound to loopback
(`127.0.0.1`) and use the app's existing custom OpenAI-compatible provider
configuration rather than starting a duplicate model server inside Agent.

The expected API base path is `/v1`. Use the exact port configured in
Termux. Do not expose the server to the LAN or set permissive CORS merely to
make the app connect.

This route is a local inference connection. It does not, by itself, grant the
Agent app permission to execute Termux commands or call Termux:API.

## Termux:API integration boundary

The Android app's native device tools and the Termux:API app are separate
permission domains. A future bridge must:

1. Detect the installed Termux and Termux:API packages without relying on
   shell-only package-manager output.
2. Require the user to enable Termux's external-app command permission.
3. Use an explicit command allowlist; never expose an unrestricted
   `RUN_COMMAND` path to model-generated text.
4. Bound execution time and output size, propagate cancellation, and report
   permission failures clearly.
5. Keep sensitive operations (calls, messages, contacts, files, settings)
   behind explicit user confirmation and Android permissions.

Until that bridge is implemented and instrumented tests pass, advertise
native Android tools and embedded shell tools accurately; do not claim that
the app can control the separate Termux installation.

## Qualification checklist

- [ ] Build the Full debug APK from this branch.
- [ ] Install on the target HONOR device and verify the app's package identity.
- [ ] Import the exact SmolLM3 GGUF file and verify its SHA-256.
- [ ] Start llama.cpp and obtain a non-empty completion.
- [ ] Verify Arabic prompts separately from English prompts.
- [ ] Verify model tool calls against a harmless read-only Android tool.
- [ ] Implement and test the explicit Termux:API bridge before advertising it.
