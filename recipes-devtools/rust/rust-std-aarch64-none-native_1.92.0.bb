# Rust standard library for aarch64-unknown-none-softfloat (built from source)
require rust-target.inc
require rust-source_${PV}.inc

# Clear PROVIDES inherited from rust-target.inc (only rust_1.92.0.bb should provide virtual/rust-native)
PROVIDES:class-native = ""

# LICENSE is in rustc root, S points to library/std
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://../../COPYRIGHT;md5=11a3899825f4376896e438c8c753f8dc"

inherit native
DEPENDS = "rust-native"

# libstd moved from src/libstd to library/std in 1.47+
# For bare-metal (target_os="none"), build core+alloc, not std
S = "${RUSTSRC}/library/core"

# Remove snapshot task
deltask do_rust_setup_snapshot

INSANE_SKIP:${PN}:class-native = "already-stripped"

# Use rust-native's tools directly (avoid cargo-native conflict)
export CARGO = "${COMPONENTS_DIR}/${BUILD_ARCH}/rust-native/usr/bin/cargo"
export RUSTC = "${COMPONENTS_DIR}/${BUILD_ARCH}/rust-native/usr/bin/rustc"

do_compile() {
    export PATH="${COMPONENTS_DIR}/${BUILD_ARCH}/rust-native/usr/bin:$PATH"
    export RUSTC_BOOTSTRAP="1"
    export CARGO_TARGET_DIR="${B}"
    
    # Build core (cargo will auto-build alloc and compiler_builtins as dependencies)
    cd ${RUSTSRC}/library/core
    ${CARGO} build --target aarch64-unknown-none-softfloat --release
}

# Override install for bare-metal target
do_install() {
    install -d ${D}${prefix}/lib/rustlib/aarch64-unknown-none-softfloat/lib
    
    # All libraries built when compiling core (cargo auto-builds dependencies)
    # core, alloc, compiler_builtins .rlib files are all in core's output
    cp ${RUSTSRC}/library/core/aarch64-unknown-none-softfloat/release/deps/*.rlib \
       ${D}${prefix}/lib/rustlib/aarch64-unknown-none-softfloat/lib/
}

python () {
    pn = d.getVar('PN')
    if not pn.endswith("-native"):
        raise bb.parse.SkipRecipe("Rust recipe doesn't work for target builds at this time. Fixes welcome.")
}
