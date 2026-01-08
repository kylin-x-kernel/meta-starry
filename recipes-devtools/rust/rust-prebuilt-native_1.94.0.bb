# rust-prebuilt-native
# 下载并安装 Rust 官方预编译工具链 (nightly)
# Nightly 版本包含所有最新的稳定化特性

SUMMARY = "Rust nightly prebuilt toolchain (official binaries)"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit native

# Rust nightly 工具链版本管理
# 版本历史：
#   2026-01-08: 初始版本，支持 x86_64 和 aarch64 Host 架构
#
# 更新方法：运行 scripts/update-rust-nightly.sh <new-date>

RUST_DATE = "2026-01-08"
RUST_CHANNEL = "nightly-${RUST_DATE}"

# 根据构建主机架构选择工具链
def get_rust_host(d):
    import re
    host_arch = d.getVar('BUILD_ARCH')
    host_os = d.getVar('BUILD_OS')
    
    # 映射 Yocto BUILD_ARCH 到 Rust target triple
    arch_map = {
        'x86_64': 'x86_64-unknown-linux-gnu',
        'aarch64': 'aarch64-unknown-linux-gnu',
        'arm64': 'aarch64-unknown-linux-gnu',  # macOS 可能用 arm64
    }
    
    rust_host = arch_map.get(host_arch)
    if not rust_host:
        bb.fatal(f"Unsupported host architecture: {host_arch}. Supported: {list(arch_map.keys())}")
    
    return rust_host

RUST_HOST = "${@get_rust_host(d)}"

# 下载预编译工具链和标准库
SRC_URI = "\
    https://static.rust-lang.org/dist/rust-${RUST_CHANNEL}-${RUST_HOST}.tar.xz;name=toolchain-${BUILD_ARCH} \
    https://static.rust-lang.org/dist/rust-std-${RUST_CHANNEL}-aarch64-unknown-none-softfloat.tar.xz;name=std-aarch64 \
    https://static.rust-lang.org/dist/rust-std-${RUST_CHANNEL}-riscv64gc-unknown-none-elf.tar.xz;name=std-riscv64 \
    https://static.rust-lang.org/dist/rust-std-${RUST_CHANNEL}-loongarch64-unknown-none-softfloat.tar.xz;name=std-loongarch64 \
    https://static.rust-lang.org/dist/rust-std-${RUST_CHANNEL}-x86_64-unknown-none.tar.xz;name=std-x86_64 \
"

# SHA256 校验和 (from https://static.rust-lang.org/dist/*.sha256)
# Host toolchains (支持多种构建主机架构)
SRC_URI[toolchain-x86_64.sha256sum] = "eca1da5e62abb5b906d40778abe0d6b94fe3ad4fb7f18d20bd605ab44b22307b"
SRC_URI[toolchain-aarch64.sha256sum] = "37f51a77093e910f3167b9275d394e71c228cbe8f5582fe87144c6c673ec80dc"

# Target standard libraries (bare-metal 目标架构)
SRC_URI[std-aarch64.sha256sum] = "2c7200035b2b0a1e2d6202beb110aa5d3ecb4d8e906946acbd0afe4c01079668"
SRC_URI[std-riscv64.sha256sum] = "bc5872a8f37f10581b8dcce0da3becb06dde911af99603a8c08dd255bbed16d4"
SRC_URI[std-loongarch64.sha256sum] = "be5dc72dbd2606fc012ba812f7fafa5b3bd14606ff177d2db99594ea2f99e221"
SRC_URI[std-x86_64.sha256sum] = "ba5cf0a4592613e940c7db508c6c39698d130bed4abc8db7086ca023697b60a2"

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
