# rust-prebuilt-native
# 下载并安装 Rust 官方预编译工具链 (nightly)

SUMMARY = "Rust nightly prebuilt toolchain (official binaries)"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

inherit native

DEPENDS = "xz-native"

# ==================== 版本配置 ====================
RUST_DATE = "2025-12-12"
RUST_CHANNEL = "nightly"

# ==================== 目标架构定义====================
# 添加新 target：在此字典添加一条即可
python __anonymous() {
    rust_targets = {
        # Bare-metal targets (for kernel development)
        'aarch64-unknown-none-softfloat': '86afbfa9cf2cfb7d686f0f9a616791e28a33da4b2a35ac0fdd889a07c4a95d80',
        'riscv64gc-unknown-none-elf': '118cdea2c09085159b00dffec9eb918b6c9c1aa64d96851fcab86a87daae06a8',
        'loongarch64-unknown-none-softfloat': '9b257d4edd7dce99fe10e7124deabc0f3f776476b503e1e5c681f3bbc4aa3ace',
        'x86_64-unknown-none': 'fd359e46b581b40c1f8952ca96eb022911d3d58fa1baed3011146e8d62ea7c63',
        
        # Linux targets (for userspace)
        'aarch64-unknown-linux-musl': 'acb4307ee294acf881315928993c4960f2ef780b644f0a8fd166377f7c3d310d',
    }
    
    d.setVar('RUST_TARGETS', ' '.join(rust_targets.keys()))
    
    for target, sha256 in rust_targets.items():
        d.setVar(f'RUST_TARGET_{target.replace("-", "_").upper()}_SHA256', sha256)
}

# ==================== 主机工具链选择 ====================
def get_rust_host(d):
    arch_map = {
        'x86_64': 'x86_64-unknown-linux-gnu',
        'aarch64': 'aarch64-unknown-linux-gnu',
        'arm64': 'aarch64-unknown-linux-gnu',
    }
    
    host_arch = d.getVar('BUILD_ARCH')
    rust_host = arch_map.get(host_arch)
    if not rust_host:
        bb.fatal(f"Unsupported host: {host_arch}. Supported: {list(arch_map.keys())}")
    
    return rust_host

RUST_HOST = "${@get_rust_host(d)}"

# ==================== 下载 URL 自动生成 ====================
python __anonymous() {
    rust_date = d.getVar('RUST_DATE')
    rust_channel = d.getVar('RUST_CHANNEL')
    rust_host = d.getVar('RUST_HOST')
    rust_targets = d.getVar('RUST_TARGETS').split()
    
    base_url = f'https://static.rust-lang.org/dist/{rust_date}'
    
    # 主机工具链
    src_uri = [
        f'{base_url}/rust-{rust_channel}-{rust_host}.tar.xz;name=toolchain-${{BUILD_ARCH}}'
    ]
    
    # 目标标准库
    for target in rust_targets:
        src_uri.append(f'{base_url}/rust-std-{rust_channel}-{target}.tar.xz;name=std-{target}')
    
    d.setVar('SRC_URI', ' '.join(src_uri))
}

# ==================== SHA256 校验和====================
# 主机工具链
SRC_URI[toolchain-x86_64.sha256sum] = "027d9e55021c9feb42f7ea2dd7588d355932d3bbf9b44f90f2f890cd74373a26"
SRC_URI[toolchain-aarch64.sha256sum] = "d4d5678099a9e102564df80e4be027e74fd9a324cde156f8dda413b94c81d26c"

# 目标标准库（自动从 RUST_TARGETS 生成）
python __anonymous() {
    rust_targets = d.getVar('RUST_TARGETS').split()
    for target in rust_targets:
        sha256_var = f'RUST_TARGET_{target.replace("-", "_").upper()}_SHA256'
        sha256 = d.getVar(sha256_var)
        d.setVarFlag('SRC_URI', f'std-{target}.sha256sum', sha256)
}

# ==================== 提供虚拟包 ====================
PROVIDES = "rust-native cargo-native"

S = "${WORKDIR}"

do_configure[noexec] = "1"
do_compile[noexec] = "1"
do_fetch[network] = "1"

# ==================== 安装逻辑 ====================
do_install() {
    TOOLCHAIN_DIR="${S}/rust-${RUST_CHANNEL}-${RUST_HOST}"
    
    install -d ${D}/usr/bin ${D}/usr/lib ${D}/usr/libexec
    
    # 安装主工具链
    cp -a ${TOOLCHAIN_DIR}/rustc/bin/* ${D}/usr/bin/
    cp -a ${TOOLCHAIN_DIR}/cargo/bin/* ${D}/usr/bin/
    cp -a ${TOOLCHAIN_DIR}/rustc/lib/* ${D}/usr/lib/
    
    [ -d "${TOOLCHAIN_DIR}/rustc/libexec" ] && \
        cp -a ${TOOLCHAIN_DIR}/rustc/libexec/* ${D}/usr/libexec/
    
    # 安装可选工具
    for component in clippy-preview rustfmt-preview rust-analyzer-preview; do
        [ -d "${TOOLCHAIN_DIR}/${component}/bin" ] && \
            cp -a ${TOOLCHAIN_DIR}/${component}/bin/* ${D}/usr/bin/ 2>/dev/null || true
    done
    
    # 安装标准库
    install -d ${D}/usr/lib/rustlib
    
    # 主机标准库
    if [ -d "${TOOLCHAIN_DIR}/rust-std-${RUST_HOST}/lib/rustlib/${RUST_HOST}" ]; then
        cp -a ${TOOLCHAIN_DIR}/rust-std-${RUST_HOST}/lib/rustlib/${RUST_HOST} \
              ${D}/usr/lib/rustlib/
    fi
    
    # 目标标准库
    for target in ${RUST_TARGETS}; do
        std_dir="${S}/rust-std-${RUST_CHANNEL}-${target}/rust-std-${target}/lib/rustlib/${target}"
        if [ -d "${std_dir}" ]; then
            bbnote "Installing std for ${target}"
            cp -a ${std_dir} ${D}/usr/lib/rustlib/
        else
            bbwarn "std not found for ${target}: ${std_dir}"
        fi
    done
    
    # 链接 LLVM 工具
    LLVM_BIN_DIR="${D}/usr/lib/rustlib/${RUST_HOST}/bin"
    if [ -d "${LLVM_BIN_DIR}" ]; then
        for tool in ${LLVM_BIN_DIR}/*; do
            [ -f "$tool" ] && [ -x "$tool" ] && \
                ln -sf "../lib/rustlib/${RUST_HOST}/bin/$(basename $tool)" \
                       "${D}/usr/bin/$(basename $tool)"
        done
    fi
    
    # 验证安装
    if [ ! -x "${D}/usr/bin/rustc" ]; then
        bbfatal "rustc not installed"
    fi
    if [ ! -x "${D}/usr/bin/cargo" ]; then
        bbfatal "cargo not installed"
    fi
}

# ==================== Sysroot 预处理 ====================
SYSROOT_PREPROCESS_FUNCS += "rust_prebuilt_sysroot_preprocess"
rust_prebuilt_sysroot_preprocess() {
    sysroot_stage_dir ${D}/usr ${SYSROOT_DESTDIR}${STAGING_DIR_NATIVE}/usr
}

# ==================== QA 跳过 ====================
INSANE_SKIP:${PN} = "already-stripped libdir"
