"""
Python wrapper that invokes the Java CLI driver via subprocess.
"""

import base64
import json
import subprocess
from pathlib import Path
from typing import List

from .base_driver import DeserializeResult, MergeResult, SerializeResult

# Path to the compiled Java CLI jar — resolved relative to this file
_DRIVERS_DIR = Path(__file__).parent
_REPO_ROOT    = _DRIVERS_DIR.parent.parent
_JAR          = _REPO_ROOT / "java" / "target" / "bareboneshll-1.0-SNAPSHOT-with-deps.jar"
_CLI_CLASS    = "io.github.bareboneslib.bareboneshll.HLLCli"


def _invoke(payload: dict) -> dict:
    if not _JAR.exists():
        raise FileNotFoundError(
            f"Java jar not found at {_JAR}\n"
            f"Build it with:  cd java && mvn package -q"
        )

    result = subprocess.run(
        ["java", "-cp", str(_JAR), _CLI_CLASS],
        input=json.dumps(payload),
        capture_output=True,
        text=True,
        timeout=30,
    )

    # print("Java Driver")
    # print("result.returncode", result.returncode)
    # print("result.stdin", payload)
    # print("result.stdout", result.stdout)
    # print("result.stderr", result.stderr)
    
    if result.returncode != 0:
        raise RuntimeError(
            f"Java CLI failed (exit {result.returncode})\n"
            f"  stdin:  {json.dumps(payload)}\n"
            f"  stderr: {result.stderr.strip()}"
        )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as e:
        raise RuntimeError(
            f"Java CLI returned invalid JSON\n"
            f"  stdout: {result.stdout!r}\n"
            f"  error:  {e}"
        )


def serialize(p: int, r: int, values: List[int]) -> SerializeResult:
    out = _invoke({"op": "serialize", "p": p, "r": r, "values": values})
    return SerializeResult(bytes_b64=out["bytes"], estimate=out["estimate"])


def deserialize(bytes_b64: str) -> DeserializeResult:
    out = _invoke({"op": "deserialize", "bytes": bytes_b64})
    return DeserializeResult(estimate=out["estimate"])


def merge(sketches_b64: List[str]) -> MergeResult:
    out = _invoke({"op": "merge", "sketches": sketches_b64})
    return MergeResult(bytes_b64=out["bytes"], estimate=out["estimate"])
