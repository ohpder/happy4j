# DouZero For HappyDouDiZhu ONNX export

This exporter converts the three `douzero_WP` checkpoints from
[`tianqiraf/DouZero_For_HappyDouDiZhu`](https://github.com/tianqiraf/DouZero_For_HappyDouDiZhu)
into dynamic-batch ONNX models used by `onnxruntime-web`.

```bash
python -m pip install -r tools/douzero_happy/requirements.txt
python tools/douzero_happy/export_onnx.py \
  --source /path/to/DouZero_For_HappyDouDiZhu \
  --output frontend/public/models/douzero_happy
```

The generated `manifest.json` records the upstream commit, checkpoint hashes,
ONNX hashes, tensor contracts, and PyTorch/ONNX numerical error.
