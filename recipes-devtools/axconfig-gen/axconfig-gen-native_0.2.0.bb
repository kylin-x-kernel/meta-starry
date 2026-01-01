SUMMARY = "ArceOS configuration generator"
DESCRIPTION = "Tool for generating ArceOS platform configurations from TOML files"
LICENSE = "MIT | Apache-2.0"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302 \
                    file://${COMMON_LICENSE_DIR}/Apache-2.0;md5=89aea4e17d99a7cacdbeed46a0096b10"

inherit native

DEPENDS = "cargo-bin-native rustc-bin-native"

# Ensure cargo is ready before fetch
do_fetch[depends] += "cargo-bin-native:do_populate_sysroot"
do_fetch[network] = "1"
do_prepare_recipe_sysroot[network] = "1"
do_install[network] = "1"

# Create wrapper scripts in do_prepare_recipe_sysroot_prepend to make cc available
python do_prepare_recipe_sysroot:prepend() {
    import os
    workdir = d.getVar('WORKDIR')
    bindir = os.path.join(workdir, 'bin')
    os.makedirs(bindir, exist_ok=True)
    
    # Create cc wrapper pointing to gcc
    cc_wrapper = os.path.join(bindir, 'cc')
    with open(cc_wrapper, 'w') as f:
        f.write('#!/bin/sh\nexec gcc "$@"\n')
    os.chmod(cc_wrapper, 0o755)
    
    # Add to PATH
    path = d.getVar('PATH')
    d.setVar('PATH', bindir + ':' + path)
}

do_fetch() {
    export CARGO_HOME="${WORKDIR}/cargo"
    export CARGO_INSTALL_ROOT="${WORKDIR}/install"
    
    # Install axconfig-gen using cargo in fetch phase
    cargo install --version 0.2.0 axconfig-gen
}

do_unpack[noexec] = "1"
do_patch[noexec] = "1"
do_configure[noexec] = "1"
do_compile[noexec] = "1"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/install/bin/axconfig-gen ${D}${bindir}/
}

INSANE_SKIP:${PN} += "already-stripped"
