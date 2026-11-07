# Docker deployment

Run Compose from workbench/. The base file is CPU-only. Copy .env.example to .env, choose OLLAMA_MODEL, and run `docker compose up -d --build`. The one-shot model-init service contacts the running Ollama server, retries a failed pull up to three times, and gates agent startup on success. It is Compose initialization, not a hook for every engine-triggered container restart.

Rerun explicitly: `docker compose run --rm model-init`. Follow progress with `docker compose logs -f model-init`. Changing OLLAMA_MODEL downloads a different model; choose it separately in the UI.

Named volumes: agent_workspace stores project files; agent_data stores plaintext SQLite sessions/audits and backups; ollama_models stores downloaded models. `docker compose down` preserves them; `docker compose down -v` deletes them. Back up these volumes before upgrades. Database/WAL copies need a consistent SQLite backup or stopped writer.

Only 127.0.0.1:8765 is published. Do not change the host port without adapting the application's Host/Origin policy. Ollama has no host port publication. The app listens on 0.0.0.0 only inside its container.

To enable commands, set CHIMERA_ALLOW_COMMANDS=true in .env, then `docker compose up -d --force-recreate agent`. Commands run as UID 10001. The root filesystem is read-only, /tmp is noexec, and /workspace and /data are writable. These restrictions can break build tools and are not a complete untrusted-code sandbox. Never mount /var/run/docker.sock or host credentials.

Native bind mounts must be writable by UID/GID 10001; named volumes avoid many initial ownership issues. Container HOME is not writable under the supplied read-only setup. Explicitly provision a narrowly scoped cache volume if tools require one.

Health checks confirm service responsiveness, not model correctness. Model downloads require outbound networking. No GPU runtime is installed by the agent Dockerfile. Pin validated Python dependencies and image digests for reproducible releases.
