# rust-kernel.bbclass
# Generic build class for bare-metal Rust kernels
#
# Provides:
#   - Rust toolchain setup (rust-native + rust-std-{arch}-none-native)
#   - Cargo environment configuration
#   - Common build/install tasks for bare-metal kernels
#
# Usage:
#   inherit rust-kernel
#
# Required variables:
#   RUST_TARGET - Target triple (e.g., aarch64-unknown-none-softfloat)
#   KERNEL_ARCH - Kernel architecture (e.g., aarch64, riscv64, loongarch64, x86_64)
#
# Optional variables:
#   CARGO_FEATURES - Cargo features to enable
#   EXTRA_CARGO_FLAGS - Additional cargo flags

# ==================== Dependencies ====================
# Rust toolchain: rustc + cargo + target std library
DEPENDS:append = " rust-native"

# Disable automatic "make clean" in base_do_configure
# The ArceOS Makefile's "clean" target runs "cargo clean" which fails
# because Yocto's HOST_SYS (x86_64-linux) is not a valid Rust target
CLEANBROKEN = "1"

# Dynamically add rust-std for target architecture
python __anonymous() {
    arch = d.getVar('KERNEL_ARCH')
    if not arch:
        arch = d.getVar('TARGET_ARCH')
    
    if arch:
        # Bare-metal std library: rust-std-{arch}-none-native
        arch_map = {
            'aarch64': 'rust-std-aarch64-none-native',
            'riscv64': 'rust-std-riscv64-none-native',
            'loongarch64': 'rust-std-loongarch64-none-native',
            'x86_64': 'rust-std-x86_64-none-native',
        }
        
        rust_std = arch_map.get(arch)
        if rust_std:
            d.appendVar('DEPENDS', f' {rust_std}')
        else:
            bb.warn(f"No rust-std package defined for architecture: {arch}")
}

# ==================== Packaging ====================
# Bare-metal kernels don't need standard packaging
PACKAGES = ""
RDEPENDS:${PN} = ""
ALLOW_EMPTY:${PN} = "1"

deltask package
deltask package_write_rpm
deltask package_write_ipk
deltask package_write_deb
deltask packagedata
deltask package_qa

# ==================== Build Configuration ====================
# Export Rust target for cargo
export CARGO_BUILD_TARGET = "${RUST_TARGET}"
export RUST_TARGET_TRIPLE = "${RUST_TARGET}"

# Setup Rust toolchain environment
rust_kernel_setup_toolchain() {
    # rust-native provides rustc, cargo, and other tools
    export RUST_NATIVE="${COMPONENTS_DIR}/${BUILD_ARCH}/rust-native"
    export PATH="${RUST_NATIVE}/usr/bin:$PATH"
    
    # Allow stable toolchain to use nightly features (required for bare-metal)
    export RUSTC_BOOTSTRAP="1"
    
    # Point to Rust sysroot
    export RUSTC_SYSROOT="${RUST_NATIVE}/usr"
    
    # CRITICAL: Yocto's rust-native was built with host=x86_64-linux
    # But x86_64-linux is not a standard Rust target.
    # We need to create a target spec JSON file that maps x86_64-linux to x86_64-unknown-linux-gnu
    local target_spec_dir="${WORKDIR}/rust-target-specs"
    mkdir -p "${target_spec_dir}"
    
    if [ ! -f "${target_spec_dir}/x86_64-linux.json" ]; then
        # Create x86_64-linux target spec based on x86_64-unknown-linux-gnu
        echo '{' > "${target_spec_dir}/x86_64-linux.json"
        echo '    "arch": "x86_64",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "cpu": "x86-64",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "crt-static-respected": true,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "data-layout": "e-m:e-p270:32:32-p271:32:32-p272:64:64-i64:64-i128:128-f80:128-n8:16:32:64-S128",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "dynamic-linking": true,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "env": "gnu",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "has-rpath": true,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "has-thread-local": true,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "linker-flavor": "gnu-cc",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "llvm-target": "x86_64-unknown-linux-gnu",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "max-atomic-width": 64,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "os": "linux",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "plt-by-default": false,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "position-independent-executables": true,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "pre-link-args": { "gnu-cc": ["-m64"] },' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "relro-level": "full",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "static-position-independent-executables": true,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "target-family": ["unix"],' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "target-mcount": "_mcount",' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "target-pointer-width": 64,' >> "${target_spec_dir}/x86_64-linux.json"
        echo '    "vendor": "unknown"' >> "${target_spec_dir}/x86_64-linux.json"
        echo '}' >> "${target_spec_dir}/x86_64-linux.json"
        bbnote "Created x86_64-linux target spec at ${target_spec_dir}/x86_64-linux.json"
    fi
    
    # Tell rustc where to find custom target specs
    export RUST_TARGET_PATH="${target_spec_dir}"
    
    # Verify toolchain availability
    if [ ! -f "${RUST_NATIVE}/usr/bin/cargo" ]; then
        bbfatal "cargo not found in rust-native"
    fi
    
    if [ ! -f "${RUST_NATIVE}/usr/bin/rustc" ]; then
        bbfatal "rustc not found in rust-native"
    fi
    
    # Link target std library into rust-native sysroot (if not already linked)
    local target_rustlib="${RUST_NATIVE}/usr/lib/rustlib/${RUST_TARGET}"
    local kernel_arch="${KERNEL_ARCH:-${TARGET_ARCH}}"
    local std_src="${COMPONENTS_DIR}/${BUILD_ARCH}/rust-std-${kernel_arch}-none-native/usr/lib/rustlib/${RUST_TARGET}"
    
    if [ ! -d "${target_rustlib}" ]; then
        if [ ! -d "${std_src}" ]; then
            bbfatal "Target ${RUST_TARGET} std library not found at ${std_src}"
        fi
        
        bbnote "Linking ${RUST_TARGET} std library into rust-native sysroot"
        ln -sf "${std_src}" "${target_rustlib}"
    fi
    
    # Verify std library (check both naming conventions: libcore.rlib and libcore-*.rlib)
    if [ ! -f "${target_rustlib}/lib/libcore.rlib" ] && ! ls "${target_rustlib}/lib/libcore-"*.rlib >/dev/null 2>&1; then
        bbfatal "libcore not found in ${target_rustlib}/lib/"
    fi
    
    bbnote "Rust toolchain configured:"
    bbnote "  rustc: $(rustc --version)"
    bbnote "  cargo: $(cargo --version)"
    bbnote "  sysroot: ${RUSTC_SYSROOT}"
    bbnote "  target: ${RUST_TARGET}"
    bbnote "  target libs: ${target_rustlib}/lib/"
}

