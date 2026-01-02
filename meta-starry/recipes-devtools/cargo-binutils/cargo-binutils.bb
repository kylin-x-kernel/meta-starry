SUMMARY = "Cargo binutils - LLVM tools wrappers"
DESCRIPTION = "Provides rust-objcopy, rust-objdump, etc. via rustc's llvm-tools"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

S = "${WORKDIR}"

INHIBIT_DEFAULT_DEPS = "1"
DEPENDS:class-native = "rust-native"

do_install() {
    install -d ${D}${bindir}
    
    # Detect host triple from rustc
    # rustc-native provides llvm-tools in lib/rustlib/<host>/bin/
    cat > ${D}${bindir}/rust-objcopy <<'EOF'
#!/bin/sh
HOST_TRIPLE=$(rustc -vV | awk '/^host:/ { print $2 }')
exec "$(rustc --print sysroot)/lib/rustlib/$HOST_TRIPLE/bin/llvm-objcopy" "$@"
EOF
    chmod +x ${D}${bindir}/rust-objcopy
    
    cat > ${D}${bindir}/rust-objdump <<'EOF'
#!/bin/sh
HOST_TRIPLE=$(rustc -vV | awk '/^host:/ { print $2 }')
exec "$(rustc --print sysroot)/lib/rustlib/$HOST_TRIPLE/bin/llvm-objdump" "$@"
EOF
    chmod +x ${D}${bindir}/rust-objdump
    
    cat > ${D}${bindir}/rust-nm <<'EOF'
#!/bin/sh
HOST_TRIPLE=$(rustc -vV | awk '/^host:/ { print $2 }')
exec "$(rustc --print sysroot)/lib/rustlib/$HOST_TRIPLE/bin/llvm-nm" "$@"
EOF
    chmod +x ${D}${bindir}/rust-nm
    
    cat > ${D}${bindir}/rust-strip <<'EOF'
#!/bin/sh
HOST_TRIPLE=$(rustc -vV | awk '/^host:/ { print $2 }')
exec "$(rustc --print sysroot)/lib/rustlib/$HOST_TRIPLE/bin/llvm-strip" "$@"
EOF
    chmod +x ${D}${bindir}/rust-strip
}

BBCLASSEXTEND = "native nativesdk"
