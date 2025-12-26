# cargo-axplat - Platform package management tool for ArceOS
# Install from crates.io using cargo install

SUMMARY = "Platform package management tool for ArceOS"
DESCRIPTION = "cargo-axplat manages hardware platform packages using axplat"
HOMEPAGE = "https://crates.io/crates/cargo-axplat"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

PV = "0.2.2"

DEPENDS = "cargo-bin-native rustc-bin-native"
INHIBIT_DEFAULT_DEPS = "1"

S = "${WORKDIR}"

do_compile() {
    export CARGO_HOME="${WORKDIR}/cargo_home"
    cargo install \
        --root ${WORKDIR}/install \
        --version ${PV} \
        cargo-axplat
}

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/install/bin/cargo-axplat ${D}${bindir}/
}

BBCLASSEXTEND = "native nativesdk"
