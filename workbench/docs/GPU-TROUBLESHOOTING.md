# GPU verification and troubleshooting

NVIDIA Linux: first run host `nvidia-smi`. Configure NVIDIA Container Toolkit for Docker; for standard rootful Docker the runtime command is `sudo nvidia-ctk runtime configure --runtime=docker`, followed by a planned Docker service restart. Test independently with `docker run --rm --gpus all ubuntu nvidia-smi`. Do not use Linux service commands as Docker Desktop setup instructions.

Start NVIDIA: `docker compose -f docker-compose.yml -f docker-compose.gpu.yml up -d --build`. Use both file arguments on subsequent management commands. Check merged configuration with the same arguments followed by `config`. GPU reservation must be under ollama.

Run a model, then execute `docker compose exec ollama ollama ps` against that running stack. PROCESSOR=100% GPU means full GPU model placement, not 100% instantaneous utilization. CPU/GPU split means partial placement. An empty list means no model is currently loaded. Watch host `nvidia-smi -l 1` during generation for activity and VRAM usage.

If 100% CPU: test host driver, Docker visibility, actual Ollama-container visibility, then `docker compose logs --tail=200 ollama`. Set OLLAMA_DEBUG=1 on the Ollama service and recreate it for discovery details. Remove unintended CUDA_VISIBLE_DEVICES=-1 or CPU-specific OLLAMA_LLM_LIBRARY overrides. Check GPU/driver compatibility. Free VRAM, use a smaller model, or reduce application num_ctx. Rebuild the agent after source changes.

AMD: use the rocm override, compatible host drivers/GPU, and inspect /dev/kfd and /dev/dri permissions. Device access may require host video/render numeric group IDs via group_add. Do not add blanket privileged mode as a generic fix.

Apple Silicon/macOS: this CUDA/ROCm Docker configuration does not expose Metal. Run Ollama natively and configure a deliberate host-service networking policy if using a containerized agent. A loopback-only host listener is not automatically reachable through host.docker.internal.

Model/server support is version-dependent. GPU configurations are supplied but not hardware-tested in this repository revision.
