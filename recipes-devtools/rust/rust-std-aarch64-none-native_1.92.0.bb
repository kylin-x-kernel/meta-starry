# Rust standard library for aarch64-unknown-none-softfloat (built from source)
require rust-target.inc
require rust-source_${PV}.inc

# Clear PROVIDES inherited from rust-target.inc (only rust_1.92.0.bb should provide virtual/rust-native)
PROVIDES:class-native = ""

inherit native
DEPENDS = "rust-native"

# Remove snapshot task (rust-std doesn't need bootstrap, it depends on rust-native)
deltask do_rust_setup_snapshot

INSANE_SKIP:${PN}:class-native = "already-stripped"

# Override do_configure to use rust-native instead of snapshot
python do_configure() {
    import json
    try:
        import configparser
    except ImportError:
        import ConfigParser as configparser

    e = lambda s: json.dumps(s)
    config = configparser.RawConfigParser()

    # [build]
    config.add_section("build")
    config.set("build", "submodules", e(False))
    config.set("build", "docs", e(False))
    
    # Use rustc from rust-native (not snapshot)
    config.set("build", "rustc", e(d.expand("${STAGING_BINDIR_NATIVE}/rustc")))
    config.set("build", "cargo", e(d.expand("${STAGING_BINDIR_NATIVE}/cargo")))
    
    config.set("build", "vendor", e(True))
    config.set("build", "build", e(d.getVar("SNAPSHOT_BUILD_SYS")))

    # [install]
    config.add_section("install")
    config.set("install", "prefix",  e(d.getVar("prefix")))
    config.set("install", "libdir",  e(d.getVar("libdir")))

    with open("config.toml", "w") as f:
        config.write(f)

    bb.build.exec_func("setup_cargo_environment", d)
}

# Compile std library from source for aarch64-unknown-none-softfloat target
do_compile() {
    rust_runx build library/std --target aarch64-unknown-none-softfloat
}

do_install() {
    rust_runx install library/std --target aarch64-unknown-none-softfloat
}

python () {
    pn = d.getVar('PN')

    if not pn.endswith("-native"):
        raise bb.parse.SkipRecipe("Rust recipe doesn't work for target builds at this time. Fixes welcome.")
}
