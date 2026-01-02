# Rust standard library for aarch64-unknown-none-softfloat (built from source)
require rust-target.inc
require rust-source_${PV}.inc

# Clear PROVIDES inherited from rust-target.inc (only rust_1.92.0.bb should provide virtual/rust-native)
PROVIDES:class-native = ""

# LICENSE is in rustc root
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${RUSTSRC}/COPYRIGHT;md5=11a3899825f4376896e438c8c753f8dc"

inherit native
DEPENDS = "rust-native"

S = "${RUSTSRC}/library"

# Remove snapshot task
deltask do_rust_setup_snapshot

INSANE_SKIP:${PN}:class-native = "already-stripped"

# Use rust-native's tools directly
export RUSTC = "${COMPONENTS_DIR}/${BUILD_ARCH}/rust-native/usr/bin/rustc"
TARGET_TRIPLE = "aarch64-unknown-none-softfloat"

do_compile() {
    export PATH="${COMPONENTS_DIR}/${BUILD_ARCH}/rust-native/usr/bin:$PATH"
    export RUSTC_BOOTSTRAP="1"
    
    cd ${S}
    BUILD_DIR="${B}/build"
    mkdir -p ${BUILD_DIR}
    
    # Build order: core -> compiler_builtins -> alloc
    # core uses #![no_core] so has no dependencies
    # compiler_builtins uses #![no_std] so needs core
    # alloc uses #![no_std] and needs core + compiler_builtins
    
    # Step 1: Build core (uses #![no_core], no dependencies)
    bbnote "Building core..."
    ${RUSTC} \
        --crate-name core \
        --crate-type rlib \
        --edition 2024 \
        --target ${TARGET_TRIPLE} \
        --out-dir ${BUILD_DIR} \
        -O \
        ${S}/core/src/lib.rs
    
    # Step 2: Build compiler_builtins (uses #![no_std], needs core)
    bbnote "Building compiler_builtins..."
    ${RUSTC} \
        --crate-name compiler_builtins \
        --crate-type rlib \
        --edition 2024 \
        --target ${TARGET_TRIPLE} \
        --out-dir ${BUILD_DIR} \
        -O \
        -L ${BUILD_DIR} \
        --extern core=${BUILD_DIR}/libcore.rlib \
        --cfg 'feature="compiler-builtins"' \
        --cfg 'feature="mem"' \
        ${RUSTSRC}/library/compiler-builtins/compiler-builtins/src/lib.rs
    
    # Step 3: Build alloc (uses #![no_std], needs core + compiler_builtins)
    bbnote "Building alloc..."
    ${RUSTC} \
        --crate-name alloc \
        --crate-type rlib \
        --edition 2024 \
        --target ${TARGET_TRIPLE} \
        --out-dir ${BUILD_DIR} \
        -O \
        -L ${BUILD_DIR} \
        --extern core=${BUILD_DIR}/libcore.rlib \
        --extern compiler_builtins=${BUILD_DIR}/libcompiler_builtins.rlib \
        ${S}/alloc/src/lib.rs
}

do_install() {
    install -d ${D}${prefix}/lib/rustlib/${TARGET_TRIPLE}/lib
    
    # Copy all built .rlib files
    cp ${B}/build/*.rlib ${D}${prefix}/lib/rustlib/${TARGET_TRIPLE}/lib/
}

python () {
    pn = d.getVar('PN')
    if not pn.endswith("-native"):
        raise bb.parse.SkipRecipe("Rust recipe doesn't work for target builds at this time. Fixes welcome.")
}
