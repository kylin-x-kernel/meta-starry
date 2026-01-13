SUMMARY = "StarryOS CI functional tests"
DESCRIPTION = "Functional tests for StarryOS from starry-test-harness"
HOMEPAGE = "https://github.com/kylin-x-kernel/starry-test-harness"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/kylin-x-kernel/starry-test-harness.git;protocol=https;branch=master"
SRCREV = "${AUTOREV}"
PV = "1.0+git${SRCPV}"

S = "${WORKDIR}/git/tests/ci/cases"

inherit rust-userspace

do_compile() {
    rust_userspace_setup
    
    cd ${S}
    cargo test --no-run --release --target ${RUST_USERSPACE_TARGET}
}

do_install() {
    install -d ${D}${libdir}/starry-ci
    
    # Cargo builds to workspace root (git/target), not ${S}/target
    for bin in ${WORKDIR}/git/target/${RUST_USERSPACE_TARGET}/release/deps/*-[0-9a-f]*[0-9a-f]; do
        # Skip non-executable files, symlinks, and lib files
        [ -x "$bin" ] && [ ! -L "$bin" ] || continue
        case "$bin" in
            *.so|*.rlib|*build_script_*) continue ;;
        esac
        
        # Install with original name (with hash)
        install -m 0755 "$bin" ${D}${libdir}/starry-ci/
        
        # Create symlink without hash for easy access
        # Example: file_io_basic-8cf43a29b8d19758 -> file_io_basic
        binname=$(basename "$bin")
        cleanname=$(echo "$binname" | sed 's/-[0-9a-f]\{16\}$//')
        if [ "$cleanname" != "$binname" ]; then
            ln -sf "$binname" "${D}${libdir}/starry-ci/$cleanname"
        fi
    done
    
    if [ -z "$(ls -A ${D}${libdir}/starry-ci/ 2>/dev/null)" ]; then
        bbfatal "No test binaries found in ${WORKDIR}/git/target/${RUST_USERSPACE_TARGET}/release/deps/"
    fi
}

FILES:${PN} = "/usr/lib/starry-ci/*"
INSANE_SKIP:${PN} = "already-stripped ldflags"
