# arceos-defaults.inc
# ArceOS 构建系统默认配置
#
# 设计原则：
# - 所有 ARCEOS_* 变量的默认值集中在此文件定义
# - 使用 ?= 赋值，允许被 machine.conf / recipe / local.conf 覆盖
# - 对应 StarryOS/Makefile 和 arceos/Makefile 中的默认值

# ==================== 构建模式 ====================
# 对应 Makefile: MODE ?= release
ARCEOS_MODE ?= "release"

# 对应 Makefile: LOG ?= warn
ARCEOS_LOG ?= "warn"

# 对应 Makefile: DWARF ?= y (在 StarryOS/Makefile 中)
ARCEOS_DWARF ?= "y"

# ==================== 设备配置 ====================
# 对应 Makefile: BUS ?= pci
ARCEOS_BUS ?= "pci"

# 对应 StarryOS/Makefile:
# export BLK := y
# export NET := y
# export VSOCK := y
ARCEOS_BLK ?= "y"
ARCEOS_NET ?= "y"
ARCEOS_VSOCK ?= "y"
ARCEOS_GRAPHIC ?= "n"
ARCEOS_INPUT ?= "n"

# ==================== 用户库配置 ====================
# StarryOS 特定: 使用 axfeat 而非 axstd
# 对应 StarryOS/Makefile:
# export NO_AXSTD := y
# export AX_LIB := axfeat
ARCEOS_NO_AXSTD ?= "y"
ARCEOS_AX_LIB ?= "axfeat"

# ==================== QEMU 配置 ====================
# 对应 StarryOS/Makefile: export MEM := 1G
ARCEOS_QEMU_MEM ?= "1G"

# 对应 arceos/Makefile: NET_DEV ?= user
ARCEOS_QEMU_NET_DEV ?= "user"

# ==================== 网络配置 ====================
# 对应 arceos/Makefile:
#   IP ?= 10.0.2.15  (QEMU 用户模式网络默认)
#   GW ?= 10.0.2.2
ARCEOS_IP ?= "10.0.2.15"
ARCEOS_GW ?= "10.0.2.2"

# ==================== Feature 默认值 ====================
# 对应 Makefile: FEATURES ?= (空)
ARCEOS_FEATURES ?= ""

# 对应 StarryOS/Makefile: APP_FEATURES := qemu
# 注意：这个应该在 recipe 中根据目标设置，这里只提供默认值
ARCEOS_APP_FEATURES ?= "qemu"

# ==================== 内存大小转换辅助 ====================
# 用于将人类可读的内存大小 (如 "1G") 转换为字节数
# 这个函数被 arceos.bbclass 中的 axconfig-gen 调用使用

def arceos_mem_to_bytes(mem_str):
    """
    将内存大小字符串转换为字节数
    对应 arceos/scripts/make/strtosz.py
    
    Examples:
        "1G" -> 1073741824
        "128M" -> 134217728
        "512K" -> 524288
    """
    import re
    match = re.match(r'^(\d+)([KMGT]?)$', mem_str.upper())
    if not match:
        return None
    
    num = int(match.group(1))
    unit = match.group(2)
    
    multipliers = {
        '': 1,
        'K': 1024,
        'M': 1024 ** 2,
        'G': 1024 ** 3,
        'T': 1024 ** 4,
    }
    
    return num * multipliers.get(unit, 1)

