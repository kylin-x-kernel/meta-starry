SUMMARY = "StarryOS CI Test Image"
DESCRIPTION = "Minimal StarryOS image for CI testing"

require starry-minimal-image.bb

IMAGE_INSTALL:append = " \
    starry-ci-tests \
    test-runner \
    python3-toml \
"

# ==================== CI 镜像特定配置 ====================
# 覆盖 minimal-image 的配置以适应 CI 需求

# 镜像格式：仅 ext4（CI 环境不需要 tar.gz，减少构建时间）
IMAGE_FSTYPES = "ext4"

# 镜像大小：~4GB (4096000 KB)
# 比 minimal-image (256MB) 大得多，用于存放:
# - CI 测试二进制文件
# - libc-test 测试套件
# - 测试运行时日志
IMAGE_ROOTFS_SIZE ?= "4096000"
IMAGE_ROOTFS_EXTRA_SPACE = "524288"

# ==================== Rootfs 后处理 ====================
ROOTFS_POSTPROCESS_COMMAND:append = " create_starry_ci_release; setup_ci_helper_scripts; "

create_starry_ci_release() {
    cat > ${IMAGE_ROOTFS}/etc/starry-release << EOF
StarryOS CI Test Image (v2.0 - Modular Architecture)
====================================================

Version: ${DISTRO_VERSION}
Build Date: $(date -u +"%Y-%m-%d %H:%M:%S UTC")

This is a configuration-driven CI testing image using:
- Modular test architecture
- Pluggable test adapters
- manifest.toml for test configuration

Test location: /usr/lib/starry-ci/
Configuration: /usr/lib/starry-ci/manifest.toml

Available commands:
  run-ci-tests          Run CI tests (human-readable)
  run-ci-tests-json     Run CI tests (JSON output)
  run-ci-tests-tap      Run CI tests (TAP output)
  test-runner -h        Show all options

For more info: test-runner --help

EOF

    cat > ${IMAGE_ROOTFS}/etc/motd << 'EOFMOTD'

 ____  _                        ___  ____  
/ ___|| |_ __ _ _ __ _ __ _   _/ _ \/ ___| 
\___ \| __/ _` | '__| '__| | | | | | \___ \ 
 ___) | || (_| | |  | |  | |_| | |_| |___) |
|____/ \__\__,_|_|  |_|   \__, |\___/|____/ 
                          |___/             

StarryOS CI Test Image (v2.0 - Modular)

Commands:
  run-ci-tests         Run all CI tests
  run-ci-tests-json    JSON output (for CI/CD)
  test-runner -h       All options

Info: cat /etc/starry-release

EOFMOTD
}

setup_ci_helper_scripts() {
    install -d ${IMAGE_ROOTFS}/usr/bin
    
    cat > ${IMAGE_ROOTFS}/usr/bin/run-ci-tests << 'EOF'
#!/bin/sh
# StarryOS CI 测试执行脚本
# 使用配置驱动的 test-runner

# 执行 CI 测试套件
test-runner --suite ci --format human --verbose

# 返回 test-runner 的退出码
exit $?
EOF
    chmod +x ${IMAGE_ROOTFS}/usr/bin/run-ci-tests
    
    # JSON 输出版本（用于 CI/CD）
    cat > ${IMAGE_ROOTFS}/usr/bin/run-ci-tests-json << 'EOF'
#!/bin/sh
# 输出 JSON 格式结果（用于 CI/CD 解析）
test-runner --suite ci --format json
EOF
    chmod +x ${IMAGE_ROOTFS}/usr/bin/run-ci-tests-json
    
    # TAP 输出版本
    cat > ${IMAGE_ROOTFS}/usr/bin/run-ci-tests-tap << 'EOF'
#!/bin/sh
# 输出 TAP 格式结果
test-runner --suite ci --format tap
EOF
    chmod +x ${IMAGE_ROOTFS}/usr/bin/run-ci-tests-tap
}
