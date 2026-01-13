#
# SPDX-License-Identifier: MIT
#
# StarryOS CI Functional Tests

from oeqa.runtime.case import OERuntimeTestCase
from oeqa.runtime.decorator.package import OEHasPackage

class CITest(OERuntimeTestCase):
    """StarryOS CI functional tests"""
    
    def _run_ci_test(self, test_name, timeout=30):
        """Helper method to run CI test and handle results"""
        self.logger.info(f"=== CI: {test_name} ===")
        
        status, output = self.target.run(
            f'/usr/lib/starry-ci/{test_name}',
            timeout=timeout
        )
        
        self.logger.info(f"{test_name}: {output}")
        
        if status == 254 or 'Connection lost' in output:
            self.fail(f"StarryOS crashed: {output}")
        elif status == 255 and 'Failed to connect' in output:
            self.skipTest("Connection lost")
        elif status == 0:
            self.logger.info(f"{test_name} PASSED")
        else:
            self.fail(f"{test_name} failed: {output}")
    
    @OEHasPackage(['starry-ci-tests'])
    def test_ci_file_io_basic(self):
        self._run_ci_test('file_io_basic')
    
    @OEHasPackage(['starry-ci-tests'])
    def test_ci_multi_processors(self):
        self._run_ci_test('multi_processors')
    
    @OEHasPackage(['starry-ci-tests'])
    def test_ci_process_spawn(self):
        self._run_ci_test('process_spawn')

