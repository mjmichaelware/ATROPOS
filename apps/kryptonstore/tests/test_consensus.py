"""Test Suite: Consensus split-vote resolution, quorum, and election invariants."""
import unittest
from krypton.consensus.state_machine import RaftConsensusStateMachine
from krypton.models.raft_state import Role
from krypton.models.rpc import RequestVoteRPC, RequestVoteResult

class TestRaftConsensus(unittest.TestCase):
    def test_quorum(self):
        sm = RaftConsensusStateMachine("n1", ["n2", "n3"])
        self.assertEqual(sm.quorum_size, 2)

    def test_election_and_split_vote(self):
        sm = RaftConsensusStateMachine("n1", ["n2", "n3"])
        sm.handle_election_timeout()
        self.assertEqual(sm.state.role, Role.CANDIDATE)
        sm.register_vote("n2", RequestVoteResult(1, False))
        self.assertEqual(sm.state.role, Role.CANDIDATE)
        won = sm.register_vote("n3", RequestVoteResult(1, True))
        self.assertTrue(won)
        self.assertEqual(sm.state.role, Role.LEADER)

    def test_stale_term(self):
        sm = RaftConsensusStateMachine("n1", ["n2"])
        sm.state.current_term = 5
        res = sm.handle_request_vote(RequestVoteRPC(4, "n2", 0, 0))
        self.assertFalse(res.vote_granted)

if __name__ == "__main__":
    unittest.main()
