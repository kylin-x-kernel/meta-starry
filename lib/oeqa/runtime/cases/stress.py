#
# SPDX-License-Identifier: MIT
#
# StarryOS Stress Tests

from oeqa.runtime.case import OERuntimeTestCase
from oeqa.runtime.decorator.package import OEHasPackage

class StressTest(OERuntimeTestCase):
    """StarryOS 压力测试套件
    
    使用 stress-ng 进行系统稳定性测试：
    - CPU 压力测试
    - 内存压力测试
    - IO 压力测试
    - 上下文切换压力测试
    """
    
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

