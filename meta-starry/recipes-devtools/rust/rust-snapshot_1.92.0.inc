## This is information on the rust-snapshot (binary) used to build our current release.
## snapshot info is taken from rust/src/stage0
## Rust is self-hosting and bootstraps itself with a pre-built previous version of itself.
## The exact (previous) version that has been used is specified in the source tarball.
## The version is replicated here.
## TODO: find a way to add additional SRC_URIs based on the contents of an
##       earlier SRC_URI.

SNAPSHOT_VERSION = "1.91.0"
SNAPSHOT_DATE = "2025-10-30"

# TODO: Add hashes for other architecture toolchains as well.

# You can use scripts/rust-get-stage0.sh to update hashes
# Hashes for rust 1.91.0 (bootstrap compiler for 1.92.0)
SRC_URI[rust-std-snapshot-x86_64.sha256sum] = "89e6520b16c12b43526440298d2da0dcb70747c5cc2d0b8e47d39b5da9aeef49"
SRC_URI[rustc-snapshot-x86_64.sha256sum] = "a7169e8cb6174af2f45717703370363d8de82ce55f6ccba185893045b9370874"
SRC_URI[cargo-snapshot-x86_64.sha256sum] = "7103c03fb8abe85b23307005a9dfe4f01c826a89945d84b96fa2d03fd4d2d138"

SRC_URI[rust-std-snapshot-aarch64.sha256sum] = "ff23dc81f796d64e34e866a44fd0bcae726e34014835369b8f9393a544167eca"
SRC_URI[rustc-snapshot-aarch64.sha256sum] = "f3ea3c964b7f3b884337f2d411764032bbd1722d7f55592a547cbb29afd87c03"
SRC_URI[cargo-snapshot-aarch64.sha256sum] = "003d7008219ca0d225ad1dfa301f7c079b123499430ee0780c85782e0878eeff"


SRC_URI += " \
    https://static.rust-lang.org/dist/${SNAPSHOT_DATE}/${RUST_STD_SNAPSHOT}.tar.xz;name=rust-std-snapshot-${RUST_BUILD_ARCH};subdir=rust-snapshot-components \
    https://static.rust-lang.org/dist/${SNAPSHOT_DATE}/${RUSTC_SNAPSHOT}.tar.xz;name=rustc-snapshot-${RUST_BUILD_ARCH};subdir=rust-snapshot-components \
    https://static.rust-lang.org/dist/${SNAPSHOT_DATE}/${CARGO_SNAPSHOT}.tar.xz;name=cargo-snapshot-${RUST_BUILD_ARCH};subdir=rust-snapshot-components \
"

RUST_STD_SNAPSHOT = "rust-std-${SNAPSHOT_VERSION}-${RUST_BUILD_ARCH}-unknown-linux-gnu"
RUSTC_SNAPSHOT = "rustc-${SNAPSHOT_VERSION}-${RUST_BUILD_ARCH}-unknown-linux-gnu"
CARGO_SNAPSHOT = "cargo-${SNAPSHOT_VERSION}-${RUST_BUILD_ARCH}-unknown-linux-gnu"
