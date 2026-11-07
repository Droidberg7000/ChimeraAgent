# Security and limits

This is a single-user local workbench, not an audited production service or OS sandbox. Keep it on localhost. Host/Origin/token checks reduce browser-origin abuse but do not defend against malicious local processes. There is no remote login, TLS, RBAC, tenant isolation or secret vault. Model/file/tool text is rendered with textContent rather than interpreted as HTML.

The model can automatically read workspace files. Never place credentials there. Path traversal and symlinks are rejected, but filesystem races and hard links are not fully prevented; keep workspace private from untrusted writers. The in-process write lock is not a multiprocess lock. Run one server instance per workspace.

Writes require exact stored-proposal approval, expire after 300 seconds, recheck prior content, save overwritten UTF-8 text under data/backups/, and replace through a temporary file. Backups/audits/exports are plaintext and do not auto-expire. Backups are UUID-named and reported in tool results. There is no deletion, rollback or restore tool. Filesystem effects and audit records are not one atomic transaction.

Commands default OFF. Every command requires approval. No implicit shell is used, but approving bash/python/etc can execute arbitrary code. Native commands inherit OS-user permissions and environment. Container commands can access mounted workspace/data and network; a container is not a complete hostile-code boundary. Never mount the Docker socket, broad home directories, or credentials.

Limits: 12 model rounds, 16 calls per response, 2 active runs globally, 1 per session, 128 KB UTF-8 reads/writes, 500 directory entries, 2000 search files, 100 search matches, 60-second command timeout, 64 KB captured output. Output-size checking is polled and is not a hard disk quota. POSIX process groups are killed, but deliberately detached processes can evade cleanup. Windows cleanup kills only the direct child.

Cancellation during inference is checked between incoming chunks; a blocked read may wait up to 600 seconds. The app does not stop/unload the Ollama service. Closing a browser does not cancel a run. Event buffers and approvals are in memory, not restart-durable. Chat turns persist at run completion; audit entries persist as actions occur. A crash can leave effects without a saved final turn. Inspect before retrying.

Recent-history selection uses a 24000-character heuristic; old whole turns are omitted and current-turn context has a 100000-character guard. These are not exact token bounds. No vector memory, automatic summaries, indefinite queue, SSH, PTY, SFTP, MCP, or remote-host orchestration is implemented.

Use local model weights and independently verify offline operation. The configured Ollama service can have network access; rejecting cloud-looking names is not an enforced offline policy. Prompt instructions are not security boundaries.
