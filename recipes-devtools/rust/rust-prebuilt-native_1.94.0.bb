# rust-prebuilt-native
# 下载并安装 Rust 官方预编译工具链 (nightly)
# Nightly 版本包含所有最新的稳定化特性

SUMMARY = "Rust nightly prebuilt toolchain (official binaries)"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit native

# Rust nightly 版本 - 包含稳定化的 maybe_uninit_slice
RUST_CHANNEL = "nightly"
# Pin nightly date to keep checksums stable
RUST_DATE = "2025-12-12"

# 根据 BUILD_ARCH 自动选择主机架构和校验和
def get_rust_host(d):
    build_arch = d.getVar('BUILD_ARCH')
    if build_arch == 'aarch64':
        return 'aarch64-unknown-linux-gnu'
    elif build_arch in ['x86_64', 'i686', 'i586', 'i486', 'i386']:
        return 'x86_64-unknown-linux-gnu'
    else:
        bb.fatal(f"Unsupported BUILD_ARCH for Rust: {build_arch}")

def get_rust_toolchain_checksum(d):
    build_arch = d.getVar('BUILD_ARCH')
    if build_arch == 'aarch64':
        return 'd4d5678099a9e102564df80e4be027e74fd9a324cde156f8dda413b94c81d26c'
    elif build_arch in ['x86_64', 'i686', 'i586', 'i486', 'i386']:
        return '027d9e55021c9feb42f7ea2dd7588d355932d3bbf9b44f90f2f890cd74373a26'
    else:
        bb.fatal(f"Unsupported BUILD_ARCH for Rust: {build_arch}")

RUST_HOST = "${@get_rust_host(d)}"

# 下载预编译工具链和标准库
SRC_URI = "\
    https://static.rust-lang.org/dist/${RUST_DATE}/rust-${RUST_CHANNEL}-${RUST_HOST}.tar.xz;name=toolchain \
    https://static.rust-lang.org/dist/${RUST_DATE}/rust-std-${RUST_CHANNEL}-aarch64-unknown-none-softfloat.tar.xz;name=std-aarch64 \
    https://static.rust-lang.org/dist/${RUST_DATE}/rust-std-${RUST_CHANNEL}-riscv64gc-unknown-none-elf.tar.xz;name=std-riscv64 \
    https://static.rust-lang.org/dist/${RUST_DATE}/rust-std-${RUST_CHANNEL}-loongarch64-unknown-none-softfloat.tar.xz;name=std-loongarch64 \
    https://static.rust-lang.org/dist/${RUST_DATE}/rust-std-${RUST_CHANNEL}-x86_64-unknown-none.tar.xz;name=std-x86_64 \
"

# SHA256 校验和 (from https://static.rust-lang.org/dist/*.sha256)
SRC_URI[toolchain.sha256sum] = "${@get_rust_toolchain_checksum(d)}"
SRC_URI[std-aarch64.sha256sum] = "86afbfa9cf2cfb7d686f0f9a616791e28a33da4b2a35ac0fdd889a07c4a95d80"
SRC_URI[std-riscv64.sha256sum] = "118cdea2c09085159b00dffec9eb918b6c9c1aa64d96851fcab86a87daae06a8"
SRC_URI[std-loongarch64.sha256sum] = "9b257d4edd7dce99fe10e7124deabc0f3f776476b503e1e5c681f3bbc4aa3ace"
SRC_URI[std-x86_64.sha256sum] = "fd359e46b581b40c1f8952ca96eb022911d3d58fa1baed3011146e8d62ea7c63"

# 提供 rust-native 和 cargo-native
PROVIDES = "rust-native cargo-native"

S = "${WORKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"

