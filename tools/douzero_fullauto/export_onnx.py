#!/usr/bin/env python3
"""Export DouZero_For_HLDDZ_FullAuto ResNet and pre-game models to ONNX."""

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
import torch.nn.functional as functional
from torch import nn


SOURCE_REPOSITORY = "https://github.com/Vincentzyx/DouZero_For_HLDDZ_FullAuto"
FEATURE_SCHEMA = "douzero-fullauto-resnet-v1"
PLAY_ROLES = ("landlord", "landlord_up", "landlord_down")
PRE_GAME_MODELS = {
    "bid": "bid_weights.pkl",
    "farmer_up": "landlord_up_weights.pkl",
    "farmer_down": "landlord_down_weights.pkl",
}


class PlayExportModel(nn.Module):
    def __init__(self, model: nn.Module):
        super().__init__()
        self.model = model

    def forward(self, z: torch.Tensor, x: torch.Tensor) -> torch.Tensor:
        return self.model(z, x, return_value=True)["values"]


class PreGameScoreModel(nn.Module):
    def __init__(self):
        super().__init__()
        self.conv1 = nn.Conv1d(1, 16, kernel_size=3, padding=1)
        self.dense1 = nn.Linear(1020, 1024)
        self.dense2 = nn.Linear(1024, 512)
        self.dense3 = nn.Linear(512, 256)
        self.dense4 = nn.Linear(256, 128)
        self.dense5 = nn.Linear(128, 1)

    def forward(self, cards: torch.Tensor) -> torch.Tensor:
        value = functional.leaky_relu(self.conv1(cards.unsqueeze(1)))
        value = torch.cat((value.flatten(1, 2), cards), 1)
        value = functional.leaky_relu(self.dense1(value))
        value = functional.leaky_relu(self.dense2(value))
        value = functional.leaky_relu(self.dense3(value))
        value = functional.leaky_relu(self.dense4(value))
        return self.dense5(value)


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
    spec = importlib.util.spec_from_file_location("douzero_fullauto_models", module_path)
    if spec is None or spec.loader is None:
        raise RuntimeError(f"Unable to load {module_path}")
    module = importlib.util.module_from_spec(spec)
    spec.loader.exec_module(module)
    return module


def add_metadata(
    model: onnx.ModelProto,
    name: str,
    commit: str,
    checkpoint_hash: str,
    kind: str,
) -> None:
    del model.metadata_props[:]
    for key, value in {
        "source_repository": SOURCE_REPOSITORY,
        "source_commit": commit,
        "checkpoint_sha256": checkpoint_hash,
        "objective": "FullAuto ResNet ADP",
        "model_name": name,
        "model_kind": kind,
        "feature_schema": FEATURE_SCHEMA,
    }.items():
        item = model.metadata_props.add()
        item.key = key
        item.value = value
    model.producer_name = "happy4j-douzero-fullauto-export"


def finish_export(
    output: Path,
    name: str,
    commit: str,
    checkpoint_hash: str,
    kind: str,
) -> None:
    exported = onnx.load(output)
    add_metadata(exported, name, commit, checkpoint_hash, kind)
    onnx.checker.check_model(exported)
    onnx.save(exported, output)


def export_play_model(module, source: Path, output: Path, role: str, commit: str) -> dict:
    checkpoint = source / "baselines" / "resnet" / f"resnet_{role}.ckpt"
    checkpoint_hash = sha256(checkpoint)
    model = module.model_dict_resnet[role]()
    model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True), strict=True)
    wrapped = PlayExportModel(model.eval()).eval()
    rng = np.random.default_rng(20260829)
    export_z = rng.normal(size=(1, 40, 54)).astype(np.float32)
    export_x = rng.normal(size=(1, 15)).astype(np.float32)
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
    finish_export(output, role, commit, checkpoint_hash, "play")
    session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
    max_error = 0.0
    for batch in (1, 2, 17):
        z = rng.normal(size=(batch, 40, 54)).astype(np.float32)
        x = rng.normal(size=(batch, 15)).astype(np.float32)
        with torch.no_grad():
            expected = wrapped(torch.from_numpy(z), torch.from_numpy(x)).numpy()
        actual = session.run(["values"], {"z": z, "x": x})[0]
        max_error = max(max_error, float(np.max(np.abs(expected - actual))))
    if max_error > 1e-4:
        raise RuntimeError(f"{role} numerical error is too high: {max_error}")
    return model_entry(output, role, checkpoint_hash, max_error, {"z": ["batch", 40, 54], "x": ["batch", 15]})


def export_pre_game_model(source: Path, output: Path, name: str, weight_file: str, commit: str) -> dict:
    checkpoint = source / "weights" / weight_file
    checkpoint_hash = sha256(checkpoint)
    model = PreGameScoreModel()
    model.load_state_dict(torch.load(checkpoint, map_location="cpu", weights_only=True), strict=True)
    model.eval()
    rng = np.random.default_rng(20260830)
    export_cards = rng.integers(0, 2, size=(1, 60)).astype(np.float32)
    torch.onnx.export(
        model,
        torch.from_numpy(export_cards),
        output,
        input_names=["cards"],
        output_names=["score"],
        dynamic_axes={"cards": {0: "batch"}, "score": {0: "batch"}},
        opset_version=14,
        do_constant_folding=True,
        dynamo=False,
    )
    finish_export(output, name, commit, checkpoint_hash, "pre-game")
    session = ort.InferenceSession(str(output), providers=["CPUExecutionProvider"])
    max_error = 0.0
    for batch in (1, 8):
        cards = rng.integers(0, 2, size=(batch, 60)).astype(np.float32)
        with torch.no_grad():
            expected = model(torch.from_numpy(cards)).numpy()
        actual = session.run(["score"], {"cards": cards})[0]
        max_error = max(max_error, float(np.max(np.abs(expected - actual))))
    if max_error > 1e-4:
        raise RuntimeError(f"{name} numerical error is too high: {max_error}")
    return model_entry(output, name, checkpoint_hash, max_error, {"cards": ["batch", 60]}, "score")


def model_entry(
    output: Path,
    name: str,
    checkpoint_hash: str,
    max_error: float,
    inputs: dict,
    output_name: str = "values",
) -> dict:
    return {
        "file": output.name,
        "name": name,
        "checkpointSha256": checkpoint_hash,
        "onnxSha256": sha256(output),
        "size": output.stat().st_size,
        "inputs": inputs,
        "output": {output_name: ["batch", 1]},
        "maxAbsError": max_error,
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument("--source", required=True, type=Path)
    parser.add_argument("--output", required=True, type=Path)
    args = parser.parse_args()
    source = args.source.resolve()
    output = args.output.resolve()
    output.mkdir(parents=True, exist_ok=True)
    commit = source_commit(source)
    module = load_model_module(source)
    models = [
        export_play_model(module, source, output / f"{role}.onnx", role, commit)
        for role in PLAY_ROLES
    ]
    models.extend(
        export_pre_game_model(source, output / f"{name}.onnx", name, weight_file, commit)
        for name, weight_file in PRE_GAME_MODELS.items()
    )
    manifest = {
        "name": "DouZero For HLDDZ FullAuto ResNet",
        "objective": "ADP",
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
