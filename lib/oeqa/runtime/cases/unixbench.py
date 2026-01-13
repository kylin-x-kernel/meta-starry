#
# SPDX-License-Identifier: MIT
#
# StarryOS UnixBench Benchmark Test

from oeqa.runtime.case import OERuntimeTestCase
from oeqa.runtime.decorator.package import OEHasPackage

class UnixBenchTest(OERuntimeTestCase):
    """StarryOS UnixBench benchmark test suite"""
    
    @OEHasPackage(['unixbench'])
    def test_unixbench(self):
        """Run UnixBench system benchmark
        
        UnixBench is a comprehensive benchmark suite for Unix systems,
        testing CPU, memory, file I/O, and process creation performance.
        """
        self.logger.info("=== UnixBench System Benchmark ===")
        
        # Check if run-unixbench exists
        status, output = self.target.run('which run-unixbench', timeout=10)
        if status != 0:
            self.skipTest(f"run-unixbench not found in PATH: {output}")
        
        self.logger.info(f"run-unixbench found at: {output.strip()}")
        
        # Run UnixBench (full output logged for analysis)
        status, output = self.target.run(
            'run-unixbench 2>&1',
            timeout=1800
        )
        
        self.logger.info(f"UnixBench status={status}, output length={len(output)}")
        self.logger.info(f"UnixBench output:\n{output}")
        
        # Check for crashes
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed during UnixBench!\n{output}")
        
        # Check for command not found
        elif status == 127:
            self.skipTest(f"UnixBench command not found (status=127): {output}")
        
        # Success: exit code 0 or benchmark score present
        elif status == 0 or 'System Benchmarks Index Score' in output:
            self.logger.info("✅ UnixBench PASSED")
        
        # Unexpected failure
        else:
            self.fail(f"UnixBench failed (status={status}):\n{output}")

