# StarryOS bare-metal SDK toolchain configuration
# 用于生成能够在开发主机上编译 StarryOS 应用的 SDK

# SDK 主机端工具（在 x86_64 开发机上运行）
TOOLCHAIN_HOST_TASK:append = " \
    nativesdk-rustc-bin \
    nativesdk-cargo-bin \
    nativesdk-cmake \
"

# SDK 目标端库（bare-metal stdlib，不是 Linux 用户空间）
# 根据 MACHINE 自动选择正确的 bare-metal target stdlib
# 注意：bare-metal 不需要 libc/gcc runtime，只需要 Rust stdlib

# 架构映射：从 TUNE_ARCH 到 Rust target
RUST_SDK_TARGET_ARCH = "${@rust_sdk_target_map(d)}"

def rust_sdk_target_map(d):
    """将 Yocto 的 TUNE_ARCH 映射到 Rust bare-metal target"""
    arch = d.getVar('TUNE_ARCH')
    arch_map = {
        'aarch64': 'aarch64-none',
        'riscv64': 'riscv64-none',
        'loongarch64': 'loongarch64-none',
        'x86_64': 'x86_64-none',
    }
    return arch_map.get(arch, 'aarch64-none')  # 默认 aarch64

TOOLCHAIN_TARGET_TASK = " \
    rust-std-${RUST_SDK_TARGET_ARCH} \
"

# SDK 环境变量设置脚本
SDK_TITLE = "StarryOS Bare-Metal SDK"

# 禁用 Linux userspace 相关的默认任务
TOOLCHAIN_HOST_TASK_ATTEMPTONLY = ""
