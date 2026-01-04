# Yocto 团队协作与缓存共享方案

## 概述

Yocto 官方提供了多种机制来加速团队协作和重复构建，本文档介绍如何在 StarryOS 项目中使用这些机制。

---

## 1. 核心概念

### DL_DIR（下载目录）
- **作用**：存储所有下载的源码包、补丁等
- **加速原理**：避免重复下载相同的文件
- **典型大小**：5-10 GB

### SSTATE_DIR（共享状态缓存）
- **作用**：存储所有构建任务的中间结果
- **加速原理**：BitBake 为每个任务生成唯一 hash，hash 匹配时直接复用缓存
- **典型大小**：50-100 GB
- **加速效果**：10-20 倍（首次构建 40 分钟 → 二次构建 2-5 分钟）

### TMPDIR（临时构建目录）
- **作用**：当前构建的工作目录
- **注意**：**不应共享**，每个开发者应独立

---

## 2. 团队协作配置方案


### 方案 A：本地独立构建

**适用场景**：
- 个人开发
- 离线环境
- 学习测试，建议可以试着先本地独立构建，熟悉流程，在build/conf/local.conf里设置并行构建数量多点，时间也不算很长，因为仅构建内核。后续开发结束即稳定后再复用sstate以确保生产环境一致性。

**配置**：
```bash
# conf/local.conf

# TMPDIR 保持默认（build/tmp-baremetal）
```

**优点**：配置简单，无需网络
**缺点**：无法与团队共享缓存

---

### 方案 B：团队共享 NFS，目前还没配置

**适用场景**：
- 团队开发（5-50 人）
- 内网环境
- 需要最大化构建速度

**配置**：
```bash
# conf/local.conf

# 团队共享目录（NFS 挂载）
DL_DIR = "/mnt/yocto-shared/downloads"
SSTATE_DIR = "/mnt/yocto-shared/sstate-cache"

# TMPDIR 保持默认
```

**NFS 服务器端配置**：
```bash
# /etc/exports
/yocto-shared *(rw,sync,no_subtree_check,no_root_squash)

# 重启 NFS 服务
sudo exportfs -ra
sudo systemctl restart nfs-server
```

**客户端挂载**：
```bash
# 临时挂载
sudo mount -t nfs -o vers=4 server:/yocto-shared /mnt/yocto-shared

# 永久挂载（添加到 /etc/fstab）
server:/yocto-shared /mnt/yocto-shared nfs defaults 0 0
```

**权限设置**：
```bash
# 方式 1：使用 nobody:nogroup（简单）
sudo chown -R nobody:nogroup /yocto-shared
sudo chmod -R 775 /yocto-shared

# 方式 2：使用团队组
sudo groupadd yocto-team
sudo usermod -aG yocto-team user1
sudo usermod -aG yocto-team user2
sudo chgrp -R yocto-team /yocto-shared
sudo chmod -R 2775 /yocto-shared  # setgid 确保新文件继承组
```

**优点**：
- 团队成员共享缓存，构建极快（2-5 分钟）
- 节省磁盘空间（不需要每人一份）

**缺点**：
- 需要网络稳定
- 需要管理员配置 NFS

---

## 4. sstate-cache 管理

### 查看使用情况
```bash
# 查看总大小
du -sh /path/to/sstate-cache

# 查看文件数
find /path/to/sstate-cache -type f | wc -l
```

### 清理旧缓存（建议每月执行）
```bash
# 进入 poky 目录
cd ~/starry-workspace/poky

# 清理重复的缓存
./scripts/sstate-cache-management.sh \
    --cache-dir=/mnt/yocto-shared/sstate-cache \
    --remove-duplicated \
    --yes

# 按时间清理（保留最近 30 天）
./scripts/sstate-cache-management.sh \
    --cache-dir=/mnt/yocto-shared/sstate-cache \
    --stamps-dir=../build/tmp-baremetal/stamps \
    --remove-duplicated \
    --yes
```

### 定期自动清理（cron）
```bash
# 每周日凌晨 2 点清理
0 2 * * 0 cd /path/to/poky && ./scripts/sstate-cache-management.sh --cache-dir=/mnt/yocto-shared/sstate-cache --remove-duplicated --yes
```

---

## 5. 常见问题

### Q: sstate-cache 会一直增长吗？
**A**: 是的。建议：
- 定期使用清理脚本
- 设置磁盘配额
- 或者手动删除旧的平台缓存

### Q: 不同 MACHINE 的 sstate 可以共享吗？
**A**: 可以！sstate 的 hash 包含了目标架构信息，不同 `MACHINE` 的缓存互不冲突，可以共存。

### Q: 修改了 bbclass 后，sstate 还有效吗？
**A**: 无效。BitBake 会检测到依赖变化，重新构建受影响的任务。

### Q: 为什么我的 sstate 没有生效？
**A**: 检查：
1. 路径是否正确（`bitbake -e | grep SSTATE_DIR`）
2. 权限是否足够（ls -ld 检查）
3. `MACHINE` 是否一致
4. 是否修改了配置或依赖

### Q: NFS 挂载很慢怎么办？
**A**: 优化：
- 使用 `async` 挂载选项（`mount -o async`）
- 调整 `rsize/wsize`（`mount -o rsize=32768,wsize=32768`）
- 检查网络带宽和延迟

### Q: DL_DIR 需要备份吗？
**A**: 建议备份。但即使丢失，BitBake 会自动重新下载。

---


