# syntax=docker/dockerfile:1
FROM python:3.12-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1 PIP_NO_CACHE_DIR=1 PIP_DISABLE_PIP_VERSION_CHECK=1 HOME=/home/chimera CHIMERA_ALLOW_COMMANDS=false OLLAMA_BASE_URL=http://ollama:11434
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends ca-certificates curl git jq less ripgrep tini && rm -rf /var/lib/apt/lists/*
COPY requirements.txt /app/requirements.txt
RUN python -m pip install --no-cache-dir -r /app/requirements.txt
RUN groupadd --gid 10001 chimera && useradd --uid 10001 --gid chimera --create-home --shell /usr/sbin/nologin chimera && mkdir -p /workspace /data && chown chimera:chimera /workspace /data
COPY --chown=chimera:chimera core.py server.py docker_entrypoint.py index.html /app/
USER chimera:chimera
EXPOSE 8765
STOPSIGNAL SIGTERM
HEALTHCHECK --interval=30s --timeout=5s --start-period=20s --retries=3 CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:8765/',timeout=3).close()"
ENTRYPOINT ["/usr/bin/tini","--"]
CMD ["python","/app/docker_entrypoint.py"]
