# SPDX-License-Identifier: Apache-2.0
# Byte cost of a UUID library id on the air (proposal O-09). Minimal CBOR encoder, preferred serialization (ble-protocol 9.1).
import math, re, sys, zlib

def head(major, n):
    if n < 24: return bytes([major << 5 | n])
    if n < 0x100: return bytes([major << 5 | 24, n])
    if n < 0x10000: return bytes([major << 5 | 25]) + n.to_bytes(2, "big")
    if n < 0x100000000: return bytes([major << 5 | 26]) + n.to_bytes(4, "big")
    return bytes([major << 5 | 27]) + n.to_bytes(8, "big")

def enc(v):
    if isinstance(v, bool): return b"\xf5" if v else b"\xf4"
    if isinstance(v, int): return head(0, v)
    if isinstance(v, bytes): return head(2, len(v)) + v
    if isinstance(v, str):
        b = v.encode("utf-8"); return head(3, len(b)) + b
    if isinstance(v, list): return head(4, len(v)) + b"".join(enc(x) for x in v)
    if isinstance(v, dict):
        return head(5, len(v)) + b"".join(enc(k) + enc(x) for k, x in v.items() if x is not None)
    raise TypeError(v)

def cmap(d):  # leave out None values, as the app does
    return {k: v for k, v in d.items() if v is not None}

OLD = "zangbundel-bibliotheek"                      # the default library's id today
UUID = "3f6c2a1e-8b4d-4c1a-9e2f-7d5b0a6c9e11"       # FDS 4.1 example
MAX = "a" * 64                                      # the longest id song-format 3.2 allows
SHORT8 = "3f6c2a1e"                                 # an 8-hex short form, for comparison
RAW16 = bytes(16)                                   # the UUID as 16 raw bytes, for comparison

print("1. One library id as CBOR (bytes, with its map key)")
for name, v in [("today, " + OLD, OLD), ("UUID as text", UUID), ("64-char maximum", MAX), ("8-hex short form", SHORT8), ("UUID as bstr 16", RAW16)]:
    print(f"   {name:34s} {len(enc(v)) + 1:3d}")

def ref(lib, song, rev=None):
    return cmap({1: lib, 2: song, 3: bytes(16), 4: rev})

def signed_len(body):
    return len(enc([body, bytes(64)]))

def frames(n, fs): return math.ceil(n / fs)

print("\n2. Worst-case state body (StateTest: title, rev and pt left out)")
def worst(lib):
    return enc(cmap({1: bytes(5), 2: 0xFFFF, 3: 2**63 - 1, 4: 0, 5: 0xFF, 6: ref(lib, "b" * 64), 8: 255, 9: 2**63 - 1,
                     10: {d: [bytes(8), 1, 65536, True] for d in range(3, 8)}, 11: 2**63 - 1, 12: [bytes(8)] * 8, 14: bytes(32)}))
for name, lib in [("64-char id", MAX), ("UUID", UUID)]:
    b = worst(lib); r = signed_len(b)
    print(f"   {name:10s} body {len(b)} (limit 440)  record {r} (limit 512)  legacy frames {frames(r, 16)} (limit 30)")

print("\n3. A representative state body (assumptions in the proposal)")
def typical(lib, rev):
    return enc(cmap({1: bytes(5), 2: 0x1234, 3: 5_400_000, 4: 0, 5: 0x07, 6: ref(lib, "vuur-in-de-nacht", rev),
                     7: "Vuur in de nacht", 8: 12, 9: 25, 10: {3: [bytes(8), 1, 1100], 4: [bytes(8), 1, 300]},
                     11: 0, 13: 12000, 14: bytes(32), 15: 3, 16: b"\x07"}))
for name, lib, rev in [("today: id + 40-hex revision", OLD, "f" * 40), ("UUID + 40-hex revision", UUID, "f" * 40),
                       ("UUID + version \"4\"", UUID, "4"), ("UUID, no rev", UUID, None)]:
    b = typical(lib, rev); r = signed_len(b)
    print(f"   {name:30s} body {len(b)}  record {r}  legacy k {frames(r, 16)}  extended k {frames(r, 233)}")

print("\n4. Sealed writes, worst case (64-char song id, 64-char rev)")
def sealed(plain): return 1 + 48 + len(enc(plain))
for name, lib in [("64-char id", MAX), ("UUID", UUID)]:
    rq = cmap({1: bytes(16), 2: 1, 3: bytes(4), 4: ref(lib, "b" * 64, "r" * 64), 6: bytes(5)})
    ct = cmap({1: bytes(16), 2: bytes(4), 3: 1, 4: ref(lib, "b" * 64, "r" * 64), 9: bytes(5)})
    print(f"   {name:10s} Request op 1 {sealed(rq)}  Control op 1/4 {sealed(ct)}  (limit 512)")

print("\n5. Song document 3 with the example song of song-format 14.1")
text = open(sys.argv[1], encoding="utf-8").read()
m = re.search(r"### 14\.1[^\n]*\n\n```\n(.*?)```", text, re.S)
song = m.group(1).encode("utf-8")
def doc3(lib, rev):
    return enc(cmap({1: ref(lib, "vuur-in-de-nacht", rev), 2: song, 3: "CC0-1.0"}))
def deflate(b):
    c = zlib.compressobj(9, zlib.DEFLATED, -15); return c.compress(b) + c.flush()
print(f"   song file {len(song)} bytes")
for name, lib, rev in [("today: id + 40-hex revision", OLD, "f" * 40), ("UUID + version \"4\"", UUID, "4"), ("UUID + 40-hex revision", UUID, "f" * 40)]:
    raw = doc3(lib, rev); z = deflate(raw)
    print(f"   {name:30s} raw {len(raw)}  deflated {len(z)}  legacy k {frames(len(z), 16)}  extended k {frames(len(z), 233)}")

print("\n6. Text keys stored on the device")
print(f"   '<lib>/<song>' worst case: today {64 + 1 + 64} chars, with a UUID {36 + 1 + 64} (resume record cap 200)")
