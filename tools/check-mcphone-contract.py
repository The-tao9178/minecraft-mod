#!/usr/bin/env python3
"""MCphone 联动契约自检。

MCphone 给我们用的只有 `com.november.mcphone.api.*` 这一层公开接口。
这个脚本把「我们是不是只用这一层」变成一条能反复跑的命令，MCphone 以后更新了，
先跑它，再看一眼输出，就知道联动有没有踩到人家的内部实现。

检查四件事：
  1. 源码里 import 的 com.november 类，必须都在 api 包下；
  2. 源码里没有拿字符串去反射 MCphone 的内部类 / 内部字段；
  3. SPI 名单文件存在，写的是我们自己的 App 类；
  4. 打出来的 jar 里不能有 MCphone 的类（compileOnly 才有意义）。

用法：python3 tools/check-mcphone-contract.py
"""

from __future__ import annotations

import json
import re
import sys
import zipfile
from pathlib import Path

ROOT = Path(__file__).resolve().parent.parent
SRC = ROOT / "src" / "main" / "java"
RESOURCES = ROOT / "src" / "main" / "resources"
SPI_FILE = RESOURCES / "META-INF" / "services" / "com.november.mcphone.api.client.app.IPhoneApp"

# 只允许这个前缀：mcphone 公开给第三方开发者的接口层
API_PREFIX = "com.november.mcphone.api."

# 出现在字符串里就说明在摸它的内部实现（这些包名不在 api 层）
INTERNAL_MARKERS = (
    "com.november.mcphone.client",
    "com.november.mcphone.phone",
    "com.november.mcphone.book",
    "com.november.mcphone.game",
    "PhonePaths",
    "BookSources",
    "TxtChapters",
)

errors: list[str] = []
notes: list[str] = []


def check_imports() -> None:
    import_re = re.compile(r"^\s*import\s+(static\s+)?(com\.november\.[\w.]+)", re.MULTILINE)
    total = 0
    for path in sorted(SRC.rglob("*.java")):
        text = path.read_text(encoding="utf-8")
        for match in import_re.finditer(text):
            name = match.group(2)
            total += 1
            if not name.startswith(API_PREFIX):
                errors.append(f"{path.relative_to(ROOT)} 引用了非 api 包：{name}")
    notes.append(f"import com.november.*：{total} 处，全部在 api 包下" if not errors else f"import 共 {total} 处")


def strip_comments(text: str) -> str:
    """去掉注释再扫：注释里提到内部类名是在解释「为什么不用它」，不算依赖。"""
    text = re.sub(r"/\*.*?\*/", "", text, flags=re.DOTALL)
    return re.sub(r"//[^\n]*", "", text)


def check_internal_strings() -> None:
    for path in sorted(SRC.rglob("*.java")):
        text = strip_comments(path.read_text(encoding="utf-8"))
        for marker in INTERNAL_MARKERS:
            if marker in text:
                errors.append(f"{path.relative_to(ROOT)} 里出现了内部实现的名字：{marker}")


def check_spi() -> None:
    if not SPI_FILE.is_file():
        errors.append(f"缺少 SPI 名单：{SPI_FILE.relative_to(ROOT)}")
        return
    lines = [line.strip() for line in SPI_FILE.read_text(encoding="utf-8").splitlines() if line.strip()]
    if not lines:
        errors.append("SPI 名单是空的")
        return
    for line in lines:
        if not line.startswith("cn.blockforge.generated.sharedstockexchange."):
            errors.append(f"SPI 名单里不是本模组的类：{line}")
    notes.append(f"SPI 注册：{', '.join(lines)}")


def check_mixin_config() -> None:
    config = RESOURCES / "shared_stock_exchange.mixins.json"
    if not config.is_file():
        return
    text = config.read_text(encoding="utf-8")
    data = json.loads(text)
    for key in ("mixins", "client", "server"):
        for entry in data.get(key, []):
            if "mcphone" in entry.lower():
                errors.append(f"Mixin 配置里有 MCphone 的补丁：{entry}")
    notes.append("Mixin：没有针对 MCphone 的补丁")


def check_jar() -> None:
    jars = sorted((ROOT / "build" / "libs").glob("*.jar"))
    if not jars:
        notes.append("还没构建，跳过 jar 检查")
        return
    for jar in jars:
        with zipfile.ZipFile(jar) as archive:
            bad = [n for n in archive.namelist() if n.startswith("com/november/mcphone/")]
            if bad:
                errors.append(f"{jar.name} 里打进了 MCphone 的类：{bad[:3]}")
        notes.append(f"{jar.name}：没有 MCphone 的类" if not bad else f"{jar.name}：有 MCphone 的类")


def main() -> int:
    check_imports()
    check_internal_strings()
    check_spi()
    check_mixin_config()
    check_jar()

    for note in notes:
        print(f"  · {note}")
    if errors:
        print("\n发现问题：")
        for error in errors:
            print(f"  ✗ {error}")
        return 1
    print("\n契约自检通过：只用了 MCphone 的 api 层，没有反射 / Mixin 它的内部实现。")
    return 0


if __name__ == "__main__":
    sys.exit(main())
