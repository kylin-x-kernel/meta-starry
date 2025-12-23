DESCRIPTION = "Starry Rust kernel (QEMU AArch64暂时)"
SECTION = "kernel"
LICENSE = "Apache-2.0"
LIC_FILES_CHKSUM = "file://LICENSE;md5=175792518e4ac015ab6696d16c4f607e"

inherit starry-cargo-env deploy

# StarryOS targets bare-metal Rust triples; ensure rustc sysroot includes the
# required core artifacts from rust-bin-cross.
DEPENDS += "rust-bin-cross-${TARGET_ARCH}"

PV = "1.0+git${SRCPV}"

# 仅携带本层的配置文件；源码由 externalsrc 提供
SRC_URI = "file://axconfig-aarch64-qemu-virt.toml"

S = "${WORKDIR}/git"
B = "${WORKDIR}/build"

COMPATIBLE_MACHINE = "(qemuarm64|aarch64.*)"

EXTRA_OEMAKE = " \
  ARCH=aarch64 \
  APP_FEATURES=qemu \
  LOG=warn \
  A=${S} \
  PLAT_CONFIG=${WORKDIR}/axconfig-aarch64-qemu-virt.toml \
  AX_SKIP_DEPS=1 \
  OBJCOPY=aarch64-linux-musl-objcopy \
  OBJDUMP=aarch64-linux-musl-objdump \
 "

do_configure[noexec] = "1"

do_compile() {
    # 要求源码树已包含 vendor/
    if [ ! -d "${S}/vendor" ]; then
        bbfatal "Missing vendored Rust deps: ${S}/vendor; run: bash StarryOS/tools/yocto-vendor.sh (outside bitbake), then retry."
    fi

    export CARGO_HOME="${WORKDIR}/cargo_home"
    export HOME="${CARGO_HOME}"
    export CARGO_NET_OFFLINE="true"
    install -d "$CARGO_HOME"
    cat > "$CARGO_HOME/config.toml" <<EOF
[net]
git-fetch-with-cli = true
[source.crates-io]
replace-with = "vendored-sources"
[source.vendored-sources]
directory = "${S}/vendor"
EOF

    if [ -f "${S}/vendor/config.toml" ]; then
        python3 - "${S}/vendor/config.toml" >> "$CARGO_HOME/config.toml" <<'PY'
import sys

path = sys.argv[1]
skip = False
for raw in open(path, "r", encoding="utf-8"):
    line = raw
    if line.startswith("[") and line.rstrip().endswith("]"):
        section = line.strip()
        skip = section in ("[source.crates-io]", "[source.vendored-sources]")
    if not skip:
        sys.stdout.write(line)
PY
    fi

    # 构建真实的 axconfig-gen（上游 make 会依赖它生成 axconfig::plat 等配置模块）
    install -d ${B}/.tools/bin
    export CARGO_TARGET_DIR="${B}/axconfig-gen-target"
    rm -rf "${B}/axconfig-gen-src"
    cp -a "${S}/vendor/axconfig-gen-0.2.0" "${B}/axconfig-gen-src"
    rm -f "${B}/axconfig-gen-src/Cargo.lock"
    cargo build --manifest-path "${B}/axconfig-gen-src/Cargo.toml" --release
    install -m 0755 "$CARGO_TARGET_DIR/release/axconfig-gen" "${B}/.tools/bin/axconfig-gen"
    export PATH="${B}/.tools/bin:${WRAPPER_DIR}:${STAGING_BINDIR_NATIVE}:/usr/bin:/bin:$PATH"

    # 跳过 arceos 自带的 cargo 安装逻辑
    if [ -f ${S}/arceos/scripts/make/deps.mk ]; then
        if ! head -n1 ${S}/arceos/scripts/make/deps.mk | grep -q "AX_SKIP_DEPS"; then
            sed -i '1i ifneq ($(AX_SKIP_DEPS),1)' ${S}/arceos/scripts/make/deps.mk
            printf "\nendif\n" >> ${S}/arceos/scripts/make/deps.mk
        fi
    fi

    # 上游使用 cargo -Z unstable-options：通过环境变量允许稳定 cargo 跑 nightly gate
    export __CARGO_TEST_CHANNEL_OVERRIDE_DO_NOT_USE_THIS="nightly"
    export CARGO_UNSTABLE_TARGET_APPLIES_TO_HOST="true"
    export CARGO_UNSTABLE_HOST_CONFIG="true"

    # externalsrc 模式下 ${B} 是独立构建目录，里面没有上游 Makefile；用 -C 指向源码目录
    oe_runmake -C ${S} build
}

do_install[noexec] = "1"

do_deploy() {
    install -d ${DEPLOYDIR}
    # Upstream ArceOS uses OUT_DIR=$(APP) (passed via A=...), so the final ELF
    # is commonly written into ${S}. With externalsrc, we still keep ${B} for
    # intermediate artifacts. Search both, but prefer ${S} (it is deterministic
    # for StarryOS builds).
    found_elf="$(find "${S}" -maxdepth 2 -type f -name "*.elf" 2>/dev/null | head -n1 || true)"
    if [ -z "$found_elf" ] || [ ! -f "$found_elf" ]; then
        found_elf="$(find "${B}" -maxdepth 8 -type f -name "*.elf" 2>/dev/null | head -n1 || true)"
    fi
    if [ -z "$found_elf" ] || [ ! -f "$found_elf" ]; then
        bbfatal "No ELF produced under ${S} or ${B}; check arceos build outputs"
    fi
    shortsha=$(git -C ${S} rev-parse --short HEAD 2>/dev/null || true)
    [ -z "$shortsha" ] && shortsha="${@d.getVar('SRCREV')[:8]}"
    base="starry-kernel-${MACHINE}-${shortsha}"
    install -m 0644 "$found_elf" "${DEPLOYDIR}/${base}.elf"
    ln -sf "${base}.elf" "${DEPLOYDIR}/starry-kernel-${MACHINE}.elf"
    ln -sf "starry-kernel-${MACHINE}.elf" "${DEPLOYDIR}/starry-kernel.elf"
}

addtask deploy after do_compile before do_build
