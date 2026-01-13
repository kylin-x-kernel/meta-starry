SUMMARY = "StarryOS daily benchmark tests"
DESCRIPTION = "Daily performance and stress tests for StarryOS"
HOMEPAGE = "https://github.com/kylin-x-kernel/starry-test-harness"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "git://github.com/kylin-x-kernel/starry-test-harness.git;protocol=https;branch=master"
SRCREV = "${AUTOREV}"
PV = "1.0+git${SRCPV}"

S = "${WORKDIR}/git"

inherit rust-userspace

do_compile() {
    rust_userspace_setup
    
    for testdir in ${S}/tests/daily/cases/*; do
        [ -d "$testdir" ] && [ -f "$testdir/Cargo.toml" ] || continue
        bbnote "Building daily test: $(basename $testdir)"
        cd "$testdir"
        cargo build --release --target ${RUST_USERSPACE_TARGET}
    done
}

do_install() {
    install -d ${D}${libdir}/starry-daily
    
    # Each test case builds to its own target directory
    for testdir in ${S}/tests/daily/cases/*; do
        [ -d "$testdir" ] || continue
        testname=$(basename "$testdir")
        binpath="$testdir/target/${RUST_USERSPACE_TARGET}/release/$testname"
        if [ -x "$binpath" ]; then
            bbnote "Installing: $testname"
            install -m 0755 "$binpath" ${D}${libdir}/starry-daily/
        else
            bbwarn "Binary not found: $binpath"
        fi
    done
    
    if [ -z "$(ls -A ${D}${libdir}/starry-daily/ 2>/dev/null)" ]; then
        bbfatal "No test binaries found to install"
    fi
}

FILES:${PN} = "${libdir}/starry-daily/*"
INSANE_SKIP:${PN} = "already-stripped ldflags"
