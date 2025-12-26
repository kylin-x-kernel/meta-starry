LICENSE = "CLOSED"
LIC_FILES_CHKSUM = ""

S = "${WORKDIR}"

INHIBIT_DEFAULT_DEPS = "1"

do_install() {
    install -d ${D}${bindir}
    cat > ${D}${bindir}/axconfig-gen <<'EOF'
#!/usr/bin/env python3
import sys, shutil, os, re

if "--version" in sys.argv:
    print("axconfig-gen 0.0.0")
    sys.exit(0)

def parse_toml_simple(path, field):
    """简单的 TOML 解析器，只读取顶层字段"""
    with open(path, "r") as f:
        for line in f:
            line = line.strip()
            if not line or line.startswith("#"):
                continue
            match = re.match(rf'{field}\s*=\s*"([^"]*)"', line)
            if match:
                return match.group(1)
    return ""

def main():
    args = sys.argv[1:]
    if "-o" in args: 
        try:
            dest = args[args.index("-o") + 1]
        except IndexError:
            sys.exit(1)
        src = None
        for a in args:
            if a.endswith(".toml") and os.path.exists(a):
                src = a
                break
        if src:
            # 复制源文件并处理 -w 参数（写入覆盖）
            with open(src, "r") as f:
                content = f.read()
            
            # 解析 -w key=value 参数
            i = 0
            while i < len(args):
                if args[i] == "-w" and i + 1 < len(args):
                    kv = args[i + 1]
                    if "=" in kv:
                        key, value = kv.split("=", 1)
                        # 移除value中可能已存在的引号
                        value = value.strip('"\'')
                        # 简单替换：在文件末尾添加或更新字段
                        pattern = rf'^{re.escape(key)}\s*=.*$'
                        new_line = f'{key} = "{value}"'
                        if re.search(pattern, content, re.MULTILINE):
                            content = re.sub(pattern, new_line, content, flags=re.MULTILINE)
                        else:
                            content += f'\n{new_line}\n'
                i += 1
            
            with open(dest, "w") as f:
                f.write(content)
    if "-r" in args:
        idx = args.index("-r")
        try:
            field = args[idx + 1]
        except IndexError:
            sys.exit(1)
        path = None
        for a in args:
            if a.endswith(".toml") and os.path.exists(a):
                path = a
                break
        if path:
            try:
                value = parse_toml_simple(path, field)
                print(f'"{value}"')
            except Exception:
                pass

if __name__ == "__main__":
    main()
EOF
    chmod +x ${D}${bindir}/axconfig-gen
}

BBCLASSEXTEND = "native nativesdk"
