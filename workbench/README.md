# Chimera Local Workbench

Complete original local Ollama assistant inside ChimeraAgent. Existing AI-STUDIO, CANON, GAME and root README are untouched. This is an independent Chaterm-inspired workbench, not a Chaterm fork or feature-complete replacement.

## Docker quick start

From this directory:

```sh
cp .env.example .env
docker compose up -d --build
docker compose logs -f model-init
```

Open http://127.0.0.1:8765 and select the downloaded model. The startup order is Ollama healthy -> automatic model pull -> agent start. First startup requires internet and sufficient disk space. The model choice is not a hardware-fit guarantee.

NVIDIA, after configuring the host driver and NVIDIA Container Toolkit:

```sh
docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build
```

AMD/ROCm with compatible hardware and drivers:

```sh
docker compose -f docker-compose.yml -f docker-compose.amd.yml up -d --build
```

Use one GPU override, not both. GPU access belongs to Ollama, not the agent. CPU is the base configuration.

## Native quick start

Requires Python 3.10+ and a running local Ollama service.

```sh
python3 -m venv .venv
. .venv/bin/activate
python -m pip install -r requirements.txt
ollama pull qwen3:4b
python server.py
```

On Windows use `.venv\Scripts\python.exe` instead of activating a POSIX shell environment.

## Features

Streaming chat; local model picker; multiple saved sessions; SQLite history and audits; full-turn bounded context; serial multi-round tools; workspace file browser, search and reads; approval-gated file writes with diffs, stale-content checks and backups; optional approved non-interactive command execution; cancellation; resumable in-memory event streams; JSON export; non-root Docker runtime; CPU/NVIDIA/AMD deployment files.

Commands are disabled by default. Set CHIMERA_ALLOW_COMMANDS=true in .env and recreate the agent, or use `python server.py --allow-commands` natively. Every command still requires approval. Commands are NOT an interactive PTY and are NOT sandboxed by the application.

## Tests

```sh
python -m unittest discover -s tests -v
```

CI installs requirements and runs tests on Python 3.10 and 3.12. This consolidated repository payload has not been executed in the authoring environment. Earlier prototype test results do not certify this revision. No live model, Docker build, GPU or browser end-to-end verification is claimed. Dependency ranges and image tags are not a reproducible lockfile; pin tested versions/digests before deployment.

See docs/INSTALL.md, docs/DOCKER.md, docs/GPU-TROUBLESHOOTING.md, docs/SECURITY.md and docs/API.md.
