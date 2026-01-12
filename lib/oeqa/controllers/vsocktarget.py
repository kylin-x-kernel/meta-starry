#
# SPDX-License-Identifier: MIT
#
# StarryOS vsock Target Controller for OEQA

import os
import sys
import time
import glob
import socket
import logging
import subprocess
from collections import defaultdict

from oeqa.core.target import OETarget
from oeqa.utils.qemurunner import QemuRunner
from oeqa.utils.dump import MonitorDumper
from oeqa.utils.dump import TargetDumper

AF_VSOCK = 40


class OEVsockTarget(OETarget):
    """通过 vsock 与 StarryOS 通信的 OEQA Target
    
    工作原理：
    1. QEMU 启动时配置 vhost-vsock-pci，分配 guest CID
    2. StarryOS 中的 vsock-server 监听端口
    3. 宿主机通过 AF_VSOCK 连接
    4. 发送命令，接收输出
    """
    
    def __init__(self, logger, ip=None, server_ip=None, cid=103, port=5555, 
                 timeout=300, **kwargs):
        if not logger:
            logger = logging.getLogger('target')
            logger.setLevel(logging.INFO)

        super(OEVsockTarget, self).__init__(logger)
        self.cid = int(cid) if cid else 103
        self.port = int(port) if port else 5555
        self.timeout = timeout
        self.ip = ip  # 保留以兼容接口

    def start(self, **kwargs):
        pass

    def stop(self, **kwargs):
        pass

    def _connect(self, timeout=None):
        """建立 vsock 连接"""
        if timeout is None:
            timeout = self.timeout
        
        # 最小超时 5 秒，避免 EINPROGRESS 错误
        if timeout < 5:
            timeout = 5
        
        sock = None
        try:
            sock = socket.socket(AF_VSOCK, socket.SOCK_STREAM)
            sock.settimeout(timeout)
            self.logger.debug(f"Connecting to vsock CID={self.cid} PORT={self.port} (timeout={timeout}s)")
            sock.connect((self.cid, self.port))
            return sock
        except Exception as e:
            self.logger.error(f"Failed to connect vsock: {e}")
            # 确保失败时关闭 socket，避免资源泄漏
            if sock:
                try:
                    sock.close()
                except:
                    pass
            return None

    def run(self, command, timeout=None):
        """在 StarryOS 中执行命令"""
        if timeout is None:
            timeout = self.timeout
        
        # 确保最小超时
        if timeout < 10:
            timeout = 10
        
        self.logger.debug(f"[Running]$ {command}")
        starttime = time.time()
        
        # 重试连接（最多 3 次）
        sock = None
        for retry in range(3):
            sock = self._connect(timeout)
            if sock:
                break
            self.logger.warning(f"Connection failed, retry {retry + 1}/3...")
            time.sleep(1)
        
        if not sock:
            return (255, "ERROR: Failed to connect to vsock after 3 retries")
        
        try:
            # 发送命令
            sock.sendall(f"{command}\n".encode('utf-8'))
            
            # 接收输出
            output = b''
            sock.settimeout(timeout)
            
            # 标记是否发生了连接错误
            connection_error = None
            
            while True:
                try:
                    chunk = sock.recv(4096)
                    if not chunk:
                        # 连接关闭
                        break
                    output += chunk
                    # 检查是否收到完整响应（以 EXIT_CODE: 结尾）
                    if b'EXIT_CODE:' in output:
                        break
                except socket.timeout:
                    self.logger.warning("Socket timeout while receiving")
                    break
                except BlockingIOError:
                    # EAGAIN - 继续等待
                    time.sleep(0.1)
                    continue
                except (ConnectionResetError, BrokenPipeError) as e:
                    # 连接被重置或管道断裂 - StarryOS 可能崩溃了
                    connection_error = f"Connection lost: {e}"
                    self.logger.error(connection_error)
                    break
                except Exception as e:
                    connection_error = f"Unexpected error: {e}"
                    self.logger.error(f"Error receiving: {e}")
                    break
            
            # 解析输出和退出码
            output_str = output.decode('utf-8', errors='ignore')
            lines = output_str.strip().split('\n')
            
            # 如果发生连接错误且没有收到 EXIT_CODE，返回特殊错误码
            if connection_error and b'EXIT_CODE:' not in output:
                return (254, f"ERROR: {connection_error}\nPartial output: {output_str}")
            
            status = 0
            if lines and 'EXIT_CODE:' in lines[-1]:
                try:
                    status = int(lines[-1].split(':')[1].strip())
                    output_str = '\n'.join(lines[:-1])
                except:
                    pass
            
            elapsed = time.time() - starttime
            self.logger.debug(f"[Command returned '{status}' after {elapsed:.2f}s]")
            
            return (status, output_str)
            
        except Exception as e:
            self.logger.error(f"Error running command: {e}")
            return (255, f"ERROR: {e}")
        finally:
            try:
                sock.close()
            except:
                pass

    def copyTo(self, localSrc, remoteDst):
        """复制文件到目标"""
        self.logger.warning("copyTo not fully implemented for vsock")
        return (0, "")

    def copyFrom(self, remoteSrc, localDst, warn_on_failure=False):
        """从目标复制文件"""
        self.logger.warning("copyFrom not fully implemented for vsock")
        return (0, "")


