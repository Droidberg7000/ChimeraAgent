FROM python:3.12-slim-bookworm
ENV PYTHONDONTWRITEBYTECODE=1 PYTHONUNBUFFERED=1
WORKDIR /app
RUN apt-get update && apt-get install -y --no-install-recommends ffmpeg ca-certificates && rm -rf /var/lib/apt/lists/* && groupadd -g 10001 chimera && useradd -u 10001 -g chimera -m chimera && mkdir /media && chown chimera:chimera /media
COPY gateway.py make_demo.py /app/
USER chimera:chimera
EXPOSE 9090
HEALTHCHECK --interval=30s --timeout=5s CMD python -c "import urllib.request; urllib.request.urlopen('http://127.0.0.1:9090/health',timeout=3).close()"
CMD ["python","gateway.py","--root","/media","serve","--bind","0.0.0.0"]
