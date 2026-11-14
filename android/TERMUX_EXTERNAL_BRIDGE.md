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

The implementation in this branch currently provides installation and
permission readiness checks only. It does not yet execute commands or claim
that Termux:API results are available to the agent. The next implementation
step must add a fixed command registry, explicit user approval for sensitive
operations, bounded execution, cancellation, and a result-return path using a
PendingIntent. Read-only diagnostics should be qualified before any write,
communication, or device-control actions are exposed.

## Protocol reference

See the official Termux RUN_COMMAND intent documentation:
https://github.com/termux/termux-app/wiki/RUN_COMMAND-Intent
