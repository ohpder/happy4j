# DouZero For HLDDZ FullAuto ONNX export

This exporter converts the FullAuto ResNet play models and the bidding/farmer
pre-game score models from
[`Vincentzyx/DouZero_For_HLDDZ_FullAuto`](https://github.com/Vincentzyx/DouZero_For_HLDDZ_FullAuto)
for browser-side `onnxruntime-web` inference.

```bash
python -m pip install -r tools/douzero_fullauto/requirements.txt
python tools/douzero_fullauto/export_onnx.py \
  --source /path/to/DouZero_For_HLDDZ_FullAuto \
  --output frontend/public/models/douzero_fullauto
```
