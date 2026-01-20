SUMMARY = "StarryOS Test Runner"
DESCRIPTION = "Simple test runner for StarryOS CI tests"
LICENSE = "MIT"
LIC_FILES_CHKSUM = "file://${COMMON_LICENSE_DIR}/MIT;md5=0835ade698e0bcf8506ecda2f7b4f302"

SRC_URI = "file://test-runner.py"

S = "${WORKDIR}"

do_install() {
    install -d ${D}${bindir}
    install -m 0755 ${WORKDIR}/test-runner.py ${D}${bindir}/test-runner
}

FILES:${PN} = "${bindir}/test-runner"
RDEPENDS:${PN} = "python3 python3-toml"
