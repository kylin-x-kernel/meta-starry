# StarryOS base-files 定制
#
# 使用方法：
#   取消注释下面的行以自定义 hostname

# 方法 1：固定 hostname（推荐用于测试环境）
# hostname = "starryos-dev"

# 方法 2：根据 MACHINE 动态设置
# hostname = "starryos-${MACHINE}"

# 方法 3：禁用默认 hostname（用于虚拟机/容器环境）
# hostname = ""

# 注意：
# - 如果在 local.conf 或 distro.conf 中已设置 hostname:pn-base-files，
#   那里的设置会覆盖这里的值
# - 当前 meta-starry/conf/distro/starryos.conf 已设置默认值为 "starryos"

