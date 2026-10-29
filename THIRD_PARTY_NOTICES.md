# Third-party notices

## DouZero For HappyDouDiZhu

- Source: https://github.com/tianqiraf/DouZero_For_HappyDouDiZhu
- Imported model family: `baselines/douzero_WP`
- License: Apache License 2.0 (`third_party/DouZero_For_HappyDouDiZhu-LICENSE.txt`)

The reproducible legacy WP exporter under `tools/douzero_happy/` targets this
project's three checkpoints. These older model binaries are not packaged by the
current application.

## DouZero For HLDDZ FullAuto

- Source: https://github.com/Vincentzyx/DouZero_For_HLDDZ_FullAuto
- Imported models: `baselines/resnet` and the bidding/farmer pre-game score weights
- License: Apache License 2.0 (`third_party/DouZero_For_HLDDZ_FullAuto-LICENSE.txt`)

The FullAuto ResNet ADP browser models under
`frontend/public/models/douzero_fullauto/` are the primary local inference
engine. Their exact source commit, checkpoint hashes, tensor contracts, and
ONNX hashes are recorded in the adjacent `manifest.json`.

## PerfectDou

- Source: https://github.com/Netease-Games-AI-Lab-Guangzhou/PerfectDou
- Imported models: `frontend/public/models/perfectdou/*.onnx`
- License: Apache License 2.0 (`third_party/PerfectDou-LICENSE.txt`)

PerfectDou models are loaded directly through Java ONNX Runtime on the server.
