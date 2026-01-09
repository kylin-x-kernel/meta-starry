# arceos-features.bbclass
# Feature 自动推导系统 -  复刻 arceos/scripts/make/features.mk 逻辑
#
# 设计原则：
# - 本 bbclass 只负责 Feature 解析和 CARGO_FEATURES 生成
# - 继承 arceos.bbclass 获取配置生成和构建逻辑
#
# 输入变量 (对应 Makefile 变量):
#   ARCEOS_FEATURES     - 用户指定的 features (对应 FEATURES)
#   ARCEOS_APP_FEATURES - 应用 features (对应 APP_FEATURES)
#   ARCEOS_PLAT_PACKAGE - 平台包名 (对应 MYPLAT)
#   ARCEOS_SMP          - CPU 数量 (对应 SMP)
#   ARCEOS_BUS          - 总线类型 mmio/pci (对应 BUS)
#   ARCEOS_DWARF        - 调试信息 y/n (对应 DWARF)
#   ARCEOS_NO_AXSTD     - 禁用 axstd y/n (对应 NO_AXSTD)
#   ARCEOS_AX_LIB       - 用户库类型 axfeat/axstd/axlibc (对应 AX_LIB)
#
# 输出变量:
#   CARGO_FEATURES - 最终传递给 cargo --features 的字符串
#
# Usage:
#   inherit arceos-features

inherit arceos

# ==================== Feature 解析 Python 函数 ====================

def arceos_resolve_features(d):
    """
    features.mk 逻辑摘要:
    1. 根据 APP_TYPE 和 NO_AXSTD 确定 ax_feat_prefix (axfeat/ 或 axstd/)
    2. lib_feat_prefix = AX_LIB/
    3. 根据 MYPLAT 添加 myplat 或 defplat
    4. 根据 BUS=mmio 添加 bus-mmio
    5. 根据 DWARF=y 添加 dwarf  
    6. 根据 SMP > 1 添加 smp 到 lib_feat
    7. 分离 FEATURES 到 ax_feat 和 lib_feat
    8. 最终: AX_FEAT = ax_feat_prefix + ax_feat
    9. 最终: LIB_FEAT = lib_feat_prefix + lib_feat
    10. 最终: APP_FEAT = APP_FEATURES 
    """
    import re
    
    # ==================== 获取输入变量 ====================
    features_raw = d.getVar('ARCEOS_FEATURES') or ''
    app_features_raw = d.getVar('ARCEOS_APP_FEATURES') or ''
    plat_package = d.getVar('ARCEOS_PLAT_PACKAGE') or ''
    use_myplat = d.getVar('ARCEOS_USE_MYPLAT') or '0'
    smp = d.getVar('ARCEOS_SMP') or '1'
    bus = d.getVar('ARCEOS_BUS') or 'pci'
    dwarf = d.getVar('ARCEOS_DWARF') or 'n'
    no_axstd = d.getVar('ARCEOS_NO_AXSTD') or 'n'
    ax_lib = d.getVar('ARCEOS_AX_LIB') or 'axfeat'
    
    bb.note(f"arceos_resolve_features: Input variables:")
    bb.note(f"  ARCEOS_FEATURES={features_raw}")
    bb.note(f"  ARCEOS_APP_FEATURES={app_features_raw}")
    bb.note(f"  ARCEOS_PLAT_PACKAGE={plat_package}")
    bb.note(f"  ARCEOS_USE_MYPLAT={use_myplat}")
    bb.note(f"  ARCEOS_SMP={smp}")
    bb.note(f"  ARCEOS_BUS={bus}")
    bb.note(f"  ARCEOS_DWARF={dwarf}")
    bb.note(f"  ARCEOS_NO_AXSTD={no_axstd}")
    bb.note(f"  ARCEOS_AX_LIB={ax_lib}")
    
    # ==================== 解析 features 列表 ====================
    # features.mk:28: override FEATURES := $(shell echo $(FEATURES) | tr ',' ' ')
    features = set(re.split(r'[,\s]+', features_raw.strip()))
    features.discard('')
    
    # APP_FEATURES 同样处理
    app_features = set(re.split(r'[,\s]+', app_features_raw.strip()))
    app_features.discard('')
    
    # ==================== 确定前缀 ====================
    # features.mk:14-24
    # StarryOS 使用 NO_AXSTD=y，所以 ax_feat_prefix = axfeat/
    if no_axstd == 'y':
        ax_feat_prefix = 'axfeat/'
    else:
        ax_feat_prefix = 'axstd/'
    
    # features.mk:26
    lib_feat_prefix = f'{ax_lib}/'
    
    # ==================== 定义 lib_features 列表 ====================
    # features.mk:16 (仅 C 应用使用，但我们也用于分类)
    lib_features_set = {'fp-simd', 'irq', 'alloc', 'multitask', 'fs', 'net', 'fd', 'pipe', 'select', 'epoll'}
    
    # ==================== 初始化 feature 集合 ====================
    ax_feat = set()
    lib_feat = set()
    
    # ==================== 平台类型 ====================
    # features.mk:44-48: 根据 MYPLAT 是否设置决定 myplat/defplat
    # 注意: PLAT_PACKAGE 和 MYPLAT 是不同的变量
    # - PLAT_PACKAGE 用于查找平台配置文件（如 axplat-aarch64-qemu-virt）
    # - MYPLAT 用于启用 myplat 特性（自定义平台，减小二进制大小）
    # 
    # StarryOS 默认: MYPLAT ?= (空)
    #               PLAT_PACKAGE 根据 ARCH 自动设置
    # 
    # 因此默认使用 defplat（包含所有平台，通过条件编译选择）
    # 仅当显式设置 ARCEOS_USE_MYPLAT="1" 时才使用 myplat
    if use_myplat == '1':
        ax_feat.add('myplat')
    else:
        ax_feat.add('defplat')
    
    # ==================== 总线类型 ====================
    # features.mk:54-56
    if bus == 'mmio':
        ax_feat.add('bus-mmio')
    # 注意: bus=pci 时不添加 bus-pci，因为 defplat 默认就是 pci
    
    # ==================== DWARF 调试信息 ====================
    # features.mk:58-60
    if dwarf == 'y':
        ax_feat.add('dwarf')
    
    # ==================== SMP 多核支持 ====================
    # features.mk:62-64
    try:
        smp_num = int(smp)
        if smp_num > 1:
            lib_feat.add('smp')
    except ValueError:
        bb.warn(f"Invalid ARCEOS_SMP value: {smp}")
    
    # ==================== 分离 FEATURES ====================
    # features.mk:66-67
    for f in features:
        if f in lib_features_set:
            lib_feat.add(f)
        else:
            ax_feat.add(f)
    
    # ==================== 构建最终 CARGO_FEATURES ====================
    # features.mk:69-71
    final_features = []
    
    # AX_FEAT: 添加 ax_feat (带前缀)
    for f in sorted(ax_feat):
        final_features.append(f'{ax_feat_prefix}{f}')
    
    # LIB_FEAT: 添加 lib_feat (带前缀)
    for f in sorted(lib_feat):
        final_features.append(f'{lib_feat_prefix}{f}')
    
    # APP_FEAT: 添加 app_features (原样，不带前缀)
    for f in sorted(app_features):
        final_features.append(f)
    
    # ==================== 返回最终特性字符串 ====================
    cargo_features = ' '.join(final_features)
    
    bb.note(f"arceos_resolve_features: Output:")
    bb.note(f"  ax_feat (raw): {sorted(ax_feat)}")
    bb.note(f"  lib_feat (raw): {sorted(lib_feat)}")
    bb.note(f"  app_features (raw): {sorted(app_features)}")
    bb.note(f"  CARGO_FEATURES={cargo_features}")
    return cargo_features

