
# StarryOS-specific Cargo build environment glue.
#
# Keeps StarryOS recipes small by centralizing:
# - offline Cargo config pattern
# - tool aliases required by vendored C dependencies (e.g. lwext4_rust)
#
# This class intentionally does NOT change the toolchain policy (which is
# selected via PREFERRED_VERSION in configuration).

inherit cargo_bin

STARRY_TOOLS_BINDIR ?= "${B}/.tools/bin"

DEPENDS:append:aarch64 = " musl-cross-aarch64-native"

starry_setup_host_cc_shims() {
    install -d "${STARRY_TOOLS_BINDIR}"

    # Ensure we don't accidentally shadow the real musl toolchain binaries with
    # stale wrappers from previous builds in the external build dir.
    rm -f "${STARRY_TOOLS_BINDIR}"/*-linux-musl-* 2>/dev/null || true

    # Some Rust build scripts (build.rs) assume `cc` exists on PATH.
    cat > "${STARRY_TOOLS_BINDIR}/cc" <<EOF
#!/bin/sh
exec ${BUILD_CC} "\$@"
EOF
    chmod 0755 "${STARRY_TOOLS_BINDIR}/cc"

    cat > "${STARRY_TOOLS_BINDIR}/c++" <<EOF
#!/bin/sh
exec ${BUILD_CXX} "\$@"
EOF
    chmod 0755 "${STARRY_TOOLS_BINDIR}/c++"
}

do_compile:prepend() {
    starry_setup_host_cc_shims
    export PATH="${STARRY_TOOLS_BINDIR}:${PATH}"
}
