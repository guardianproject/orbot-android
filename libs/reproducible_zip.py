#!/usr/bin/env python3
# Usage: python3 reproducible_zip.py src_dir out.zip
from pathlib import Path
from zipfile import ZipFile, ZipInfo, ZIP_STORED, ZIP_DEFLATED
from datetime import datetime, timezone
import sys

def make_zipinfo(path: Path, arcname: str, fixed_dt: datetime):
    zi = ZipInfo(arcname)
    zi.date_time = (fixed_dt.year, fixed_dt.month, fixed_dt.day,
                    fixed_dt.hour, fixed_dt.minute, fixed_dt.second)
    zi.extra = b""
    zi.comment = b""
    zi.external_attr = (path.stat().st_mode & 0o777) << 16
    return zi

def main(src, out, fixed_ts=(2000,1,1,0,0,0), compression=ZIP_DEFLATED, compresslevel=9):
    src = Path(src)
    files = sorted([p for p in src.rglob("*") if p.is_file()])
    fixed_dt = datetime(*fixed_ts, tzinfo=timezone.utc)
    with ZipFile(out, "w", compression=compression, compresslevel=compresslevel) as zf:
        for p in files:
            arc = str(p.relative_to(src)).replace("\\", "/")
            zi = make_zipinfo(p, arc, fixed_dt)
            data = p.read_bytes()
            zf.writestr(zi, data)

if __name__ == "__main__":
    if len(sys.argv) != 3:
        print("Usage: reproducible_zip.py SRC_DIR OUT_ZIP"); sys.exit(2)
    main(sys.argv[1], sys.argv[2])