# 直接安装到 ${D}/usr
do_install() {
    TOOLCHAIN_DIR="${S}/rust-${RUST_CHANNEL}-${RUST_HOST}"
    
    # 手动复制文件到 ${D}/usr
    install -d ${D}/usr/bin
    install -d ${D}/usr/lib
    install -d ${D}/usr/libexec
    
    # 复制 bin
    cp -a ${TOOLCHAIN_DIR}/rustc/bin/* ${D}/usr/bin/
    cp -a ${TOOLCHAIN_DIR}/cargo/bin/* ${D}/usr/bin/
    
    # 复制其他工具 (如果存在)
    for component in clippy-preview rustfmt-preview rust-analyzer-preview; do
        if [ -d "${TOOLCHAIN_DIR}/${component}/bin" ]; then
            cp -a ${TOOLCHAIN_DIR}/${component}/bin/* ${D}/usr/bin/ 2>/dev/null || true
        fi
    done
    
    # 复制 lib
    cp -a ${TOOLCHAIN_DIR}/rustc/lib/* ${D}/usr/lib/
    
    # 复制 libexec (如果存在)
    if [ -d "${TOOLCHAIN_DIR}/rustc/libexec" ]; then
        cp -a ${TOOLCHAIN_DIR}/rustc/libexec/* ${D}/usr/libexec/
    fi
    
    # 复制 rust-std 目标库
    install -d ${D}/usr/lib/rustlib
    
    # 复制主机标准库
    if [ -d "${TOOLCHAIN_DIR}/rust-std-${RUST_HOST}/lib/rustlib/${RUST_HOST}" ]; then
        cp -a ${TOOLCHAIN_DIR}/rust-std-${RUST_HOST}/lib/rustlib/${RUST_HOST} ${D}/usr/lib/rustlib/
    fi
    
    # 复制目标架构标准库
    for target in aarch64-unknown-none-softfloat riscv64gc-unknown-none-elf loongarch64-unknown-none-softfloat x86_64-unknown-none; do
        std_dir="${S}/rust-std-${RUST_CHANNEL}-${target}/rust-std-${target}/lib/rustlib/${target}"
        if [ -d "${std_dir}" ]; then
            bbnote "Installing std for ${target}"
            cp -a ${std_dir} ${D}/usr/lib/rustlib/
        fi
    done
    
    # 创建 LLVM 工具的符号链接到 bindir
    # rust-prebuilt 包含的 LLVM 工具在 lib/rustlib/${HOST_TRIPLE}/bin/
    HOST_TRIPLE="${RUST_HOST}"
    LLVM_BIN_DIR="${D}/usr/lib/rustlib/${HOST_TRIPLE}/bin"
    
    if [ -d "${LLVM_BIN_DIR}" ]; then
        bbnote "Creating symlinks for LLVM tools from ${LLVM_BIN_DIR}"
        for tool in ${LLVM_BIN_DIR}/*; do
            if [ -f "$tool" ] && [ -x "$tool" ]; then
                tool_name=$(basename "$tool")
                bbnote "  Linking ${tool_name}"
                ln -sf "../lib/rustlib/${HOST_TRIPLE}/bin/${tool_name}" "${D}/usr/bin/${tool_name}"
            fi
        done
    else
        bbwarn "LLVM tools directory not found: ${LLVM_BIN_DIR}"
    fi
    
    # 验证安装
    if [ ! -x "${D}/usr/bin/rustc" ]; then
        bbfatal "rustc not found at ${D}/usr/bin/rustc"
    fi
    if [ ! -x "${D}/usr/bin/cargo" ]; then
        bbfatal "cargo not found at ${D}/usr/bin/cargo"
    fi
    
    bbnote "Rust nightly installed to ${D}/usr"
}

# 跳过 QA 检查
INSANE_SKIP:${PN} = "already-stripped libdir"

# 需要网络下载
do_fetch[network] = "1"

# 手动处理 sysroot staging
SYSROOT_PREPROCESS_FUNCS += "rust_prebuilt_sysroot_preprocess"
rust_prebuilt_sysroot_preprocess() {
    sysroot_stage_dir ${D}/usr ${SYSROOT_DESTDIR}${STAGING_DIR_NATIVE}/usr
}
