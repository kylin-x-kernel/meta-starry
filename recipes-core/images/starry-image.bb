# starry-image.bb
# StarryOS 标准发行版镜像

require starry-minimal-image.bb

SUMMARY = "StarryOS Standard Distribution Image"
DESCRIPTION = "Full-featured StarryOS distribution with userspace development tools. \
Includes StarryOS kernel, musl C library, BusyBox, network tools, \
and a comprehensive set of development utilities. \
Suitable for application development, debugging, and production deployment."

# 开发者可通过修改 packagegroup-starry-userspace.bb 添加新软件
IMAGE_INSTALL:append = " packagegroup-starry-userspace"

# 512MB rootfs + 64MB 额外空间 = ~576MB 总镜像大小
IMAGE_ROOTFS_SIZE ?= "524288"
IMAGE_ROOTFS_EXTRA_SPACE = "65536"

# ==================== 镜像层次说明 ====================
#
# StarryOS 提供三个层次的镜像：
#
# 1. starry-minimal-image (本镜像的基础)
#    - 最小系统：~256MB
#    - 包含：内核、musl、BusyBox、基础网络工具
#    - 适合：嵌入式部署、资源受限环境
#
# 2. starry-image (本镜像)
#    - 标准系统：~512MB
#    - 包含：minimal-image + 开发工具 + 文件系统工具
#    - 适合：日常开发、应用调试、生产部署
#
# 3. starry-test-image
#    - 测试系统：~8GB
#    - 包含：starry-minimal-image + OEQA 测试套件 + 性能测试工具
#    - 适合：系统测试、性能评估、CI/CD
#