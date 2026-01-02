# Workaround for user namespace permission issues
# Globally disable network isolation

python () {
    # 方法1: 为所有任务添加network标志
    # 获取所有任务
    tasks = [e for e in d.keys() if e.startswith('do_')]
    for task in tasks:
        d.setVarFlag(task, 'network', '1')
}

# 方法2: 完全禁用网络隔离检查（备用）
BB_TASK_IONICE_LEVEL = ""
