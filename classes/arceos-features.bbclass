# arceos-features.bbclass
# StarryOS Feature 配置系统
#
#
# ==================== 输入变量 ====================
#
#   ARCEOS_APP_FEATURES - 应用 features (如 "qemu", "crosvm", "vf2")
#   ARCEOS_SMP          - CPU 数量，>1 时启用 smp feature
#   ARCEOS_BUS          - 总线类型 mmio/pci (mmio 时添加 mmio feature)
#   ARCEOS_EXTRA_FEATURES - 额外的 starry 包 features
#
# ==================== 输出变量 ====================
#
#   CARGO_FEATURES - 传递给 cargo --features 的字符串
#
# ==================== 使用方法 ====================
#
#   inherit arceos-features
#
# ==================== 参考 ====================
#
#   StarryOS/Cargo.toml [features] 部分
#   StarryOS/Makefile: APP_FEATURES ?= qemu

inherit arceos

# ==================== Feature 解析函数 ====================

def arceos_resolve_features(d):
    """
    根据 Yocto 变量生成 StarryOS 的 CARGO_FEATURES
    
    StarryOS Cargo.toml [features] 结构:
    
    qemu = [
        "axfeat/display", "axfeat/input", "starry-api/input",
        "axfeat/vsock", "starry-api/vsock",
        "axfeat/defplat", "axfeat/bus-pci",
        "axfeat/fs-times", "starry-api/dev-log",
    ]
    smp = ["axfeat/smp", ...]
    mmio = ["axfeat/bus-mmio"]
    pci = ["axfeat/bus-pci"]
    crosvm = ["axfeat/crosvm", "dep:axplat-aarch64-crosvm-virt", ...]
    vf2 = ["dep:axplat-riscv64-visionfive2", "axfeat/driver-sdmmc"]
    """
    import re
    
    # ==================== 获取输入变量 ====================
    app_features_raw = d.getVar('ARCEOS_APP_FEATURES') or 'qemu'
    smp = d.getVar('ARCEOS_SMP') or '1'
    bus = d.getVar('ARCEOS_BUS') or 'pci'
    extra_features_raw = d.getVar('ARCEOS_EXTRA_FEATURES') or ''
    
    bb.note(f"arceos_resolve_features: Input variables:")
    bb.note(f"  ARCEOS_APP_FEATURES={app_features_raw}")
    bb.note(f"  ARCEOS_SMP={smp}")
    bb.note(f"  ARCEOS_BUS={bus}")
    bb.note(f"  ARCEOS_EXTRA_FEATURES={extra_features_raw}")
    
    # ==================== 解析 features 列表 ====================
    features = set()
    
    # 添加应用 features（如 qemu, crosvm, vf2）
    app_features = set(re.split(r'[,\s]+', app_features_raw.strip()))
    app_features.discard('')
    features.update(app_features)
    
    # 添加额外 features
    extra_features = set(re.split(r'[,\s]+', extra_features_raw.strip()))
    extra_features.discard('')
    features.update(extra_features)
    
    # ==================== SMP 多核支持 ====================
    # StarryOS: smp = ["axfeat/smp", ...]
    try:
        smp_num = int(smp)
        if smp_num > 1:
            features.add('smp')
    except ValueError:
        bb.warn(f"Invalid ARCEOS_SMP value: {smp}")
    
    # ==================== 总线类型 ====================
    # StarryOS:
    #   qemu feature 已经包含 axfeat/bus-pci
    #   如果需要 mmio，需要显式添加 mmio feature
    #
    # 注意：mmio 和 qemu 的 bus-pci 可能冲突，
    # 用户应该使用 vf2 等特定平台 feature 而不是手动指定 mmio
    if bus == 'mmio' and 'qemu' in features:
        bb.warn("ARCEOS_BUS=mmio 与 qemu feature 冲突，qemu 默认使用 pci")
        bb.warn("如果需要 mmio，请使用 vf2 等特定平台 feature")
    elif bus == 'mmio':
        features.add('mmio')
    
    # ==================== 构建最终 CARGO_FEATURES ====================
    cargo_features = ' '.join(sorted(features))
    
    bb.note(f"arceos_resolve_features: Output:")
    bb.note(f"  CARGO_FEATURES={cargo_features}")
    
    return cargo_features

# 使用延迟求值，确保 basehash 稳定
CARGO_FEATURES = "${@arceos_resolve_features(d)}"

# ==================== 辅助函数：打印配置 ====================

python arceos_print_feature_config() {
    """调试用：打印 StarryOS feature 配置"""
    bb.note("=" * 60)
    bb.note("StarryOS Feature Configuration")
    bb.note("=" * 60)
    bb.note(f"ARCEOS_APP_FEATURES = {d.getVar('ARCEOS_APP_FEATURES')}")
    bb.note(f"ARCEOS_SMP          = {d.getVar('ARCEOS_SMP')}")
    bb.note(f"ARCEOS_BUS          = {d.getVar('ARCEOS_BUS')}")
    bb.note(f"ARCEOS_EXTRA_FEATURES = {d.getVar('ARCEOS_EXTRA_FEATURES')}")
    bb.note("-" * 60)
    bb.note(f"Final CARGO_FEATURES = {d.getVar('CARGO_FEATURES')}")
    bb.note("=" * 60)
    bb.note("")
    bb.note("StarryOS workspace.dependencies already includes:")
    bb.note("  - fs-ext4 (enables virtio-blk driver)")
    bb.note("  - net (enables virtio-net driver)")
    bb.note("  - irq, multitask, rtc, uspace, ...")
    bb.note("")
    bb.note("These are NOT passed via --features, they're in Cargo.toml")
    bb.note("=" * 60)
}
