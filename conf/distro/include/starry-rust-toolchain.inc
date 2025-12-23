
# StarryOS Rust toolchain pinning (keep StarryOS/rust-toolchain.toml in sync)
#
# This file is intentionally configuration-only (not in recipes) to keep
# StarryOS build recipes decoupled from toolchain policy.

PREFERRED_VERSION_rust-bin-cross-aarch64 ?= "nightly-2025-12-12"
PREFERRED_VERSION_cargo-bin-cross-aarch64 ?= "nightly-2025-12-12"

