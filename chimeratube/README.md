# ChimeraTube client + integrated gateway

The repaired Java ME client and CT1 gateway now share this component. Android, JavaBoyChimera, the AI workbench and main are unchanged. Source integration is not a claimed handset-tested JAR/COD or third-party project fusion.

## Start

Python 3.10+ uses no pip dependencies; preparation requires ffmpeg and ffprobe:

```sh
python make_demo.py --root media
python gateway.py --root media serve
```

Or, from this directory:

```sh
cp .env.example .env
docker compose up -d --build
docker compose exec gateway python make_demo.py --root /media
curl 'http://127.0.0.1:9090/v1/search?q=&source=catalog'
```

The demo is real generated video/tone. The catalog otherwise starts empty. IDs cannot overwrite existing media. Docker publishes loopback only. For a phone, explicitly set GATEWAY_BIND to your host's private LAN IP and configure that URL in the client. This development HTTP server has NO authentication/TLS: never expose it publicly or on untrusted networks.

Place permitted files under imports/:

```sh
docker compose exec gateway python gateway.py --root /media ingest /imports/myclip.mp4 --id myclip --title 'My clip'
```

Optional INVIDIOUS_ORIGIN must be an instance you trust. Select invidious in the client. Operator import: `python gateway.py --root media import-invidious --origin https://YOUR-INSTANCE --id VIDEO_ID`. It requires a supplied progressive itag 18 URL. No guessed URLs, cookies, DRM/access-control bypass or fake fallback. Live/upcoming/paid/premium sources are unsupported. Search metadata is playable only after local profiles exist.

## Contract

CT1: header CT1<TAB>catalog|invidious<LF>; percent-encoded UTF-8 ID/title/author/seconds/profiles, TAB-separated, one row per LF. Limits: 20 rows, 4096-byte rows, 32768 bytes total. Truncation preserves complete rows. GET/HEAD /v1/media/ID/mp4 or /3gp supports single byte ranges; missing media=404, invalid range=416.

Profiles: MPEG-4 Part 2 MP4 320x240/15fps; H.263 3GP 176x144/15fps; mono AAC-LC 22050 Hz when audio exists. FFprobe checks dimensions/codec; faststart moves the index forward. Handset codec/rendering compatibility remains untested.

Client unchanged: incremental parsing, bounded cleanup, coalesced/generation-checked UI, gateway/source-bound RMS metadata and Canvas readiness before playback. Cache is metadata, not offline video. Real builds require CLDC_API, MIDP_API, MMAPI_API, legacy-capable ECJ_JAR and genuine PREVERIFY: run `python build_client.py`. No proprietary tools or fake COD are supplied.

## Validation and security

`python -B -m unittest -v test_gateway`: 14 tests passed here, including actual localhost HTTP/HEAD/ranges and real FFmpeg preparation of both profiles. Invidious is fixture-tested, not live-tested. Docker build, Java/MIDP compilation, preverification and handset playback were NOT run. See GATEWAY-VALIDATION.json; client VALIDATION.json is an older preparation snapshot, not live CI status. Existing client CI tests the portable Java core separately.

CLI ingestion is trusted-operator work, not a public upload/transcoding API. Input/download <=200 MiB, clips <=900 s, bounded HTTP workers/feed/metadata, FFmpeg timeout. No redirects/environment proxies. Media hosts are the configured instance or public-address-checked Googlevideo domains. DNS checks are NOT pinning; use egress controls and trust the origin. Network deadlines are best-effort between reads. Keep media storage private; filesystem races are not fully prevented. Writer locks/renames are not crash-atomic across all files; inspect stale .writer.lock/orphans after crashes. Use patched FFmpeg, disk quotas and isolation for hostile files. Docker caps memory/PIDs and has a read-only root. Do not use down -v unless deleting media intentionally. Pin validated image digests before release.
