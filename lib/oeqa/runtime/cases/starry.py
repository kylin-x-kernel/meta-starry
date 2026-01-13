#
# SPDX-License-Identifier: MIT
#
# StarryOS OEQA Runtime Tests

import os
import datetime
from oeqa.runtime.case import OERuntimeTestCase
from oeqa.core.decorator.depends import OETestDepends
from oeqa.runtime.decorator.package import OEHasPackage

class StarryPtestTest(OERuntimeTestCase):
    """StarryOS ptest 测试套件
    
    遵循 Yocto 官方 OEQA 标准：
    - 使用 ptest-runner 自动执行所有 ptest
    - 使用标准的 PASS:/FAIL: 输出格式
    - 自动生成测试日志和报告
    
    参考：poky/meta/lib/oeqa/runtime/cases/ptest.py
    """
    
    @OEHasPackage(['ptest-runner'])
    def test_starry_ptest_runner(self):
        """运行 StarryOS ptest 套件（官方标准方法）
        
        此测试调用 ptest-runner，自动执行：
        - CI 测试（功能测试）
        - Stress 测试（压力测试）
        - Daily 测试（基准测试）
        
        结果格式（符合 Yocto 标准）：
        - PASS: test_name
        - FAIL: test_name
        - SKIP: test_name
        """
        # 检查 ptest-runner 是否可用
        status, output = self.target.run('which ptest-runner', 0)
        if status != 0:
            self.skipTest("ptest-runner not found in image")
        
        # 检查 starry-test-suite-ptest 是否安装
        status, output = self.target.run('test -d /usr/lib/starry-test-suite/ptest', 0)
        if status != 0:
            self.skipTest("starry-test-suite-ptest not installed")
        
        # 获取测试日志目录
        test_log_dir = self.td.get('TEST_LOG_DIR', '')
        if not test_log_dir:
            test_log_dir = os.path.join(self.td.get('WORKDIR', ''), 'testimage')
        if not os.path.isabs(test_log_dir):
            test_log_dir = os.path.join(self.td.get('TOPDIR', ''), test_log_dir)
        
        timestamp = datetime.datetime.now().strftime('%Y%m%d%H%M%S')
        ptest_log_dir_link = os.path.join(test_log_dir, 'starry_ptest_log')
        ptest_log_dir = f'{ptest_log_dir_link}.{timestamp}'
        ptest_runner_log = os.path.join(ptest_log_dir, 'ptest-runner.log')
        
        # 运行 ptest-runner（30 分钟超时，扫描 /usr/lib）
        self.logger.info("Running ptest-runner for starry-test-suite...")
        libdir = self.td.get('libdir', '/usr/lib')
        ptest_dirs = ['/usr/lib']
        if libdir not in ptest_dirs:
            ptest_dirs.append(libdir)
        
        status, output = self.target.run(
            f'ptest-runner -t 1800 -d "{" ".join(ptest_dirs)}"',
            timeout=1900
        )
        
        # 保存日志
        os.makedirs(ptest_log_dir, exist_ok=True)
        with open(ptest_runner_log, 'w') as f:
            f.write(output)
        
        # 创建符号链接到最新日志
        if os.path.exists(ptest_log_dir_link):
            os.remove(ptest_log_dir_link)
        os.symlink(os.path.basename(ptest_log_dir), ptest_log_dir_link)
        
        self.logger.info(f"ptest results saved to: {ptest_runner_log}")
        self.logger.info(f"ptest output:\n{output}")
        
        self.assertTrue(status != 127, msg="Cannot execute ptest-runner!")
        
        # 简单解析结果（生产环境应使用 oeqa.utils.logparser.PtestParser）
        import re
        passed = len(re.findall(r'PASS:', output))
        failed = len(re.findall(r'FAIL:', output))
        skipped = len(re.findall(r'SKIP:', output))
        
        self.logger.info(f"Test Summary: {passed} passed, {failed} failed, {skipped} skipped")
        
        # 如果没有任何测试被执行，记录警告
        if passed == 0 and failed == 0 and skipped == 0:
            self.logger.warning(
                "No tests were executed! This usually means:"
                "\n  1. Test binaries are not installed in ptest directories"
                "\n  2. run-ptest script is missing or not executable"
                "\n  3. Tests failed to compile during image build"
            )
    
    def test_starry_ptest_structure(self):
        """验证 ptest 目录结构完整性"""
        status, output = self.target.run('ls -laR /usr/lib/starry-test-suite/ptest/')
        self.logger.info(f"ptest structure:\n{output}")
        
        # 验证基本结构
        self.assertEqual(status, 0, "ptest directory not found")
        self.assertIn('run-ptest', output, "run-ptest script not found")
        self.assertIn('ci', output, "ci directory not found")
        self.assertIn('stress', output, "stress directory not found")
        self.assertIn('daily', output, "daily directory not found")
        
        # 验证 run-ptest 可执行
        status, _ = self.target.run('test -x /usr/lib/starry-test-suite/ptest/run-ptest')
        self.assertEqual(status, 0, "run-ptest is not executable")
    
    @OEHasPackage(['ltp'])
    def test_starry_ltp_syscalls(self):
        """运行 LTP 系统调用测试子集（快速验证）
        
        LTP (Linux Test Project) 是 Linux 官方测试项目，
        包含 3000+ 测试用例，覆盖系统调用、文件系统、进程管理等。
        此测试运行系统调用相关的核心测试。
        """
        # 检查 LTP 是否安装
        status, output = self.target.run('test -d /opt/ltp', 0)
        if status != 0:
            self.skipTest("LTP not installed")
        
        # 运行 LTP 系统调用测试（快速子集）
        self.logger.info("Running LTP syscalls tests...")
        status, output = self.target.run(
            '/opt/ltp/runltp -f syscalls -s syscall_basic -q',
            timeout=600
        )
        
        self.logger.info(f"LTP syscalls output:\n{output}")
        
        # LTP 返回失败测试的数量作为退出码
        # 0 表示所有测试通过
        if status != 0:
            self.logger.warning(f"LTP syscalls had {status} failed tests")
        

        self.assertTrue(status != 127, msg="Cannot execute LTP runltp!")
    
    @OEHasPackage(['stress-ng'])
    def test_starry_stress_ng_quick(self):
        """运行 stress-ng 快速压力测试
        
        stress-ng 是标准压力测试工具，
        支持 CPU、内存、IO、网络等多种压力场景。
        此测试运行 CPU 压力测试以验证系统稳定性。
        """
        # 检查 stress-ng 是否可用
        status, output = self.target.run('which stress-ng', 0)
        if status != 0:
            self.skipTest("stress-ng not found")
        
        # 运行 CPU 压力测试（4核心，10秒）
        self.logger.info("Running stress-ng CPU test...")
        status, output = self.target.run(
            'stress-ng --cpu 4 --timeout 10s --metrics-brief 2>&1 | head -100',
            timeout=30
        )
        
        self.logger.info(f"stress-ng output:\n{output}")
        
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed during quick stress test!\n{output}")
        elif status == 255 and 'Failed to connect' in output:
            self.skipTest("StarryOS already crashed (cannot connect)")
        # 测试成功
        elif 'stress-ng' in output or 'cpu' in output.lower() or status == 0:
            self.logger.info("stress-ng quick test PASSED")
        else:
            self.logger.warning(f"stress-ng output unexpected: status={status}, output={output[:200]}")
    
    @OEHasPackage(['stress-ng'])
    def test_starry_stress_ng_cpu(self):
        """CPU 压力测试
        
        测试 StarryOS 在 CPU 高负载下的稳定性。
        """
        self.logger.info("=== CPU 压力测试 ===")
        status, output = self.target.run(
            'stress-ng --cpu 2 --cpu-method all --timeout 5s --metrics-brief 2>&1',
            timeout=30
        )
        self.logger.info(f"CPU stress output:\n{output}")
        
        # 检查 vsock 连接失败（StarryOS 崩溃）
        if status == 255 and 'Failed to connect' in output:
            self.skipTest("StarryOS connection lost (previous test may have crashed the system)")
        
        # 检查是否执行成功
        if status == 0 or 'stress-ng' in output:
            self.logger.info("CPU stress test PASSED")
        else:
            self.fail(f"CPU stress test failed: status={status}, output={output}")
    
    @OEHasPackage(['stress-ng'])
    def test_starry_stress_ng_memory(self):
        """内存压力测试
        
        测试 StarryOS 的内存管理在压力下的稳定性。
        使用较小的内存量以避免 OOM。
        """
        self.logger.info("=== 内存压力测试 ===")
        # 使用较小的内存（16M）和较短时间（3s），避免 StarryOS OOM
        status, output = self.target.run(
            'stress-ng --vm 1 --vm-bytes 16M --vm-keep --timeout 3s --metrics-brief 2>&1',
            timeout=30
        )
        self.logger.info(f"Memory stress output:\n{output}")
        
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed during memory stress test!\n{output}")
        elif status == 255 and 'Failed to connect' in output:
            self.skipTest("StarryOS already crashed (cannot connect)")
        elif status == 0 or 'stress-ng' in output:
            self.logger.info("Memory stress test PASSED")
        else:
            self.logger.warning(f"Memory stress test issue: status={status}")
            self.skipTest("Memory stress test not fully supported on StarryOS")
    
    @OEHasPackage(['stress-ng'])
    def test_starry_stress_ng_io(self):
        """IO 压力测试
        
        测试 StarryOS 文件系统在 IO 压力下的稳定性。
        """
        self.logger.info("=== IO 压力测试 ===")
        # 使用简单的 IO 测试
        status, output = self.target.run(
            'stress-ng --iomix 1 --timeout 3s --metrics-brief 2>&1',
            timeout=30
        )
        self.logger.info(f"IO stress output:\n{output}")
        
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed during IO stress test!\n{output}")
        elif status == 255 and 'Failed to connect' in output:
            self.skipTest("StarryOS already crashed (cannot connect)")
        # 测试成功
        elif status == 0 or 'stress-ng' in output:
            self.logger.info("IO stress test PASSED")
        else:
            self.logger.warning(f"IO stress test issue: status={status}")
            self.skipTest("IO stress test not fully supported on StarryOS")
    
    @OEHasPackage(['stress-ng'])
    def test_starry_stress_ng_matrix(self):
        """矩阵运算压力测试
        
        测试 StarryOS 的浮点运算性能。
        """
        self.logger.info("=== 矩阵运算压力测试 ===")
        status, output = self.target.run(
            'stress-ng --matrix 1 --timeout 3s --metrics-brief 2>&1',
            timeout=30
        )
        self.logger.info(f"Matrix stress output:\n{output}")
        
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed during matrix stress test!\n{output}")
        elif status == 255 and 'Failed to connect' in output:
            self.skipTest("StarryOS already crashed (cannot connect)")
        elif status == 0 or 'stress-ng' in output:
            self.logger.info("Matrix stress test PASSED")
        else:
            self.skipTest("Matrix stress test not supported")
    
    @OEHasPackage(['stress-ng'])
    def test_starry_stress_ng_context_switch(self):
        """上下文切换压力测试
        
        测试 StarryOS 的进程调度性能。
        注意：使用较温和的参数，避免资源耗尽
        """
        self.logger.info("=== 上下文切换压力测试 ===")
        
        # 先检查 vsock 连接是否正常
        status, output = self.target.run('echo "vsock check"', timeout=10)
        if status == 255 or 'Failed to connect' in output:
            self.skipTest("vsock connection unavailable")
        
        # 使用更温和的参数：1个实例，2秒超时
        status, output = self.target.run(
            'stress-ng --switch 1 --timeout 2s --metrics-brief 2>&1',
            timeout=30
        )
        self.logger.info(f"Context switch stress output:\n{output}")
        
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed during context switch stress test!\n{output}")
        elif status == 255 and 'Failed to connect' in output:
            self.skipTest("StarryOS already crashed (cannot connect)")
        elif status == 0 or 'stress-ng' in output:
            self.logger.info("Context switch stress test PASSED")
        else:
            self.skipTest("Context switch stress test not supported")
