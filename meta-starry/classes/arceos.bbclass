# ArceOS build system class
# Provides ArceOS-specific build logic (platform config, features, etc.)
#
# Inherits rust-kernel.bbclass for common Rust bare-metal kernel build
#
# Usage:
#   inherit arceos
#
# Required machine variables:
#   ARCEOS_ARCH - Architecture (from machine config)
#   ARCEOS_PLAT_PACKAGE - Platform package name (e.g., axplat-loongarch64-qemu-virt)
#   RUST_TARGET - Rust target triple (e.g., loongarch64-unknown-none-softfloat)
#
# Optional variables:
#   ARCEOS_FEATURES - ArceOS features to enable
#   ARCEOS_SMP - Number of CPUs (default: 1)
#   ARCEOS_LOG - Log level (default: warn)
#   ARCEOS_PLAT_CONFIG - Path to pre-generated platform config file

# Inherit common Rust kernel build infrastructure
inherit rust-kernel

# ArceOS-specific tool dependencies
DEPENDS:append = " \
    axconfig-gen-native \
    cargo-binutils-native \
    cmake-native \
"

# Export CARGO_FEATURES so it's available in shell functions
export CARGO_FEATURES

# CRITICAL: Export RUSTC_BOOTSTRAP at metadata level (not in shell function)
# This ensures the environment variable is properly passed to ALL child processes
# including cargo -> rustc chain. Shell-level 'export' inside do_compile()
# does NOT work because BitBake runs shell functions in isolated environments.
# Required because axnet uses #![feature(maybe_uninit_slice)] which is unstable.
export RUSTC_BOOTSTRAP = "1"

# Set KERNEL_ARCH for rust-kernel.bbclass
KERNEL_ARCH = "${ARCEOS_ARCH}"

# Architecture and platform configuration
ARCEOS_ARCH ?= ""
ARCEOS_PLAT_PACKAGE ?= ""

# Export environment variables for ArceOS build
export ARCEOS_ARCH

# ==================== ArceOS Configuration ====================

def arceos_arch_map(d):
    """Map Yocto TARGET_ARCH to ArceOS ARCH"""
    arch = d.getVar('TARGET_ARCH')
    arch_map = {
        'x86_64': 'x86_64',
        'aarch64': 'aarch64',
        'riscv64': 'riscv64',
        'loongarch64': 'loongarch64',
    }
    mapped = arch_map.get(arch, arch)
    if mapped == arch and arch not in arch_map:
        bb.warn(f"Unknown architecture '{arch}' for ArceOS, using as-is")
    return mapped

# Generate .axconfig.toml platform configuration file
arceos_generate_config() {
    local config_file="${S}/.axconfig.toml"
    
    if [ -n "${ARCEOS_PLAT_CONFIG}" ] && [ -f "${ARCEOS_PLAT_CONFIG}" ]; then
        # Use pre-defined configuration file
        bbnote "Using pre-defined platform config: ${ARCEOS_PLAT_CONFIG}"
        cp "${ARCEOS_PLAT_CONFIG}" "${config_file}"
        return
    fi
    
    # Check if this is StarryOS (has arceos submodule with configs)
    if [ -f "${S}/arceos/configs/defconfig.toml" ]; then
        bbnote "Detected StarryOS structure, generating config via axconfig-gen..."
        
        # StarryOS uses arceos/configs/defconfig.toml as base
        local starry_defconfig="${S}/arceos/configs/defconfig.toml"
        
        if [ ! -f "${starry_defconfig}" ]; then
            bbfatal "StarryOS defconfig not found: ${starry_defconfig}"
        fi
        
        # Generate .axconfig.toml using axconfig-gen (replacing make defconfig)
        # This replicates what arceos/scripts/make/config.mk does
        bbnote "Generating .axconfig.toml for StarryOS:"
        bbnote "  Base config: ${starry_defconfig}"
        bbnote "  Architecture: ${ARCEOS_ARCH}"
        bbnote "  Platform: ${ARCEOS_PLAT_PACKAGE}"
        
        # Find platform axconfig.toml
        local plat_config="${S}/local_crates/axplat_crates/platforms/${ARCEOS_PLAT_PACKAGE}/axconfig.toml"
        if [ ! -f "${plat_config}" ]; then
            bbfatal "Platform config not found: ${plat_config}"
        fi
        
        # Call axconfig-gen with defconfig + platform config (like Makefile does)
        bbnote "  Platform config: ${plat_config}"
        axconfig-gen \
            "${starry_defconfig}" \
            "${plat_config}" \
            -w "arch=\"${ARCEOS_ARCH}\"" \
            -w "platform=\"${ARCEOS_PLATFORM}\"" \
            -o "${config_file}" || \
            bbfatal "Failed to generate StarryOS platform config"
        
        # Override SMP if specified
        if [ -n "${ARCEOS_SMP}" ] && [ "${ARCEOS_SMP}" != "1" ]; then
            bbnote "Setting SMP to ${ARCEOS_SMP} CPUs"
            axconfig-gen "${config_file}" -w "plat.cpu-num=${ARCEOS_SMP}" -o "${config_file}" || \
                bbwarn "Failed to set SMP configuration"
        fi
        
        # Override LOG level if specified
        if [ -n "${ARCEOS_LOG}" ]; then
            bbnote "Setting log level to ${ARCEOS_LOG}"
            axconfig-gen "${config_file}" -w "log=\"${ARCEOS_LOG}\"" -o "${config_file}" || \
                bbwarn "Failed to set log level"
        fi
        
        # Override memory size if specified (in bytes)
        if [ -n "${ARCEOS_MEM}" ]; then
            bbnote "Setting memory size to ${ARCEOS_MEM}"
            axconfig-gen "${config_file}" -w "plat.phys-memory-size=${ARCEOS_MEM}" -o "${config_file}" || \
                bbwarn "Failed to set memory size"
        fi
        
        bbnote "Generated .axconfig.toml for StarryOS:"
        cat "${config_file}"
        return
    fi
    
    # If we reach here, neither pre-defined config nor StarryOS structure found
    bbfatal "Unable to generate config: not a StarryOS project and no pre-defined config provided"
}

