#!/usr/bin/env python3
"""Read-only structural checks. These do not replace installing and testing X."""
import argparse
import hashlib
import re
import struct
import subprocess
import zipfile
from pathlib import Path

NATIVE_HASHES = {
    "arm64-v8a": "68730e8c81b613113249574e22838fd6ce9b1c4da2be2f5fe7c71d651e32b005",
    "armeabi-v7a": "2a6570004148c338337d945342326d4c42e71684d36bb2ee999a5d7863408680",
    "x86": "3d9a36280ba19c5f710935c06f563afe377d4e7e19d0d5c931d47d861d2d48df",
    "x86_64": "2793e254ac34ec4df86450866fd924a26b9b9e96cc7ba5f6379badd432da1e76",
}
REQUIRED_CLASSES = {
    "Lcom/wireguard/android/backend/GoBackend;",
    "Lcom/wireguard/android/backend/GoBackend$VpnService;",
    "Lcom/wireguard/config/Config;",
    "Lapp/morphe/extension/twitter/wireguard/WireGuardManager;",
    "Lapp/morphe/extension/twitter/wireguard/WireGuardVpnService;",
    "Lapp/morphe/extension/twitter/wireguard/WireGuardFragment;",
    "Lapp/morphe/extension/twitter/wireguard/WireGuardStorage;",
}
LICENSES = (
    "NOTICE", "Apache-2.0.txt", "MIT-wireguard-go.txt", "AndroidX-Apache-2.0.txt",
    "Kotlin-Apache-2.0.txt", "Go-BSD.txt", "Go-crypto-BSD.txt", "Go-net-BSD.txt", "Go-sys-BSD.txt",
)


def require(condition, message):
    if not condition:
        raise ValueError(message)


def dex_classes(data):
    """Read class definitions, not arbitrary class-name references in string tables."""
    require(data[:4] == b"dex\n", "Extension is not a DEX")
    require(struct.unpack_from("<I", data, 40)[0] == 0x12345678, "Unsupported DEX endianness")
    string_offset = struct.unpack_from("<I", data, 60)[0]
    type_offset = struct.unpack_from("<I", data, 68)[0]
    count, definitions = struct.unpack_from("<II", data, 96)
    result = set()
    for index in range(count):
        type_index = struct.unpack_from("<I", data, definitions + index * 32)[0]
        string_index = struct.unpack_from("<I", data, type_offset + type_index * 4)[0]
        offset = struct.unpack_from("<I", data, string_offset + string_index * 4)[0]
        while data[offset] & 0x80:
            offset += 1
        offset += 1  # Skip the final byte of the UTF-16 length ULEB128.
        end = data.index(0, offset)
        result.add(data[offset:end].decode("utf-8"))
    return result


def check_native(archive, prefix, abis):
    require(bool(abis), "No native ABIs found")
    for abi in sorted(abis):
        require(abi in NATIVE_HASHES, f"Unsupported ABI: {abi}")
        path = f"{prefix}{abi}/libwg-go.so"
        require(path in archive.namelist(), f"Missing native backend: {path}")
        data = archive.read(path)
        require(data[:4] == b"\x7fELF", f"Not an ELF library: {path}")
        require(hashlib.sha256(data).hexdigest() == NATIVE_HASHES[abi], f"Unexpected WireGuard binary: {path}")


def check_manifest(apk, aapt2):
    output = subprocess.check_output(
        [aapt2, "dump", "xmltree", "--file", "AndroidManifest.xml", str(apk)], text=True
    )
    def attribute_integer(block, attribute):
        line = next((line for line in block.splitlines() if f"android:{attribute}(" in line), "")
        require("=" in line, f"Missing Android attribute: {attribute}")
        value = line.split("=", 1)[1].strip()
        if value in ("true", "false"):
            return int(value == "true")
        tokens = re.findall(r"0x[0-9a-fA-F]+", value)
        require(bool(tokens), f"Cannot decode Android attribute: {attribute}")
        return int(tokens[-1], 16)

    require(attribute_integer(output, "extractNativeLibs") != 0, "Native extraction must be enabled")
    require("FOREGROUND_SERVICE_SYSTEM_EXEMPTED" in output, "Missing foreground service permission")
    lines = output.splitlines()
    blocks = []
    for index, line in enumerate(lines):
        if line.strip().startswith("E: service"):
            indent = len(line) - len(line.lstrip())
            end = index + 1
            while end < len(lines) and len(lines[end]) - len(lines[end].lstrip()) > indent:
                end += 1
            blocks.append("\n".join(lines[index:end]))
    services = [block for block in blocks if '"app.morphe.extension.twitter.wireguard.WireGuardVpnService"' in block]
    require(len(services) == 1, "Expected exactly one Piko VPN service declaration")
    service = services[0]
    require("android.permission.BIND_VPN_SERVICE" in service, "VPN service is not permission-protected")
    require("android.net.VpnService" in service, "Missing VPN intent filter")
    require(attribute_integer(service, "exported") == 0, "VPN service must not be exported")
    require(attribute_integer(service, "foregroundServiceType") == 0x400, "Missing systemExempted service type")
    require("com.wireguard.android.backend.GoBackend$VpnService" not in output,
            "Unwrapped upstream service must not also be registered")
    require(not re.search(r'A: (?:android:)?split(?:\(|=)', output), "Input is still an APK split")


def verify(path, bundle=False, aapt2=None):
    with zipfile.ZipFile(path) as archive:
        names = archive.namelist()
        require(len(names) == len(set(names)), "Duplicate ZIP entries")
        if bundle:
            require("extensions/twitter.mpe" in names, "Missing Twitter extension DEX")
            classes = dex_classes(archive.read("extensions/twitter.mpe"))
            check_native(archive, "wireguard/native/", set(NATIVE_HASHES))
            license_prefix = "wireguard/licenses/"
        else:
            dex_files = [name for name in names if re.fullmatch(r"classes\d*\.dex", name)]
            require(bool(dex_files), "No DEX files in APK; supply Morphe's merged patched APK")
            classes = set().union(*(dex_classes(archive.read(name)) for name in dex_files))
            abis = {name.split("/")[1] for name in names if re.fullmatch(r"lib/[^/]+/[^/]+\.so", name)}
            check_native(archive, "lib/", abis)
            license_prefix = "assets/piko-wireguard/"
        require(REQUIRED_CLASSES <= classes, f"Missing runtime class definitions: {sorted(REQUIRED_CLASSES - classes)}")
        for name in LICENSES:
            require(license_prefix + name in names, f"Missing license: {name}")
    if not bundle:
        require(aapt2 is not None, "--aapt2 is required for APK manifest verification")
        check_manifest(path, aapt2)
    print(f"PASS: {path} — runtime classes, native payloads and notices" + ("; manifest" if not bundle else ""))
    print("Device installation, native loading, consent, and traffic routing still require runtime tests.")


if __name__ == "__main__":
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("artifact", type=Path)
    parser.add_argument("--bundle", action="store_true", help="Verify a Piko .mpp/.jar patch bundle")
    parser.add_argument("--aapt2", help="Path to Android SDK aapt2 (required for patched APK)")
    args = parser.parse_args()
    try:
        verify(args.artifact, args.bundle, args.aapt2)
    except (ValueError, KeyError, OSError, struct.error, zipfile.BadZipFile, subprocess.CalledProcessError) as error:
        parser.exit(1, f"FAIL: {error}\n")
