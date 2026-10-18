\"\"\"Core models: RaftState and Role.\"\"\"
from enum import Enum
from dataclasses import dataclass
from typing import Optional

class Role(str, Enum):
    FOLLOWER = "FOLLOWER"
    CANDIDATE = "CANDIDATE"
    LEADER = "LEADER"

@dataclass
class RaftState:
    node_id: str
    current_term: int = 0
    voted_for: Optional[str] = None
    role: Role = Role.FOLLOWER
    commit_index: int = 0
    last_applied: int = 0
