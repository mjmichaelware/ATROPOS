\"\"\"Core models: Raft RPC packets.\"\"\"
from dataclasses import dataclass
from typing import List
from krypton.models.log_entry import LogEntry

@dataclass
class AppendEntriesRPC:
    term: int
    leader_id: str
    prev_log_index: int
    prev_log_term: int
    entries: List[LogEntry]
    leader_commit: int

@dataclass
class AppendEntriesResult:
    term: int
    success: bool
    match_index: int

@dataclass
class RequestVoteRPC:
    term: int
    candidate_id: str
    last_log_index: int
    last_log_term: int

@dataclass
class RequestVoteResult:
    term: int
    vote_granted: bool
