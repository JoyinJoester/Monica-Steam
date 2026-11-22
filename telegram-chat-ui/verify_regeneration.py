"""Verify extraction in an empty directory without copying host implementations."""

import hashlib
import json
from pathlib import Path
import shutil
import subprocess
import sys
import tempfile


MODULE = Path(__file__).resolve().parent
HOST_ADAPTERS = {
    "src/main/java/com/monica/telegramchat/" + name + ".java"
    for name in (
        "TelegramAttachmentTabsView", "TelegramChatComposerView",
        "TelegramChatHeaderView", "TelegramGlassView", "TelegramMenuRow",
        "TelegramMessageTextView", "TelegramPopupLayout", "TelegramWallpaperDrawable",
    )
}


def main():
    source = Path(sys.argv[1]).resolve()
    expected = json.loads((MODULE / "UPSTREAM.json").read_text(encoding="utf-8"))
    with tempfile.TemporaryDirectory(prefix="monica-telegram-extraction-") as directory:
        output = Path(directory)
        for importer in MODULE.glob("import_*.py"):
            shutil.copyfile(importer, output / importer.name)
        subprocess.run(
            [sys.executable, str(output / "import_upstream.py"), str(source)],
            check=True, stdout=sys.stderr,
        )
        actual = json.loads((output / "UPSTREAM.json").read_text(encoding="utf-8"))
        paths = {entry["local"] for entry in expected["files"]} | {
            entry["local"] for entry in actual["files"]
        }
        errors = []
        generated = []
        for path in sorted(paths):
            checked, rebuilt = MODULE / path, output / path
            if not checked.is_file():
                errors.append("Missing checked file: " + path)
            if path in HOST_ADAPTERS:
                if rebuilt.exists():
                    errors.append("Host adapter unexpectedly generated: " + path)
                continue
            generated.append(path)
            if not rebuilt.is_file():
                errors.append("Missing generated file: " + path)
            elif checked.is_file():
                checked_hash = hashlib.sha256(checked.read_bytes()).digest()
                if checked_hash != hashlib.sha256(rebuilt.read_bytes()).digest():
                    errors.append("Generated file differs: " + path)
        if expected != actual:
            errors.append("Generated source manifest differs")
        if not HOST_ADAPTERS.issubset(paths):
            errors.append("Recorded host adapter list differs")
        result = {
            "source_records": len(actual["files"]),
            "unique_recorded_files": len(paths),
            "generated_files": len(generated),
            "preserved_host_files": sorted(HOST_ADAPTERS),
            "manifest_matches": expected == actual,
            "errors": errors,
        }
        print(json.dumps(result, ensure_ascii=False, indent=2))
        return 1 if errors else 0


if __name__ == "__main__":
    if len(sys.argv) != 2:
        raise SystemExit("Usage: verify_regeneration.py <Telegram-source-root>")
    raise SystemExit(main())
