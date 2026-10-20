"""WAL Engine: Binary frame serializer/deserializer with CRC32 verification."""
import struct
import zlib
import json
from typing import Tuple
from krypton.models.log_entry import LogEntry

class FrameCorruptionError(Exception): pass

class FrameCodec:
    HEADER_FORMAT = ">II"
    HEADER_SIZE = struct.calcsize(HEADER_FORMAT)

    @classmethod
    def serialize(cls, entry: LogEntry) -> bytes:
        payload = json.dumps({
            "index": entry.index,
            "term": entry.term,
            "command": entry.command,
            "key": entry.key,
            "value": entry.value.hex(),
            "timestamp": entry.timestamp,
        }, sort_keys=True).encode("utf-8")
        checksum = zlib.crc32(payload) & 0xFFFFFFFF
        header = struct.pack(cls.HEADER_FORMAT, len(payload), checksum)
        return header + payload

    @classmethod
    def deserialize(cls, buffer: bytes, offset: int = 0) -> Tuple[LogEntry, int]:
        if len(buffer) - offset < cls.HEADER_SIZE:
            raise ValueError("Buffer too short for header")
        length, expected_crc = struct.unpack_from(cls.HEADER_FORMAT, buffer, offset)
        start = offset + cls.HEADER_SIZE
        end = start + length
        if len(buffer) < end:
            raise ValueError("Truncated payload")
        payload = buffer[start:end]
        if (zlib.crc32(payload) & 0xFFFFFFFF) != expected_crc:
            raise FrameCorruptionError("CRC32 mismatch on frame")
        data = json.loads(payload.decode("utf-8"))
        return LogEntry(
            index=int(data["index"]),
            term=int(data["term"]),
            command=str(data["command"]),
            key=str(data["key"]),
            value=bytes.fromhex(data["value"]),
            timestamp=float(data.get("timestamp", 0.0)),
        ), end
