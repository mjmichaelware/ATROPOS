# ATROPOS Pass 13 Provider Truth and Queue Daemon

Pass 13 backend adds two runtime-control pieces without UI work:

- canonical provider truth
- local durable queue daemon/watchdog

## Provider Truth

`ProviderTruthService` combines:

- `StaticProviderDescriptorRegistry`
- `ProviderFactory` adapter construction
- `AgentProviderSelector` ask and patch orders
- Ollama health probing
- descriptor required environment contracts

It reports each provider id, category, cost mode, key presence, descriptor presence, adapter presence, executable support, health, ask eligibility, patch eligibility, paid lock, and missing requirements. It never prints key values.

`/providers inventory`, `/status endpoints`, and `/agent status` use this truth source. Provider ask and patch priority order are preserved.

## Daemon

The daemon is local and durable. It consumes existing Pass 12 queue entries through `AgentQueueService`; it does not bypass leases, retries, cancellation, recovery, smoke safety, patch safety, verification, paid locks, or no-commit/no-push policy.

Commands:

- `/agent daemon once`
- `/agent daemon foreground`
- `/agent daemon start`
- `/agent daemon stop`
- `/agent daemon status`
- `/agent daemon doctor`

Storage:

```text
.atropos/agent/daemon/state.meta
.atropos/agent/daemon/daemon.lock
.atropos/agent/daemon/stop.request
.atropos/agent/daemon/events.log
.atropos/agent/daemon/daemon.log
```

State writes use sibling temp files plus atomic replacement. The daemon uses a JDK `FileChannel` single-instance lock and a UUID instance id with pid and host owner metadata.

Foreground mode polls the queue every 15 seconds by default. Polling is bounded from 2 to 300 seconds and can be overridden by `ATROPOS_AGENT_DAEMON_POLL_SECONDS`.

Background start launches a real Java process running `atropos.jar --agent-daemon-foreground` and verifies that daemon state becomes running.

Stop is durable through `stop.request`; foreground exits gracefully at the next loop boundary. Optional Termux wake lock is only attempted when `ATROPOS_TERMUX_WAKELOCK=1`.

The daemon doctor uses temporary storage and does not call providers.
