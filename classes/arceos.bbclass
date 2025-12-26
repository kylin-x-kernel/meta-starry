# ArceOS build system class
# Provides common build logic for ArceOS-based kernels
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

# Architecture and platform configuration
# These can be set in machine/*.conf files or recipes
# Using ?= (weak assignment) allows machine config to override defaults
ARCEOS_ARCH ?= ""
ARCEOS_PLAT_PACKAGE ?= ""
RUST_TARGET ?= ""

# Rust target triple
RUST_TARGET_TRIPLE = "${RUST_TARGET}"

# Tool dependencies for ArceOS bare-metal build
DEPENDS:append = " \
    axconfig-gen-native \
    cargo-binutils-native \
"

# We need Rust toolchain but not the standard cross-compiler
# For bare-metal, we rely on rustup/cargo to provide the target
DEPENDS:append = " cargo-bin-native rustc-bin-native"

# Remove standard dependencies (bare-metal kernel, no libc)
INHIBIT_DEFAULT_DEPS = "1"

# Export environment variables for build
export ARCEOS_ARCH
export RUST_TARGET_TRIPLE
export CARGO_BUILD_TARGET = "${RUST_TARGET_TRIPLE}"

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
        bbnote "Detected StarryOS structure, using make defconfig..."
        
        # Set environment variables for StarryOS Makefile
        export ARCH=${ARCEOS_ARCH}
        export A="${S}"
        export NO_AXSTD=y
        export AX_LIB=axfeat
        export RUSTC_BOOTSTRAP=1
        
        # Use StarryOS's own build system to generate config
        cd "${S}"
        make defconfig || bbfatal "make defconfig failed"
        
        if [ ! -f "${config_file}" ]; then
            bbfatal "make defconfig didn't generate ${config_file}"
        fi
        
        bbnote "Generated .axconfig.toml via StarryOS Makefile"
        return
    fi
    
    # Fallback: Generate configuration via axconfig-gen (for pure ArceOS projects)
    if [ -z "${ARCEOS_PLAT_PACKAGE}" ]; then
        bbfatal "ARCEOS_PLAT_PACKAGE is not set. Please define it in machine config."
    fi
    
    bbnote "Generating platform config with axconfig-gen..."
    bbnote "  Architecture: ${ARCEOS_ARCH}"
    bbnote "  Platform: ${ARCEOS_PLAT_PACKAGE}"
    
    # Use ArceOS default configs
    local defconfig="${S}/configs/defconfig.toml"
    local plat_config="${S}/platforms/${ARCEOS_PLAT_PACKAGE}/defconfig.toml"
    
    if [ ! -f "${defconfig}" ]; then
        bbfatal "Default config not found: ${defconfig}"
    fi
    
    # Generate config (similar to ArceOS Makefile)
    axconfig-gen \
        "${defconfig}" \
        ${plat_config:+-w "${plat_config}"} \
        -w "arch=\"${ARCEOS_ARCH}\"" \
        -w "platform=\"${ARCEOS_PLAT_PACKAGE}\"" \
        -o "${config_file}" || \
        bbfatal "Failed to generate platform config"
    
    # Override SMP configuration if specified
    if [ "${ARCEOS_SMP}" != "1" ]; then
        bbnote "Overriding SMP to ${ARCEOS_SMP}"
        axconfig-gen "${config_file}" -w smp="${ARCEOS_SMP}" || \
            bbwarn "Failed to set SMP configuration"
    fi
    
    # Override LOG configuration
    if [ -n "${ARCEOS_LOG}" ]; then
        bbnote "Setting log level to ${ARCEOS_LOG}"
        axconfig-gen "${config_file}" -w log="${ARCEOS_LOG}" || \
            bbwarn "Failed to set log level"
    fi
    
    bbnote "Generated .axconfig.toml:"
    cat "${config_file}"
}

# Setup cargo build environment
arceos_setup_cargo() {
    mkdir -p "${S}/.cargo"
    
    # Create base cargo config for bare-metal target
    # NOTE: Do NOT set [build] target here - it would force build scripts 
    # to compile for the target platform instead of the host.
    # We specify --target on the command line instead.
    cat > "${S}/.cargo/config.toml" <<EOF
[target.x86_64-unknown-linux-gnu]
# Build scripts use host platform
linker = "gcc"

[target.${RUST_TARGET_TRIPLE}]
# ArceOS uses custom linker script, no external linker needed
rustflags = [
    "-C", "link-arg=-nostartfiles",
    "-C", "link-arg=-no-pie",
]

# Git dependency path redirects will be appended by recipe
# [patch.crates-io]
# [patch."https://github.com/..."]
EOF

    bbnote "Created .cargo/config.toml for target ${RUST_TARGET_TRIPLE}"
}

# Hook into do_configure - run AFTER make clean
do_configure:append() {
    arceos_generate_config
    arceos_setup_cargo
}

# Export environment variables for build
export ARCEOS_ARCH
export RUST_TARGET_TRIPLE
export CARGO_BUILD_TARGET = "${RUST_TARGET_TRIPLE}"
