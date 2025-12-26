SUMMARY = "Cargo binutils - LLVM tools wrappers"
DESCRIPTION = "Provides rust-objcopy, rust-objdump, etc. via rustc's llvm-tools"
LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

S = "${WORKDIR}"

INHIBIT_DEFAULT_DEPS = "1"
DEPENDS:class-native = "rustc-bin-native"

do_install() {
    install -d ${D}${bindir}
    
    # Create wrappers to rustc's llvm-tools
    # rustc-native provides llvm-objcopy in lib/rustlib/<host>/bin/
    cat > ${D}${bindir}/rust-objcopy <<'EOF'
#!/bin/sh
exec "$(rustc --print sysroot)/lib/rustlib/x86_64-unknown-linux-gnu/bin/llvm-objcopy" "$@"
EOF
    chmod +x ${D}${bindir}/rust-objcopy
    
    cat > ${D}${bindir}/rust-objdump <<'EOF'
#!/bin/sh
exec "$(rustc --print sysroot)/lib/rustlib/x86_64-unknown-linux-gnu/bin/llvm-objdump" "$@"
EOF
    chmod +x ${D}${bindir}/rust-objdump
    
    cat > ${D}${bindir}/rust-nm <<'EOF'
#!/bin/sh
exec "$(rustc --print sysroot)/lib/rustlib/x86_64-unknown-linux-gnu/bin/llvm-nm" "$@"
EOF
    chmod +x ${D}${bindir}/rust-nm
    
    cat > ${D}${bindir}/rust-strip <<'EOF'
#!/bin/sh
exec "$(rustc --print sysroot)/lib/rustlib/x86_64-unknown-linux-gnu/bin/llvm-strip" "$@"
EOF
    chmod +x ${D}${bindir}/rust-strip
}

BBCLASSEXTEND = "native nativesdk"