# ==================== 由变量展开触发解析 ====================
# 采用纯函数返回，避免解析期副作用导致 basehash 不稳定
CARGO_FEATURES = "${@arceos_resolve_features(d)}"

# ==================== 辅助函数：打印 Feature 映射关系 ====================
python arceos_print_feature_mapping() {
    """调试用：打印 Makefile 变量到 Yocto 变量的映射"""
    bb.note("=" * 60)
    bb.note("ArceOS Feature Mapping (Makefile -> Yocto)")
    bb.note("=" * 60)
    bb.note(f"ARCH           -> ARCEOS_ARCH         = {d.getVar('ARCEOS_ARCH')}")
    bb.note(f"MYPLAT         -> ARCEOS_PLAT_PACKAGE = {d.getVar('ARCEOS_PLAT_PACKAGE')}")
    bb.note(f"FEATURES       -> ARCEOS_FEATURES     = {d.getVar('ARCEOS_FEATURES')}")
    bb.note(f"APP_FEATURES   -> ARCEOS_APP_FEATURES = {d.getVar('ARCEOS_APP_FEATURES')}")
    bb.note(f"SMP            -> ARCEOS_SMP          = {d.getVar('ARCEOS_SMP')}")
    bb.note(f"BUS            -> ARCEOS_BUS          = {d.getVar('ARCEOS_BUS')}")
    bb.note(f"DWARF          -> ARCEOS_DWARF        = {d.getVar('ARCEOS_DWARF')}")
    bb.note(f"NO_AXSTD       -> ARCEOS_NO_AXSTD     = {d.getVar('ARCEOS_NO_AXSTD')}")
    bb.note(f"AX_LIB         -> ARCEOS_AX_LIB       = {d.getVar('ARCEOS_AX_LIB')}")
    bb.note("-" * 60)
    bb.note(f"Final CARGO_FEATURES = {d.getVar('CARGO_FEATURES')}")
    bb.note("=" * 60)
}
