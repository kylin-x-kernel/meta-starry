# Use upstream common include from poky/core
require ${COREBASE}/meta/recipes-kernel/linux-libc-headers/linux-libc-headers.inc

SRC_URI:append:libc-musl = "\
    file://0001-libc-compat.h-fix-some-issues-arising-from-in6.h.patch \
    file://0003-remove-inclusion-of-sysinfo.h-in-kernel.h.patch \
    file://0001-libc-compat.h-musl-_does_-define-IFF_LOWER_UP-DORMAN.patch \
   "

SRC_URI += "\
    file://0001-kbuild-install_headers.sh-Strip-_UAPI-from-if-define.patch \
    file://0001-connector-Fix-invalid-conversion-in-cn_proc.h.patch \
"

LIC_FILES_CHKSUM = "file://COPYING;md5=6bc538ed5bd9a7fc9398086aedcd7e46"

SRC_URI[sha256sum] = "d926a06c63dd8ac7df3f86ee1ffc2ce2a3b81a2d168484e76b5b389aba8e56d0"

# Upstream kernel tree uses arch/loongarch/, map ARCH explicitly without touching poky classes
ARCH = "loongarch"
