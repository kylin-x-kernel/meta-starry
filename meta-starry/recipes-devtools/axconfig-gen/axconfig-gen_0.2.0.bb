SUMMARY = "ArceOS configuration generator"
DESCRIPTION = "Tool for generating ArceOS platform configurations from TOML files"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
                    file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

inherit cargo

# 从 GitHub 获取源码
SRC_URI = "git://github.com/arceos-org/axconfig_crates.git;protocol=https;branch=main"
SRCREV = "${AUTOREV}"
PV = "0.2.0+git${SRCPV}"

S = "${WORKDIR}/git/axconfig-gen"

# Need CA certificates for cargo to fetch from crates.io
DEPENDS += "ca-certificates-native"

# Disable bitbake's crates vendoring - allow cargo to fetch from crates.io
# This is needed because we don't provide pre-downloaded crates
CARGO_DISABLE_BITBAKE_VENDORING = "1"

# 允许 cargo 在编译时下载依赖（native 工具可以联网）
CARGO_BUILD_FLAGS += "--locked"
do_compile[network] = "1"

BBCLASSEXTEND = "native"
