"""
Shared types and protocol definition for HLL interop CLI drivers.

CLI protocol (stdin → stdout, one JSON object per invocation):

  serialize:
    in:  {"op": "serialize", "p": int, "r": int, "values": [int, ...]}
    out: {"bytes": "<base64>", "estimate": int}

  deserialize:
    in:  {"op": "deserialize", "bytes": "<base64>"}
    out: {"estimate": int}

  merge:
    in:  {"op": "merge", "sketches": ["<base64>", "<base64>", ...]}
    out: {"bytes": "<base64>", "estimate": int}
"""

from dataclasses import dataclass


@dataclass
class SerializeResult:
    bytes_b64: str      # base64-encoded serialized sketch
    estimate:  int


@dataclass
class DeserializeResult:
    estimate: int


@dataclass
class MergeResult:
    bytes_b64: str
    estimate:  int