# ==================== Build Tasks ====================

# Hook into rust-kernel.bbclass configure task to generate ArceOS config  
do_configure:append() {
    arceos_generate_config
}

# Override rust-kernel.bbclass compile to add ArceOS-specific logic
do_compile() {
    # Call rust-kernel setup
    rust_kernel_setup_toolchain
    
    export CARGO_BUILD_TARGET="${RUST_TARGET}"
    export AX_CONFIG_PATH="${S}/.axconfig.toml"
    export RUST_BACKTRACE=1
    
    # ==================== lwext4_rust musl toolchain setup ====================
    # lwext4_rust's build.rs expects aarch64-linux-musl-gcc toolchain
    # We create wrapper scripts that:
    # 1. Handle -print-sysroot to return our fake sysroot
    # 2. Forward compilation to the real cross compiler with bare-metal flags
    
    MUSL_WRAPPER_DIR="${WORKDIR}/musl-wrapper"
    MUSL_SYSROOT="${WORKDIR}/musl-sysroot"
    
    mkdir -p "${MUSL_WRAPPER_DIR}"
    mkdir -p "${MUSL_SYSROOT}/include"
    
    # Create minimal sysroot with essential headers for lwext4
    # Use self-contained headers from meta-starry (portable across hosts)
    if [ ! -f "${MUSL_SYSROOT}/include/stdint.h" ]; then
        bbnote "Creating minimal sysroot for lwext4_rust..."
        # Copy portable C headers bundled with meta-starry
        # LAYERDIR_meta-starry is set by bitbake
        MUSL_HEADERS_SRC="${LAYERDIR_meta-starry}/files/musl-headers"
        if [ -d "${MUSL_HEADERS_SRC}" ]; then
            cp -r "${MUSL_HEADERS_SRC}"/* "${MUSL_SYSROOT}/include/"
            bbnote "Copied musl headers from ${MUSL_HEADERS_SRC}"
        else
            bbfatal "musl-headers not found at ${MUSL_HEADERS_SRC}"
        fi
    fi
    
    # Create gcc wrapper script
    cat > "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc" << 'EOFGCC'
#!/bin/bash
# Musl gcc wrapper for lwext4_rust build
# Handles -print-sysroot and forwards other args to real compiler

SYSROOT="__MUSL_SYSROOT__"
REAL_CC="__REAL_CC__"

for arg in "$@"; do
    if [ "$arg" = "-print-sysroot" ]; then
        echo "$SYSROOT"
        exit 0
    fi
done

# Forward to real compiler with bare-metal compatible flags
# -ffreestanding: Don't assume standard library exists
# -nostdinc: Don't search standard include paths
# -isystem: Add our sysroot headers as system include path
exec $REAL_CC -ffreestanding -nostdinc -isystem "${SYSROOT}/include" "$@"
EOFGCC

    # Substitute placeholders
    sed -i "s|__MUSL_SYSROOT__|${MUSL_SYSROOT}|g" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    sed -i "s|__REAL_CC__|${CC}|g" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    chmod +x "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    
    # Create cc alias (lwext4 cmake uses -cc suffix)
    ln -sf "${TUNE_ARCH}-linux-musl-gcc" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-cc"
    
    # Create other tool wrappers as simple symlinks
    ln -sf "$(which ${AR})" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ar" 2>/dev/null || \
        ln -sf "$(which ar)" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ar"
    ln -sf "$(which ${AS})" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-as" 2>/dev/null || \
        ln -sf "$(which as)" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-as"
    ln -sf "$(which ${RANLIB})" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ranlib" 2>/dev/null || \
        ln -sf "$(which ranlib)" "${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-ranlib"
    
    bbnote "Created musl toolchain wrappers in ${MUSL_WRAPPER_DIR}"
    bbnote "  gcc wrapper: ${MUSL_WRAPPER_DIR}/${TUNE_ARCH}-linux-musl-gcc"
    bbnote "  sysroot: ${MUSL_SYSROOT}"
    
    export PATH="${MUSL_WRAPPER_DIR}:$PATH"
    export ARCH="${TUNE_ARCH}"
    
    # Note: RUSTC_BOOTSTRAP=1 is set in .cargo/config.toml by rust-kernel.bbclass
    # and also in the rustc-wrapper script for reliability
    
    # Export ArceOS features if specified
    if [ -n "${ARCEOS_FEATURES}" ]; then
        export FEATURES="${ARCEOS_FEATURES}"
        bbnote "ArceOS features: ${ARCEOS_FEATURES}"
    fi
    
    bbnote "Building ArceOS kernel for ${RUST_TARGET}"
    bbnote "Cargo manifest: ${S}/Cargo.toml"
    
    # Set linker script path - this is generated during configure
    # The linker script is created by axhal during the build based on .axconfig.toml
    LD_SCRIPT="${S}/target/${RUST_TARGET}/release/linker_${ARCEOS_PLATFORM}.lds"
    
    # Set RUSTFLAGS for linking (matching arceos Makefile)
    # -C link-arg=-T<script>: Use custom linker script
    # -C link-arg=-no-pie: Disable position-independent executable
    # -C link-arg=-znostart-stop-gc: Prevent garbage collection of start/stop symbols
    export RUSTFLAGS="-C link-arg=-T${LD_SCRIPT} -C link-arg=-no-pie -C link-arg=-znostart-stop-gc"
    
    # Add DWARF debug info flags if enabled
    if [ "${ARCEOS_DWARF}" = "y" ]; then
        export RUSTFLAGS="${RUSTFLAGS} -C force-frame-pointers -C debuginfo=2 -C strip=none"
    fi
    
    bbnote "RUSTFLAGS: ${RUSTFLAGS}"
    
    # Build using cargo -C to match make build behavior EXACTLY
    # This is critical - cargo -C behaves differently from --manifest-path
    # The make build uses: cargo -C <project_root> build -Z unstable-options ...
    # Using --manifest-path triggers different path resolution behavior
    
    # TARGET_DIR must match make's behavior: $(PWD)/target -> ${S}/target
    TARGET_DIR="${S}/target"
    
    bbnote "Running: cargo -C ${S} build -Z unstable-options --target ${RUST_TARGET} --target-dir ${TARGET_DIR} --release --features ${CARGO_FEATURES:-none}"
    
    # Note: RUSTC_BOOTSTRAP=1 is configured via:
    # 1. .cargo/config.toml [env] section (set by rust-kernel.bbclass)
    # 2. rustc-wrapper script (fallback)
    # No need to export here as cargo reads from config.toml
    
    # Debug: verify environment (use $VAR not ${VAR} to avoid BitBake expansion)
    bbnote "DEBUG: RUSTC_BOOTSTRAP=$RUSTC_BOOTSTRAP"
    bbnote "DEBUG: RUSTC=$RUSTC"
    bbnote "DEBUG: RUST_TARGET_PATH=$RUST_TARGET_PATH"
    bbnote "DEBUG: PATH (first 3 dirs)=$(echo $PATH | cut -d: -f1-3)"
    
    cargo -C "${S}" build \
        -Z unstable-options \
        --target "${RUST_TARGET}" \
        --target-dir "${TARGET_DIR}" \
        --release \
        ${CARGO_FEATURES:+--features "${CARGO_FEATURES}"} \
        ${EXTRA_CARGO_FLAGS}
}
