# Installation

Python 3.10+ is required. On Debian/Ubuntu: `sudo apt-get update && sudo apt-get install python3 python3-venv python3-pip git ca-certificates curl`. Alpine uses `apk add python3 py3-pip git ca-certificates curl`; native dependencies may additionally need build-base, python3-dev and cargo. Alpine uses musl, so glibc inference binaries are not automatically compatible.

Use a project virtual environment; never run the agent as root merely to make pip or file permissions work. Install Ollama separately, start its service, and pull a local tool-capable model. Agent mode requires tool calling; Chat mode does not pass tool schemas.

From workbench/: `python3 -m venv .venv`, `. .venv/bin/activate`, `python -m pip install -r requirements.txt`, `python server.py`. Default URL: http://127.0.0.1:8765.

CLI options: --workspace, --data, --port, --allow-commands. State must remain outside workspace. OLLAMA_BASE_URL defaults to http://127.0.0.1:11434; configure only a trusted endpoint. The application disables HTTP proxy environment routing. Obvious cloud/routed model tags are rejected, but that is not verified network isolation.

This revision requests num_ctx=4096 and num_predict=2048 to reduce the prototype's default memory demand. Change server.py and rebuild for different limits. Context selection uses characters, not an exact tokenizer.
