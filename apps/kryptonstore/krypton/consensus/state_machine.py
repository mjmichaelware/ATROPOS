\"\"\"Consensus State Machine: Deterministic Raft engine.\"\"\"
from typing import List, Set
from krypton.models.raft_state import RaftState, Role
from krypton.models.log_entry import LogEntry
from krypton.models.rpc import AppendEntriesRPC, AppendEntriesResult, RequestVoteRPC, RequestVoteResult

class RaftConsensusStateMachine:
    def __init__(self, node_id: str, peers: List[str]) -> None:
        self.state = RaftState(node_id=node_id)
        self.peers = peers
        self.log: List[LogEntry] = []
        self.votes_received: Set[str] = set()

    @property
    def quorum_size(self) -> int:
        return ((len(self.peers) + 1) // 2) + 1

    def handle_election_timeout(self) -> RequestVoteRPC:
        self.state.role = Role.CANDIDATE
        self.state.current_term += 1
        self.state.voted_for = self.state.node_id
        self.votes_received = {self.state.node_id}
        if len(self.votes_received) >= self.quorum_size:
            self.state.role = Role.LEADER
        last_idx = len(self.log) - 1 if self.log else -1
        last_term = self.log[-1].term if self.log else 0
        return RequestVoteRPC(term=self.state.current_term, candidate_id=self.state.node_id, last_log_index=last_idx, last_log_term=last_term)

    def handle_request_vote(self, rpc: RequestVoteRPC) -> RequestVoteResult:
        if rpc.term < self.state.current_term:
            return RequestVoteResult(term=self.state.current_term, vote_granted=False)
        if rpc.term > self.state.current_term:
            self.state.current_term = rpc.term
            self.state.role = Role.FOLLOWER
            self.state.voted_for = None
        can_vote = (self.state.voted_for is None or self.state.voted_for == rpc.candidate_id)
        last_idx = len(self.log) - 1 if self.log else -1
        last_term = self.log[-1].term if self.log else 0
        up_to_date = (rpc.last_log_term > last_term or (rpc.last_log_term == last_term and rpc.last_log_index >= last_idx))
        vote = can_vote and up_to_date
        if vote: self.state.voted_for = rpc.candidate_id
        return RequestVoteResult(term=self.state.current_term, vote_granted=vote)

    def register_vote(self, voter_id: str, result: RequestVoteResult) -> bool:
        if result.term > self.state.current_term:
            self.state.current_term = result.term
            self.state.role = Role.FOLLOWER
            self.state.voted_for = None
            return False
        if self.state.role == Role.CANDIDATE and result.vote_granted:
            self.votes_received.add(voter_id)
            if len(self.votes_received) >= self.quorum_size:
                self.state.role = Role.LEADER
                return True
        return False

    def handle_append_entries(self, rpc: AppendEntriesRPC) -> AppendEntriesResult:
        if rpc.term < self.state.current_term:
            return AppendEntriesResult(term=self.state.current_term, success=False, match_index=len(self.log) - 1)
        if rpc.term >= self.state.current_term:
            self.state.current_term = rpc.term
            self.state.role = Role.FOLLOWER
        if rpc.prev_log_index >= 0:
            if rpc.prev_log_index >= len(self.log):
                return AppendEntriesResult(term=self.state.current_term, success=False, match_index=len(self.log) - 1)
            if self.log[rpc.prev_log_index].term != rpc.prev_log_term:
                self.log = self.log[:rpc.prev_log_index]
                return AppendEntriesResult(term=self.state.current_term, success=False, match_index=len(self.log) - 1)
        for entry in rpc.entries:
            if entry.index < len(self.log): self.log[entry.index] = entry
            else: self.log.append(entry)
        if rpc.leader_commit > self.state.commit_index:
            self.state.commit_index = min(rpc.leader_commit, len(self.log) - 1)
        return AppendEntriesResult(term=self.state.current_term, success=True, match_index=len(self.log) - 1 if self.log else -1)
