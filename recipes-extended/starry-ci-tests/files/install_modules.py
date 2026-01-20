#!/usr/bin/env python3
"""
Module Installer for StarryOS Test Harness
根据 manifest.toml 动态安装测试模块到镜像 rootfs
"""

import sys
import os
import shutil
import argparse
from pathlib import Path
import re

try:
    import toml
except ImportError:
    print("Error: python3-toml module not found", file=sys.stderr)
    sys.exit(1)


class ModuleInstaller:
    """模块安装器 - 根据 manifest.toml 安装测试"""
    
    def __init__(self, manifest_path, source_dir, dest_dir, target_arch):
        with open(manifest_path, 'r') as f:
            self.manifest = toml.load(f)
        self.source_dir = Path(source_dir)
        self.dest_dir = Path(dest_dir)
        self.target_arch = target_arch
        self.installed_count = 0
    
    def install_all(self, suite_name='ci'):
        """安装指定套件的所有测试"""
        # 查找对应的 test_suite
        suite = None
        for ts in self.manifest.get('test_suite', []):
            if ts.get('name') == suite_name:
                suite = ts
                break
        
        if not suite:
            print(f"Error: Test suite '{suite_name}' not found in manifest", file=sys.stderr)
            return False
        
        tests = suite.get('tests', [])
        if not tests:
            print(f"Warning: No tests defined in suite '{suite_name}'", file=sys.stderr)
            return False
        
        print(f"Installing {len(tests)} tests from suite '{suite_name}'...", file=sys.stderr)
        
        # 按类型分组安装
        rust_tests = [t for t in tests if t.get('type') == 'rust']
        libc_tests = [t for t in tests if t.get('type') == 'libc']
        
        if rust_tests:
            self._install_rust_tests(rust_tests)
        
        if libc_tests:
            self._install_libc_tests(libc_tests)
        
        if self.installed_count == 0:
            print("Warning: No test modules were installed!", file=sys.stderr)
            return False
        
        print(f"Successfully installed {self.installed_count} test modules", file=sys.stderr)
        return True
    
    def _install_rust_tests(self, tests):
        """安装 Rust 测试二进制"""
        print(f"Installing {len(tests)} Rust tests...", file=sys.stderr)
        
        dest_dir = self.dest_dir / "rust-tests"
        dest_dir.mkdir(parents=True, exist_ok=True)
        
        deps_dir = self.source_dir / "target" / self.target_arch / "release" / "deps"
        
        if not deps_dir.exists():
            print(f"Error: Rust deps directory not found: {deps_dir}", file=sys.stderr)
            return
        
        for test in tests:
            test_name = test['name']
            # 测试二进制文件名格式: test_name-{16位hash}
            # 例如: file_io_basic-1234567890abcdef
            pattern = re.compile(f"^{test_name}-[0-9a-f]{{16}}$")
            
            found = False
            for binary in deps_dir.iterdir():
                if binary.is_file() and pattern.match(binary.name):
                    # 确认是可执行文件
                    if os.access(binary, os.X_OK):
                        dest = dest_dir / test_name
                        shutil.copy2(binary, dest)
                        dest.chmod(0o755)
                        print(f"  ✓ Installed: {test_name}", file=sys.stderr)
                        self.installed_count += 1
                        found = True
                        break
            
            if not found:
                print(f"  ✗ Warning: Binary not found for test '{test_name}'", file=sys.stderr)
    
    def _install_libc_tests(self, tests):
        """安装 libc-test 测试套件"""
        # libc-test 的所有测试共享同一个 harness
        libc_harness = self.source_dir / "tests" / "ci" / "cases" / "libc-test"
        
        if not libc_harness.exists():
            print(f"Error: libc-test harness not found at {libc_harness}", file=sys.stderr)
            return
        
        print(f"Installing libc-test suite...", file=sys.stderr)
        
        dest_dir = self.dest_dir / "libc-test"
        dest_dir.mkdir(parents=True, exist_ok=True)
        
        # 安装 run 脚本
        run_script = libc_harness / "run"
        if run_script.exists():
            shutil.copy2(run_script, dest_dir / "run")
            (dest_dir / "run").chmod(0o755)
            print("  ✓ Installed: run script", file=sys.stderr)
        else:
            print(f"  ✗ Error: run script not found at {run_script}", file=sys.stderr)
            return
        
        # 安装 src/ 目录（包含所有测试二进制）
        src_dir = libc_harness / "libc-test-bins" / "src"
        if src_dir.exists():
            dest_src = dest_dir / "src"
            if dest_src.exists():
                shutil.rmtree(dest_src)
            shutil.copytree(src_dir, dest_src)
            
            # 确保所有 .exe 文件可执行
            exe_count = 0
            for root, _, files in os.walk(dest_src):
                for f in files:
                    if f.endswith('.exe'):
                        exe_path = Path(root) / f
                        exe_path.chmod(0o755)
                        exe_count += 1
            
            print(f"  ✓ Installed: {exe_count} test binaries", file=sys.stderr)
            self.installed_count += 1
        else:
            print(f"  ✗ Error: libc-test binaries not found at {src_dir}", file=sys.stderr)
            return
        
        # 安装 runtest.exe (在 src/common/ 目录下)
        runtest_exe = libc_harness / "libc-test-bins" / "runtest.exe"
        if runtest_exe.exists():
            common_dir = dest_dir / "src" / "common"
            common_dir.mkdir(parents=True, exist_ok=True)
            shutil.copy2(runtest_exe, common_dir / "runtest.exe")
            (common_dir / "runtest.exe").chmod(0o755)
            print("  ✓ Installed: runtest.exe", file=sys.stderr)
        else:
            print(f"  ✗ Warning: runtest.exe not found at {runtest_exe}", file=sys.stderr)


def main():
    parser = argparse.ArgumentParser(
        description="Install test modules from starry-test-harness to rootfs")
    parser.add_argument('--manifest', required=True,
                        help='Path to manifest.toml')
    parser.add_argument('--source', required=True,
                        help='Source directory (harness root)')
    parser.add_argument('--dest', required=True,
                        help='Destination directory in rootfs')
    parser.add_argument('--target', required=True,
                        help='Rust target architecture (e.g., aarch64-unknown-linux-musl)')
    parser.add_argument('--suite', default='ci',
                        help='Test suite to install (default: ci)')
    
    args = parser.parse_args()
    
    installer = ModuleInstaller(
        manifest_path=args.manifest,
        source_dir=args.source,
        dest_dir=args.dest,
        target_arch=args.target
    )
    
    success = installer.install_all(suite_name=args.suite)
    sys.exit(0 if success else 1)


if __name__ == '__main__':
    main()
