SUMMARY = "StarryOS Userspace Package Group"
DESCRIPTION = "Modular package group for StarryOS userspace applications and libraries. \
Provides development tools, network utilities, and filesystem tools. \
Developers can easily extend by adding packages to the appropriate sub-package."
LICENSE = "Apache-2.0"

inherit packagegroup

PACKAGES = "\
    ${PN} \
    ${PN}-dev \
    ${PN}-network \
    ${PN}-filesystem \
"

RDEPENDS:${PN} = "\
    ${PN}-dev \
    ${PN}-network \
    ${PN}-filesystem \
"

RDEPENDS:${PN}-dev = "\
    strace \
    file \
"

RDEPENDS:${PN}-network = "\
"

RDEPENDS:${PN}-filesystem = "\
"

# ==================== 使用说明 ====================
#
# 开发者添加新软件包的方法：
#
# 1. 确定软件包属于哪个类别（dev / network / filesystem）
#
# 2. 在对应的 RDEPENDS:${PN}-xxx 中添加软件包名称：
#    RDEPENDS:${PN}-dev = "\
#        strace \
#        file \
#        my-new-tool \    # 添加新工具
#    "
#
# 3. 如果需要新的分类，可以添加新的子包：
#    PACKAGES += "${PN}-mynewcategory"
#    RDEPENDS:${PN}-mynewcategory = "tool1 tool2"
#    RDEPENDS:${PN} += "${PN}-mynewcategory"
#
# 4. 重新构建镜像：
#    bitbake starry-image
#