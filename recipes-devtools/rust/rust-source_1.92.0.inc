SRC_URI += "https://static.rust-lang.org/dist/rustc-${PV}-src.tar.xz;name=rust"
SRC_URI[rust.sha256sum] = "ebee170bfe4c4dfc59521a101de651e5534f4dae889756a5c97ca9ea40d0c307"

# Rust standard library for bare-metal targets (all 1.92.0)
SRC_URI += "https://static.rust-lang.org/dist/rust-std-${PV}-aarch64-unknown-none-softfloat.tar.xz;name=rust-std-aarch64-none"
SRC_URI[rust-std-aarch64-none.sha256sum] = "0dc46fafaaa36f53eec49e14a69e1d6d9ac6f0b9624a01081ad311d8139a2be0"

SRC_URI += "https://static.rust-lang.org/dist/rust-std-${PV}-x86_64-unknown-none.tar.xz;name=rust-std-x86_64-none"
SRC_URI[rust-std-x86_64-none.sha256sum] = "1d8420ab8eb241a35e38b76470277c722bd5a7aa4ac0c7a565ad6f30b37cb852"

SRC_URI += "https://static.rust-lang.org/dist/rust-std-${PV}-riscv64gc-unknown-none-elf.tar.xz;name=rust-std-riscv64-none"
SRC_URI[rust-std-riscv64-none.sha256sum] = "7eacf6a98786d58ef912643fd5aedc35111712e96a47b7e5d3ccddcdf9fb166d"

SRC_URI += "https://static.rust-lang.org/dist/rust-std-${PV}-loongarch64-unknown-none-softfloat.tar.xz;name=rust-std-loongarch64-none"
SRC_URI[rust-std-loongarch64-none.sha256sum] = "dd01f0348ca2c90d6115b79894360f9ae8468aa8755e0f345e53fe5186f2d891"

RUSTSRC = "${WORKDIR}/rustc-${PV}-src"

UPSTREAM_CHECK_URI = "https://forge.rust-lang.org/infra/other-installation-methods.html"
UPSTREAM_CHECK_REGEX = "rustc-(?P<pver>\d+(\.\d+)+)-src"

# These CVEs are specific to Microsoft Windows
CVE_CHECK_IGNORE += "CVE-2024-24576 CVE-2024-43402"
