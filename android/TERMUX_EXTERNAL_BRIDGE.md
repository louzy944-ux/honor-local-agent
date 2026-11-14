# External Termux bridge setup

The Android app's embedded Linux environment and the separately installed
Termux app are different Android application sandboxes. Installing the
Termux:API add-on does not automatically grant Agent access to it.

## Required user setup

1. Install Termux and the matching Termux:API add-on from the same trusted
   distribution source.
2. In Termux, create or edit `~/.termux/termux.properties` and set
   `allow-external-apps = true`.
3. Restart Termux.
4. Open Android Settings > Apps > Agent > Permissions > Additional
   permissions, then grant **Run commands in Termux environment**.
5. Return to Agent and re-check the external Termux bridge status.

Agent requests the Android permission
`com.termux.permission.RUN_COMMAND` and declares package visibility for
`com.termux` and `com.termux.api`.

## Security boundary

Termux's documented RUN_COMMAND service can execute commands with the Termux
app's UID. It is therefore equivalent to granting a powerful local automation
capability. The model must never supply an arbitrary executable path, shell
string, or unrestricted argument list.

The branch now includes one narrowly allowlisted, read-only diagnostic:
`termux-battery-status`. The app sends the fixed executable path to
`com.termux.app.RunCommandService` with a one-shot mutable PendingIntent on
Android 12+, then stores the returned stdout, stderr, exit code, and Termux
error details in private app preferences through `HermesTermuxResultService`.
The result can be read by `HermesExternalTermuxBridge.lastBatteryStatusResult()`.

This is a bridge primitive, not yet a chat-agent tool or a visible diagnostics
screen. The model cannot choose a command or arguments. No write, network,
communication, or device-control commands are exposed. Termux must be version
0.109 or newer for result delivery. The command must be requested while the
Agent app is in the foreground; the app must also have the RUN_COMMAND
permission and Termux must have `allow-external-apps=true`.

## Protocol reference

See the official Termux RUN_COMMAND intent documentation:
https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
