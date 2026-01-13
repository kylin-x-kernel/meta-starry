SUMMARY = "StarryOS Test Suite"
DESCRIPTION = "Comprehensive test suite including CI, stress, and daily tests from starry-test-harness repository"
HOMEPAGE = "https://github.com/kylin-x-kernel/starry-test-harness"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

# ==================== 引用 harness 仓库 ====================

SRC_URI = "git://github.com/kylin-x-kernel/starry-test-harness.git;protocol=https;branch=master"

SRCREV = "59f2d7544ba8ea7b89b2413cd5f71a94b718e6a4"
PV = "1.0"

S = "${WORKDIR}/git"

# ==================== 继承类 ====================
inherit ptest

# ==================== ptest 配置 ====================
# 启用 ptest 功能
PTEST_ENABLED = "1"

# ==================== 依赖项 ====================
DEPENDS += "rust-native cargo-native"
RDEPENDS:${PN}-ptest += "bash coreutils findutils"

# 允许空的主包
ALLOW_EMPTY:${PN} = "1"

# ==================== Rust 目标 ====================
RUST_TARGET = "aarch64-unknown-linux-musl"

do_compile_ptest() {
    bbnote "Skipping compile_ptest (compilation done in install_ptest_base)"
}

do_install_ptest_base() {
    bbnote "Installing ptest framework (tests will be built externally)..."
    
    # 创建目录结构
    install -d ${D}${PTEST_PATH}/ci
    install -d ${D}${PTEST_PATH}/stress
    install -d ${D}${PTEST_PATH}/daily
    
    # 安装 run-ptest 脚本
    install -m 0755 ${S}/scripts/run-ptest ${D}${PTEST_PATH}/run-ptest
    
    # 安装测试源码和脚本（供手动编译或外部 CI 使用）
    # 注意：由于 Cargo 在 Yocto 交叉编译环境中有 "couldn't allocate absolute path" 问题，
    # 实际的测试二进制需要在目标机上编译，或通过独立的 Recipe 构建
    
    bbnote "Installed ptest structure. Test binaries need to be built separately."
    bbnote "You can:"
    bbnote "  1. Build tests manually and place in ${PTEST_PATH}/ci/"
    bbnote "  2. Create separate recipes for each test case"
    bbnote "  3. Use pre-built test binaries from artifacts/"
}

# ==================== 调试信息 ====================
do_install_ptest[dirs] = "${B} ${D}"

# ==================== 文件列表 ====================
FILES:${PN}-ptest += "\
    ${PTEST_PATH}/ci/* \
    ${PTEST_PATH}/stress/* \
    ${PTEST_PATH}/daily/* \
    ${PTEST_PATH}/run-ptest \
"

# ==================== 跳过QA 检查====================
INSANE_SKIP:${PN}-ptest += "already-stripped"

