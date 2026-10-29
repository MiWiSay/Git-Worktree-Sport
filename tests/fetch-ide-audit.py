"""Fetch official IDE Java libraries for API auditing, not complete runnable installations.

Uses HTTP ranges to avoid downloading bundled language models, browsers and runtimes.
Every extracted ZIP entry is CRC/size checked; identical libraries share a local cache.
"""
import argparse
import concurrent.futures
import io
import json
import pathlib
import shutil
import struct
import threading
import time
import urllib.request
import zipfile
import zlib


def fetch_range(url, first, last):
    for attempt in range(4):
        try:
            request = urllib.request.Request(url, headers={"Range": f"bytes={first}-{last}"})
            with urllib.request.urlopen(request, timeout=90) as response:
                if response.status != 206:
                    raise ValueError("Server did not honor the requested ZIP byte range")
                data = response.read()
                if len(data) != last - first + 1:
                    raise ValueError("Incomplete ZIP byte range")
                return data
        except Exception:
            if attempt == 3:
                raise
            time.sleep(attempt + 1)


def main():
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("releases", type=pathlib.Path)
    parser.add_argument("--codes", nargs="+", default=["DG", "WS", "RR", "PCP", "IIU"])
    parser.add_argument("--workers", type=int, default=12)
    args = parser.parse_args()
    root = pathlib.Path(__file__).resolve().parent.parent / "build" / "ide-api-audit"
    cache = root / "entry-cache"
    cache.mkdir(parents=True, exist_ok=True)
    releases = json.loads(args.releases.read_text(encoding="utf-8-sig"))
    lock = threading.Lock()
    for code in args.codes:
        release = releases[code][0]
        download = release["downloads"]["windowsZip"]
        url, size = download["link"], download["size"]
        target = root / f"{code}-{release['build']}"
        target.mkdir(parents=True, exist_ok=True)
        tail = fetch_range(url, size - 65557, size - 1)
        end = tail.rfind(b"PK\x05\x06")
        fields = struct.unpack_from("<4s4H2LH", tail, end)
        central_size, central_offset = fields[5:7]
        central = fetch_range(url, central_offset, central_offset + central_size - 1)
        archive = zipfile.ZipFile(io.BytesIO(central + tail[end:]))
        entries = [entry for entry in archive.infolist() if (
            entry.filename in ("product-info.json", "build.txt", "bin/idea.properties")
            or (entry.filename.endswith(".jar") and entry.filename.startswith(("lib/", "modules/", "plugins/vcs-git/")))
        )]
        print(f"{code} {release['version']}: {len(entries)} entries, "
              f"{sum(e.compress_size for e in entries) // 1048576} MiB before cache reuse", flush=True)
        progress = [0]

        def extract(entry):
            path = target / entry.filename
            parts = pathlib.PurePosixPath(entry.filename)
            if parts.is_absolute() or ".." in parts.parts or ":" in entry.filename or "\\" in entry.filename:
                raise ValueError(f"Unsafe ZIP entry: {entry.filename}")
            key = f"{entry.CRC:08x}-{entry.file_size}-{path.name}"
            cached = cache / key
            if not cached.exists():
                offset = entry.header_offset + central_offset
                header = fetch_range(url, offset, offset + 29)
                if header[:4] != b"PK\x03\x04":
                    raise ValueError(f"Invalid ZIP header: {entry.filename}")
                name_length, extra_length = struct.unpack_from("<HH", header, 26)
                start = offset + 30 + name_length + extra_length
                compressed = fetch_range(url, start, start + entry.compress_size - 1) if entry.compress_size else b""
                if entry.compress_type == zipfile.ZIP_DEFLATED:
                    data = zlib.decompress(compressed, -15)
                elif entry.compress_type == zipfile.ZIP_STORED:
                    data = compressed
                else:
                    raise ValueError("Unsupported ZIP compression")
                if len(data) != entry.file_size or zlib.crc32(data) != entry.CRC:
                    raise ValueError(f"ZIP integrity check failed: {entry.filename}")
                cached.write_bytes(data)
            path.parent.mkdir(parents=True, exist_ok=True)
            if not path.exists() or path.stat().st_size != entry.file_size:
                shutil.copyfile(cached, path)
            with lock:
                progress[0] += 1
                if progress[0] % 75 == 0:
                    print(f"  {code}: {progress[0]}/{len(entries)}", flush=True)

        with concurrent.futures.ThreadPoolExecutor(max_workers=args.workers) as pool:
            list(pool.map(extract, entries))
        (target / "audit-source.json").write_text(json.dumps({
            "code": code, "version": release["version"], "build": release["build"], "url": url,
            "scope": "Official platform/Git JAR subset; not a runnable IDE installation",
            "integrity": "ZIP-entry uncompressed size and CRC32 verified", "entries": len(entries),
        }, indent=2), encoding="utf-8")
        print(f"READY: {target}", flush=True)


if __name__ == "__main__":
    main()