class OEQemuVsockTarget(OEVsockTarget):
    """QEMU + vsock 的 OEQA Target
    
    结合 QemuRunner 启动 QEMU，通过 vsock 通信
    """
    
    supported_fstypes = ['ext3', 'ext4', 'cpio.gz', 'wic']
    
    def __init__(self, logger, ip=None, server_ip=None, timeout=300,
            machine='', rootfs='', kernel='', kvm=False, slirp=False,
            dump_dir='', dump_host_cmds='', display='', bootlog='',
            tmpdir='', dir_image='', boottime=60, serial_ports=2,
            boot_patterns=defaultdict(str), ovmf=False, tmpfsdir=None, 
            **kwargs):

        cid = kwargs.pop('cid', 103)
        port = kwargs.pop('port', 5555)
        
        super(OEQemuVsockTarget, self).__init__(logger, ip, server_ip, 
                                                 cid, port, timeout)

        self.server_ip = server_ip
        self.machine = machine
        self.rootfs = rootfs
        self.kernel = kernel
        self.kvm = kvm
        self.ovmf = ovmf
        self.use_slirp = slirp
        self.boot_patterns = boot_patterns
        self.dump_dir = dump_dir
        self.bootlog = bootlog

        vsock_boot_patterns = boot_patterns.copy() if boot_patterns else defaultdict(str)
        vsock_boot_patterns['search_reached_prompt'] = b'StarryOS'
        vsock_boot_patterns['search_login_succeeded'] = b'root@starry'
        vsock_boot_patterns['send_login_user'] = b''  
        
        self.runner = QemuRunner(machine=machine, rootfs=rootfs, tmpdir=tmpdir,
                                 deploy_dir_image=dir_image, display=display,
                                 logfile=bootlog, boottime=boottime,
                                 use_kvm=kvm, use_slirp=slirp, dump_dir=dump_dir,
                                 dump_host_cmds=dump_host_cmds, logger=logger,
                                 serial_ports=serial_ports, boot_patterns=vsock_boot_patterns, 
                                 use_ovmf=ovmf, tmpfsdir=tmpfsdir)
        
        dump_monitor_cmds = kwargs.get("testimage_dump_monitor")
        self.monitor_dumper = MonitorDumper(dump_monitor_cmds, dump_dir, self.runner)
        if self.monitor_dumper:
            self.monitor_dumper.create_dir("qmp")

        dump_target_cmds = kwargs.get("testimage_dump_target")
        self.target_dumper = TargetDumper(dump_target_cmds, dump_dir, self.runner)
        self.target_dumper.create_dir("qemu")

    def start(self, params=None, extra_bootparams=None, runqemuparams=''):

        self.logger.info("Starting QEMU with vsock support (IP detection disabled)...")
        
        self.runner.ip = "10.0.2.15"
        
        result = self.runner.start(params, get_ip=False, 
                                   extra_bootparams=extra_bootparams, 
                                   runqemuparams=runqemuparams)
        
        if result:
            self.logger.info("QEMU started successfully")
        else:
            self.logger.warning("QemuRunner returned False, checking if QEMU is alive...")
            
            # 检查 QEMU 进程
            qemu_alive = False
            if hasattr(self.runner, 'qemupid') and self.runner.qemupid:
                try:
                    os.kill(self.runner.qemupid, 0)
                    qemu_alive = True
                    self.logger.info(f"QEMU process (PID {self.runner.qemupid}) is running")
                except:
                    pass
            
            if not qemu_alive:
                raise RuntimeError("FAILED to start QEMU - process not running")
        

        self.logger.info(f"Waiting for vsock service on CID={self.cid} PORT={self.port}...")
        retry_count = 0
        max_retries = 90 
        
        while retry_count < max_retries:
            sock = self._connect(timeout=1)
            if sock:
                sock.close()
                self.logger.info("vsock service is ready!")
                return True
            
            retry_count += 1
            time.sleep(1)
            if retry_count % 10 == 0:
                self.logger.info(f"Still waiting for vsock... ({retry_count}s)")
        
        self.logger.error(f"vsock service not ready after {max_retries}s")
        self.stop()
        raise RuntimeError("FAILED to connect to vsock service - is vsock-server running in StarryOS?")

    def stop(self):
        """停止 QEMU"""
        self.logger.info("Stopping QEMU...")
        self.runner.stop()