# Setup cargo configuration
rust_kernel_setup_cargo() {
    mkdir -p "${S}/.cargo"
    
    # Create base cargo config for bare-metal target
    cat > "${S}/.cargo/config.toml" <<EOF
# Auto-generated by rust-kernel.bbclass

[target.x86_64-unknown-linux-gnu]
# Build scripts run on host
linker = "gcc"

[target.x86_64-linux]
# Yocto's rust-native uses x86_64-linux as host target
linker = "gcc"

[target.${RUST_TARGET}]
# Bare-metal target - no external linker
rustflags = [
    "-C", "link-arg=-nostartfiles",
    "-C", "link-arg=-no-pie",
]

[build]
# Note: Do NOT set default target here, it breaks build scripts
# Use --target on command line instead

# Recipes can append additional config via do_configure:append
EOF

    bbnote "Created .cargo/config.toml for ${RUST_TARGET}"
}

# ==================== Build Tasks ====================
do_configure:prepend() {
    rust_kernel_setup_toolchain
    rust_kernel_setup_cargo
}

do_compile() {
    rust_kernel_setup_toolchain
    
    export CARGO_BUILD_TARGET="${RUST_TARGET}"
    export RUST_BACKTRACE=1
    
    # Ensure build scripts can find host tools (cc, gcc)
    # Use system's native toolchain for build scripts (proc-macro crates)
    # HOST_PREFIX may be empty for native builds
    if [ -z "${BUILD_CC}" ]; then
        export CC="gcc"
        export CXX="g++"
        export AR="ar"
    else
        export CC="${BUILD_CC}"
        export CXX="${BUILD_CXX}"
        export AR="${BUILD_AR}"
    fi
    
    bbnote "Building Rust kernel for ${RUST_TARGET}"
    bbnote "Cargo manifest: ${S}/Cargo.toml"
    
    # Build with cargo
    cargo build \
        --manifest-path "${S}/Cargo.toml" \
        --target "${RUST_TARGET}" \
        --release \
        ${CARGO_FEATURES:+--features "${CARGO_FEATURES}"} \
        ${EXTRA_CARGO_FLAGS}
    
    bbnote "Build completed successfully"
}

do_install() {
    bbwarn "rust-kernel.bbclass: No do_install defined, recipes should override this"
    
    install -d ${D}/boot
    
    # Try to find the kernel binary (common naming patterns)
    local kernel_bin=""
    for name in ${PN} kernel main; do
        if [ -f "${B}/target/${RUST_TARGET}/release/${name}" ]; then
            kernel_bin="${B}/target/${RUST_TARGET}/release/${name}"
            break
        fi
    done
    
    if [ -n "${kernel_bin}" ]; then
        install -m 0755 "${kernel_bin}" "${D}/boot/${PN}.elf"
        bbnote "Installed kernel binary: ${D}/boot/${PN}.elf"
    else
        bbwarn "Kernel binary not found in ${B}/target/${RUST_TARGET}/release/"
    fi
}

# ==================== Helpers ====================
# Allow network access during build (for downloading crates)
do_compile[network] = "1"
do_configure[network] = "1"
