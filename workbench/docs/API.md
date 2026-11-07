# Local API

All /api/ endpoints require X-Chimera-Token from the local page. Browser origin and host must match localhost or 127.0.0.1 with the configured port. The token changes on server restart. Do not expose it.

- GET /api/config: workspace and command availability.
- GET /api/models: installed models from trusted Ollama /api/tags.
- GET /api/sessions: recent sessions.
- POST /api/sessions {title}: create session.
- GET /api/sessions/{id}: complete saved turns and active_run.
- GET /api/sessions/{id}/export: JSON history and recent audit entries.
- GET /api/sessions/{id}/audit: up to 500 recent audit records.
- POST /api/sessions/{id}/runs {text,model,mode}: mode is chat or agent.
- POST /api/sessions/{id}/commands {argv,cwd}: optional approved command.
- GET /api/runs/{id}/events?after=0: NDJSON stream; resume after cursor.
- POST /api/runs/{id}/approval {id,approve}: resolve exact pending proposal.
- POST /api/runs/{id}/cancel {}: request cancellation.
- GET /api/files?path=.: bounded directory listing.
- GET /api/file?path=relative.txt: bounded UTF-8 file read.

Events: assistant_start, token, context, tool_start, tool_result, approval, approval_resolved, metrics, notice, error, done. Completed event buffers expire when a new run starts; durable history/audits remain. Reconnecting replays events and can show an already-resolved approval until its resolution event arrives; stale submissions are rejected.
