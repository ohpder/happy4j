# PerfectDou inference engine

PerfectDou is available as the `PERFECTDOU` inference engine on both the server
and browser. Select it in the AI control panel. The server and browser each load
the published ONNX models directly.

The published checkpoints cover card-play positions; calling, robbing, and
doubling continue to use the existing policy. Model files live under
`frontend/public/models/perfectdou/` and are mounted at
`HAPPY4J_PERFECTDOU_MODEL_ROOT` for the packaged server.
