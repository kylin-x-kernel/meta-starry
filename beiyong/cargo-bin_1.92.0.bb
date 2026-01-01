# Use prebuilt cargo 1.92.0 instead of building from source
# This avoids the bootstrap version mismatch issue

SUMMARY = "Prebuilt Cargo, the Rust package manager"
HOMEPAGE = "https://crates.io"
LICENSE = "MIT | Apache-2.0"
SECTION = "devel"

LIC_FILES_CHKSUM = " \
    file://LICENSE-MIT;md5=b377b220f43d747efdec40d69fcaa69d \
    file://LICENSE-APACHE;md5=71b224ca933f0676e26d5c2e2271331c \
    file://LICENSE-THIRD-PARTY;md5=f257ad009884cb88a3a87d6920e7180a \
"

PV = "1.92.0"

SRC_URI = "https://static.rust-lang.org/dist/cargo-${PV}-${BUILD_ARCH}-unknown-linux-gnu.tar.xz"
SRC_URI[sha256sum] = "e5e12be2c7126a7036c8adf573078a28b92611f5767cc9bd0a6f7c83081df103"

S = "${WORKDIR}/cargo-${PV}-${BUILD_ARCH}-unknown-linux-gnu"

# Prevent Yocto from stripping or modifying prebuilt binaries
INHIBIT_PACKAGE_STRIP = "1"
INHIBIT_SYSROOT_STRIP = "1"
INHIBIT_PACKAGE_DEBUG_SPLIT = "1"
INSANE_SKIP:${PN} = "already-stripped"

# Cargo needs rustc and rust-std to compile build scripts
DEPENDS = "rustc-bin-native rust-std-native"

do_install() {
    ./install.sh --prefix="${D}${prefix}" --disable-ldconfig
    
    # Remove files that conflict with rustc-bin-native
    rm -f ${D}${prefix}/lib/rustlib/uninstall.sh
    rm -f ${D}${prefix}/lib/rustlib/install.log
    rm -f ${D}${prefix}/lib/rustlib/rust-installer-version
    rm -f ${D}${prefix}/lib/rustlib/components
    rm -f ${D}${prefix}/lib/rustlib/manifest-cargo
}

BBCLASSEXTEND = "native nativesdk"
