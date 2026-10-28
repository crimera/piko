#!/usr/bin/env python3
"""Convert an APK's binary baseline profile into ART human-readable profile rules.

Patching rewrites and re-splits the dex files, so the APK's assets/dexopt/baseline.prof (keyed by
dex CRC32 and method index) no longer matches anything. Rules name classes and methods by
descriptor, so they survive patching; ART's profman turns them back into a binary profile for the
patched APK on the device.

Usage: baseline_profile_rules.py ORIGINAL.apk|.apkm|.apks > rules.txt
"""
import io
import struct
import sys
import zipfile
import zlib

PROFILE_PATH = "assets/dexopt/baseline.prof"
MAGIC = b"pro\x00"
VERSION_P = b"010\x00"


class Reader:
    def __init__(self, data, offset=0):
        self.data = data
        self.offset = offset

    def take(self, fmt):
        values = struct.unpack_from("<" + fmt, self.data, self.offset)
        self.offset += struct.calcsize("<" + fmt)
        return values if len(values) > 1 else values[0]

    def bytes(self, size):
        value = self.data[self.offset:self.offset + size]
        self.offset += size
        return value


def uleb128(data, offset):
    result = shift = 0
    while True:
        byte = data[offset]
        offset += 1
        result |= (byte & 0x7F) << shift
        if byte < 0x80:
            return result, offset
        shift += 7


class Dex:
    """Just enough of the dex format to name type_ids and method_ids."""

    def __init__(self, data):
        self.data = data
        (string_count, string_offset, type_count, type_offset, proto_count, proto_offset,
         _, _, method_count, method_offset) = struct.unpack_from("<10I", data, 0x38)
        self.method_count = method_count
        self._string_ids = struct.unpack_from(f"<{string_count}I", data, string_offset)
        self._type_ids = struct.unpack_from(f"<{type_count}I", data, type_offset)
        self._protos = [struct.unpack_from("<3I", data, proto_offset + 12 * i) for i in range(proto_count)]
        self._methods = [struct.unpack_from("<HHI", data, method_offset + 8 * i) for i in range(method_count)]
        self._strings = {}

    def string(self, index):
        cached = self._strings.get(index)
        if cached is None:
            size, start = uleb128(self.data, self._string_ids[index])
            end = self.data.index(b"\x00", start)
            # Dex strings are MUTF-8; descriptors and member names are ASCII in practice.
            cached = self.data[start:end].decode("utf-8", "surrogateescape")
            self._strings[index] = cached
        return cached

    def type_name(self, index):
        return self.string(self._type_ids[index])

    def method_name(self, index):
        class_index, proto_index, name_index = self._methods[index]
        _, return_type, parameters_offset = self._protos[proto_index]
        parameters = ""
        if parameters_offset:
            (count,) = struct.unpack_from("<I", self.data, parameters_offset)
            types = struct.unpack_from(f"<{count}H", self.data, parameters_offset + 4)
            parameters = "".join(self.type_name(t) for t in types)
        return (f"{self.type_name(class_index)}->{self.string(name_index)}"
                f"({parameters}){self.type_name(return_type)}")


def read_profile(blob):
    """Yields (profile_key, checksum, hot_methods, classes, startup, post_startup) per dex."""
    if blob[:4] != MAGIC or blob[4:8] != VERSION_P:
        raise SystemExit(f"Unsupported baseline profile header: {blob[:8]!r}")
    dex_count = blob[8]
    uncompressed_size, compressed_size = struct.unpack_from("<II", blob, 9)
    data = zlib.decompress(blob[17:17 + compressed_size])
    if len(data) != uncompressed_size:
        raise SystemExit("Baseline profile size mismatch after decompression")

    reader = Reader(data)
    headers = []
    for _ in range(dex_count):
        key_size, class_count, hot_region_size, checksum, method_count = reader.take("HHIII")
        key = reader.bytes(key_size).decode() or "classes.dex"
        headers.append((key, class_count, hot_region_size, checksum, method_count))

    for key, class_count, hot_region_size, checksum, method_count in headers:
        hot_methods = []
        region_end = reader.offset + hot_region_size
        method = 0
        while reader.offset < region_end:
            method += reader.take("H")
            if reader.take("H"):
                raise SystemExit(f"{key}: inline caches are not supported in baseline profiles")
            hot_methods.append(method)
        if reader.offset != region_end:
            raise SystemExit(f"{key}: hot method region overran its declared size")

        classes = []
        type_index = 0
        for _ in range(class_count):
            type_index += reader.take("H")
            classes.append(type_index)

        bitmap = reader.bytes((2 * method_count + 7) // 8)
        bit = lambda index: bitmap[index // 8] >> (index % 8) & 1
        startup = {m for m in range(method_count) if bit(m)}
        post_startup = {m for m in range(method_count) if bit(m + method_count)}
        yield key, checksum, method_count, hot_methods, classes, startup, post_startup

    if reader.offset != len(data):
        raise SystemExit("Baseline profile has trailing data")


def main():
    if len(sys.argv) != 2:
        raise SystemExit(__doc__)
    apk = zipfile.ZipFile(sys.argv[1])
    if "classes.dex" not in apk.namelist() and "base.apk" in apk.namelist():
        apk = zipfile.ZipFile(io.BytesIO(apk.read("base.apk")))  # split bundle
    crcs = {info.filename: info.CRC for info in apk.infolist()}
    rules = []
    for key, checksum, method_count, hot, classes, startup, post in read_profile(apk.read(PROFILE_PATH)):
        if crcs.get(key) != checksum:
            raise SystemExit(f"{key}: profile checksum does not match {sys.argv[1]}; pass the unpatched APK")
        dex = Dex(apk.read(key))
        if dex.method_count != method_count:
            raise SystemExit(f"{key}: profile expects {method_count} methods, dex has {dex.method_count}")
        rules.extend(dex.type_name(t) for t in classes)
        hot = set(hot)
        for method in sorted(hot | startup | post):
            flags = ("H" if method in hot else "") + ("S" if method in startup else "") \
                + ("P" if method in post else "")
            rules.append(flags + dex.method_name(method))
    sys.stdout.write("\n".join(rules) + "\n")


if __name__ == "__main__":
    main()
