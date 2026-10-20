"""Transport Layer: Peer registry."""
from typing import Dict, List, Optional
from dataclasses import dataclass

@dataclass
class PeerNode:
    peer_id: str
    endpoint: str
    alive: bool = True

class PeerRegistry:
    def __init__(self, peers: Optional[Dict[str, str]] = None) -> None:
        self._peers: Dict[str, PeerNode] = {pid: PeerNode(pid, ep) for pid, ep in (peers or {}).items()}
    def register(self, peer_id: str, endpoint: str) -> None:
        self._peers[peer_id] = PeerNode(peer_id, endpoint)
    def list_peers(self) -> List[PeerNode]:
        return list(self._peers.values())
    def get_peer(self, peer_id: str) -> Optional[PeerNode]:
        return self._peers.get(peer_id)
