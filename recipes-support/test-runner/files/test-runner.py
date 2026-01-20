#!/usr/bin/env python3


import sys
import os
import argparse
import subprocess
import time
from pathlib import Path

try:
    import toml
except ImportError:
    print("Error: python3-toml not installed", file=sys.stderr)
    sys.exit(1)


# ==================== 配置 ====================
DEFAULT_MANIFEST = "/usr/lib/starry-tests/manifest.toml"
DEFAULT_TEST_DIR = "/usr/lib/starry-tests"


# ==================== 测试执行 ====================
def run_rust_test(test_dir, test_config, verbose=False):
    """运行单个 Rust 测试"""
    name = test_config['name']
    rust_dir = Path(test_dir) / "rust-tests"
    binary = rust_dir / name
    
    if not binary.exists():
        return {"name": name, "status": "error", "output": f"Binary not found: {binary}"}
    
    try:
        start = time.time()
        result = subprocess.run([str(binary)], capture_output=True, text=True, timeout=60)
        duration = time.time() - start
        
        status = "pass" if result.returncode == 0 else "fail"
        output = result.stdout + result.stderr
        
        return {"name": name, "status": status, "duration": duration, "output": output}
    except subprocess.TimeoutExpired:
        return {"name": name, "status": "fail", "output": "Timeout (60s)"}
    except Exception as e:
        return {"name": name, "status": "error", "output": str(e)}


def run_libc_test(test_dir, test_config, verbose=False):
    """运行 libc-test 模块"""
    name = test_config['name']
    module = test_config.get('module', 'functional')
    mode = test_config.get('mode', 'dynamic')
    
    libc_dir = Path(test_dir) / "libc-test"
    run_script = libc_dir / "run"
    
    if not run_script.exists():
        return {"name": name, "status": "error", "output": f"Run script not found: {run_script}"}
    
    try:
        start = time.time()
        # 运行: ./run src/functional dynamic
        result = subprocess.run(
            [str(run_script), f"src/{module}", mode],
            cwd=str(libc_dir),
            capture_output=True,
            text=True,
            timeout=600
        )
        duration = time.time() - start
        
        # 解析输出获取统计
        output = result.stdout + result.stderr
        status = "pass" if result.returncode == 0 else "fail"
        
        return {"name": name, "status": status, "duration": duration, "output": output}
    except subprocess.TimeoutExpired:
        return {"name": name, "status": "fail", "output": "Timeout (600s)"}
    except Exception as e:
        return {"name": name, "status": "error", "output": str(e)}


# ==================== 主程序 ====================
def main():
    parser = argparse.ArgumentParser(description='StarryOS Test Runner')
    parser.add_argument('--suite', default='ci', help='Test suite name')
    parser.add_argument('--manifest', default=DEFAULT_MANIFEST, help='Manifest file path')
    parser.add_argument('--test-dir', default=DEFAULT_TEST_DIR, help='Test directory')
    parser.add_argument('--format', choices=['human', 'tap', 'json'], default='human')
    parser.add_argument('--verbose', '-v', action='store_true')
    args = parser.parse_args()
    
    # 加载 manifest
    if not os.path.exists(args.manifest):
        print(f"Error: Manifest not found: {args.manifest}", file=sys.stderr)
        sys.exit(1)
    
    with open(args.manifest) as f:
        manifest = toml.load(f)
    
    # 查找测试套件
    suite = None
    for ts in manifest.get('test_suite', []):
        if ts.get('name') == args.suite:
            suite = ts
            break
    
    if not suite:
        print(f"Error: Suite '{args.suite}' not found", file=sys.stderr)
        sys.exit(1)
    
    if not suite.get('enabled', True):
        print(f"Suite '{args.suite}' is disabled", file=sys.stderr)
        sys.exit(0)
    
    # 输出头部
    print("=" * 50)
    print(f"StarryOS Test Runner - Suite: {args.suite}")
    print(f"Description: {suite.get('description', '')}")
    print("=" * 50)
    print()
    
    # 运行测试
    tests = suite.get('tests', [])
    total, passed, failed, skipped = 0, 0, 0, 0
    
    for test in tests:
        total += 1
        test_type = test.get('type', 'rust')
        
        # 根据类型运行测试
        if test_type == 'rust':
            result = run_rust_test(args.test_dir, test, args.verbose)
        elif test_type == 'libc':
            result = run_libc_test(args.test_dir, test, args.verbose)
        else:
            result = {"name": test['name'], "status": "skip", "output": f"Unknown type: {test_type}"}
        
        # 统计
        if result['status'] == 'pass':
            passed += 1
            status_str = "\033[32m✓ PASS\033[0m"
        elif result['status'] == 'fail':
            failed += 1
            status_str = "\033[31m✗ FAIL\033[0m"
        elif result['status'] == 'skip':
            skipped += 1
            status_str = "\033[33m○ SKIP\033[0m"
        else:
            failed += 1
            status_str = "\033[31m✗ ERROR\033[0m"
        
        # 输出结果
        print(f"Running: {result['name']}")
        print(f"  {status_str}")
        
        if args.verbose and result.get('output'):
            for line in result['output'].splitlines()[:10]:
                print(f"    {line}")
        print()
    
    # 输出统计
    print("=" * 50)
    print("Test Results Summary")
    print("=" * 50)
    print(f"Total:   {total}")
    print(f"Passed:  \033[32m{passed}\033[0m")
    print(f"Failed:  \033[31m{failed}\033[0m")
    if skipped > 0:
        print(f"Skipped: \033[33m{skipped}\033[0m")
    print("=" * 50)
    
    sys.exit(0 if failed == 0 else 1)


if __name__ == '__main__':
    main()
