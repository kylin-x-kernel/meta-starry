# Force std library features for baremetal; avoid inheriting machine-level CARGO_FEATURES
CARGO_FEATURES = "panic-unwind backtrace"
