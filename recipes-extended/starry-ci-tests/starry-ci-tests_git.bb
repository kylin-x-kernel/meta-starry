SUMMARY = "StarryOS CI functional tests"
DESCRIPTION = "Functional tests for StarryOS from starry-test-harness (modular architecture)"
HOMEPAGE = "https://github.com/kylin-x-kernel/starry-test-harness"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/kylin-x-kernel/starry-test-harness.git;protocol=https;branch=master \
           file://install_modules.py \
          "
SRCREV = "${AUTOREV}"
PV = "2.0+git${SRCPV}"

S = "${WORKDIR}/git/tests/ci/cases"

inherit rust-userspace

DEPENDS += "python3-toml-native"

do_compile() {
    rust_userspace_setup
    
    cd ${S}
    cargo test --no-run --release --target ${RUST_USERSPACE_TARGET}
}


do_compile[network] = "1"

do_install() {
    install -d ${D}${libdir}/starry-tests
    install -d ${D}${libdir}/starry-tests/common
    
    # ========== 安装 manifest.toml ==========
    MANIFEST="${WORKDIR}/git/manifest.toml"
    if [ -f "$MANIFEST" ]; then
        install -m 0644 "$MANIFEST" ${D}${libdir}/starry-tests/
        bbnote "Installed manifest.toml"
    else
        bbwarn "manifest.toml not found in harness repository"
    fi
    
    # ========== 安装公共工具 ==========
    COMMON_DIR="${WORKDIR}/git/common"
    if [ -d "$COMMON_DIR" ]; then
        cp -r "$COMMON_DIR"/* ${D}${libdir}/starry-tests/common/
        chmod +x ${D}${libdir}/starry-tests/common/*.sh 2>/dev/null || true
        bbnote "Installed common utilities"
    else
        bbwarn "common/ directory not found in harness repository"
    fi
    
    # ========== 使用 Python helper 动态安装模块 ==========
    if [ -f "$MANIFEST" ]; then
        bbnote "Installing modules via install_modules.py..."
        # 使用 nativepython3 以确保能找到 python3-toml-native
        nativepython3 ${WORKDIR}/install_modules.py \
            --manifest "$MANIFEST" \
            --source "${WORKDIR}/git" \
            --dest "${D}${libdir}/starry-tests" \
            --target "${RUST_USERSPACE_TARGET}" \
            --suite ci || bbwarn "Module installation had warnings"
    fi
    
    # ========== 验证安装 ==========
    if [ ! -d "${D}${libdir}/starry-tests/rust-tests" ] && [ ! -d "${D}${libdir}/starry-tests/libc-test" ]; then
        bbwarn "No test modules installed - check manifest.toml and module structure"
    fi
}

FILES:${PN} = "${libdir}/starry-tests/*"
INSANE_SKIP:${PN} = "already-stripped ldflags"
RDEPENDS:${PN} = "python3 python3-toml"
