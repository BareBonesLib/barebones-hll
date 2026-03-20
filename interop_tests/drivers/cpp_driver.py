"""
Python wrapper that invokes the C++ CLI driver via subprocess.
"""

import json
import subprocess
from pathlib import Path
from typing import List

from .base_driver import DeserializeResult, MergeResult, SerializeResult

_DRIVERS_DIR = Path(__file__).parent
_REPO_ROOT    = _DRIVERS_DIR.parent.parent
_BIN          = _REPO_ROOT / "cpp" / "build" / "hll_cli"


def _invoke(payload: dict) -> dict:
    if not _BIN.exists():
        raise FileNotFoundError(
            f"C++ CLI binary not found at {_BIN}\n"
            f"Build it with:  cd cpp && make cli"
        )
    result = subprocess.run(
        [str(_BIN)],
        input=json.dumps(payload) + "\n",
        capture_output=True,
        text=True,
        timeout=30,
    )

    # print("C++ Driver")
    # print("result.returncode", result.returncode)
    # print("result.stdin", payload)
    # print("result.stdout", result.stdout)
    # print("result.stderr", result.stderr)

    if result.returncode != 0:
        raise RuntimeError(
            f"C++ CLI failed (exit {result.returncode})\n"
            f"  stdin:  {json.dumps(payload)}\n"
            f"  stderr: {result.stderr.strip()}"
        )
    try:
        return json.loads(result.stdout)
    except json.JSONDecodeError as e:
        raise RuntimeError(
            f"C++ CLI returned invalid JSON\n"
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
