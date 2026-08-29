#!/usr/bin/env python3
"""Export DouZero_For_HappyDouDiZhu WP checkpoints to browser-ready ONNX models."""

from __future__ import annotations

import argparse
import hashlib
import importlib.util
import json
import subprocess
from pathlib import Path

import numpy as np
import onnx
import onnxruntime as ort
import torch
from torch import nn


ROLES = {"landlord": 373, "landlord_up": 484, "landlord_down": 484}
SOURCE_REPOSITORY = "https://github.com/tianqiraf/DouZero_For_HappyDouDiZhu"
FEATURE_SCHEMA = "douzero-happy-v1"


class ExportModel(nn.Module):
    def __init__(self, model: nn.Module):
        super().__init__()
        self.model = model

    def forward(self, z: torch.Tensor, x: torch.Tensor) -> torch.Tensor:
        return self.model(z, x, return_value=True)["values"]


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for block in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(block)
    return digest.hexdigest()


def source_commit(source: Path) -> str:
    return subprocess.check_output(
        ["git", "-C", str(source), "rev-parse", "HEAD"], text=True
    ).strip()


def load_model_module(source: Path):
    module_path = source / "douzero" / "dmc" / "models.py"
    spec = importlib.util.spec_from_file_location("douzero_happy_models", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def add_metadata(model: onnx.ModelProto, role: str, commit: str, checkpoint_hash: str) -> None:
    del model.metadata_props[:]
    for key, value in {
        "source_repository": SOURCE_REPOSITORY,
        "source_commit": commit,
        "checkpoint_sha256": checkpoint_hash,
        "objective": "WP",
        "role": role,
        "feature_schema": FEATURE_SCHEMA,
    }.items():
        item = model.metadata_props.add()
        item.key = key
        item.value = value
    model.producer_name = "happy4j-douzero-happy-export"


def export_role(module, source: Path, output: Path, role: str, width: int, commit: str) -> dict:
    checkpoint = source / "baselines" / "douzero_WP" / f"{role}.ckpt"
    checkpoint_hash = sha256(checkpoint)
    state = torch.load(checkpoint, map_location="cpu", weights_only=True)
    model = module.model_dict[role]()
    model.load_state_dict(state, strict=True)
    model.eval()
    wrapped = ExportModel(model).eval()

    rng = np.random.default_rng(20260829 + width)
    export_z = rng.integers(0, 2, size=(1, 5, 162)).astype(np.float32)
    export_x = rng.integers(0, 2, size=(1, width)).astype(np.float32)
    output.parent.mkdir(parents=True, exist_ok=True)
    torch.onnx.export(
        wrapped,
        (torch.from_numpy(export_z), torch.from_numpy(export_x)),
        output,
        input_names=["z", "x"],
        output_names=["values"],
        dynamic_axes={"z": {0: "batch"}, "x": {0: "batch"}, "values": {0: "batch"}},
        opset_version=14,
        do_constant_folding=True,
        dynamo=False,
    )

    exported = onnx.load(output)
    add_metadata(exported, role, commit, checkpoint_hash)
    onnx.checker.check_model(exported)
    onnx.save(exported, output)

    session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
    max_error = 0.0
    for batch in (1, 2, 17):
        z = rng.integers(0, 2, size=(batch, 5, 162)).astype(np.float32)
        x = rng.integers(0, 2, size=(batch, width)).astype(np.float32)
        with torch.no_grad():
            expected = wrapped(torch.from_numpy(z), torch.from_numpy(x)).numpy()
        actual = session.run(["values"], {"z": z, "x": x})[0]
        max_error = max(max_error, float(np.max(np.abs(expected - actual))))
    if max_error > 1e-4:
        raise RuntimeError(f"{role} ONNX numerical error is too high: {max_error}")
    return {
        "file": output.name,
        "role": role,
        "checkpointSha256": checkpoint_hash,
        "onnxSha256": sha256(output),
        "size": output.stat().st_size,
        "inputs": {"z": ["batch", 5, 162], "x": ["batch", width]},
        "output": {"values": ["batch", 1]},
        "maxAbsError": max_error,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    commit = source_commit(source)
    module = load_model_module(source)
    models = [
        export_role(module, source, output / f"{role}.onnx", role, width, commit)
        for role, width in ROLES.items()
    ]
    manifest = {
        "name": "DouZero For HappyDouDiZhu WP",
        "objective": "WP",
        "featureSchema": FEATURE_SCHEMA,
        "sourceRepository": SOURCE_REPOSITORY,
        "sourceCommit": commit,
        "models": models,
    }
    (output / "manifest.json").write_text(
        json.dumps(manifest, ensure_ascii=False, indent=2) + "\n", encoding="utf-8"
    )
    print(json.dumps(manifest, ensure_ascii=False, indent=2))


if __name__ == "__main__":
    main()
