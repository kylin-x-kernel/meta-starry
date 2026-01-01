# Workaround for user namespace permission issues
# Add network flag to all tasks to prevent BitBake from calling disable_network()

python () {
    # For all recipes (native and target), add network flag to common tasks
    # 包括 Rust/Cargo 工具链特有的任务
    for task in ['do_fetch', 'do_unpack', 'do_patch', 'do_prepare_recipe_sysroot', 
                 'do_configure', 'do_compile', 'do_install', 'do_populate_sysroot',
                 'do_clean', 'do_cleanall', 'do_cleansstate', 'do_preconfigure',
                 'do_kernel_configme', 'do_kernel_configcheck', 'do_devshell',
                 'do_generate_toolchain_file', 'do_rust_gen_targets', 
                 'do_rust_setup_snapshot', 'do_cargo_setup_snapshot',
                 'do_rust_create_wrappers']:
        d.setVarFlag(task, 'network', '1')
}
