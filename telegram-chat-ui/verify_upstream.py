"""Verify this local extraction against the recorded Telegram source snapshot."""
import hashlib
import json
from pathlib import Path
import sys

module = Path(__file__).resolve().parent
source = Path(sys.argv[1]).resolve()
manifest = json.loads((module / "UPSTREAM.json").read_text(encoding="utf-8"))
errors = []
for record in manifest["files"]:
    original = source / record["upstream"]
    local = module / record["local"]
    if not original.is_file():
        errors.append(f"Missing original: {record['upstream']}")
    elif hashlib.sha256(original.read_bytes()).hexdigest() != record["sha256"]:
        errors.append(f"Changed snapshot: {record['upstream']}")
    if not local.is_file():
        errors.append(f"Missing extraction: {record['local']}")
    if not record.get("license") or not record.get("modifications"):
        errors.append(f"Missing attribution or modifications: {record['local']}")
    if original.is_file() and local.is_file() and original.suffix in {".webp", ".png", ".svg", ".agsl", ".json", ".pack", ".xml"}:
        if original.read_bytes() != local.read_bytes():
            errors.append(f"Resource differs: {record['local']}")
print(json.dumps({"version": manifest["version"], "records": len(manifest["files"]), "errors": errors}, indent=2))
sys.exit(1 if errors else 0)
