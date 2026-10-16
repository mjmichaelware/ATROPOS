# SPDX-License-Identifier: AGPL-3.0-only
"""Streaming execution DAG generator for SpecGraph compiler (§0 R_t bounded host resources)."""

import hashlib
from typing import Generator, Dict, Any, Iterable


def generate_streaming_execution_dag(
    atoms_stream: Iterable[Dict[str, Any]],
    partition_budget: int = 12000
) -> Generator[Dict[str, Any], None, None]:
    """Generates streaming partition clusters from an input stream of atoms without materializing the whole graph.
    
    Yields partition clusters when cumulative token count reaches partition_budget.
    Computes boundary hashes on the fly.
    """
    cluster_index = 0
    current_cluster_atoms = []
    current_tokens = 0
    current_territories = set()
    hasher = hashlib.sha256()

    for atom in atoms_stream:
        atom_id = atom.get("id", f"atom_{len(current_cluster_atoms)}")
        territory = atom.get("territory", [])
        statement = atom.get("statement", "")
        atom_tokens = 100 + len(statement) // 4 + len(territory) * 50

        if current_cluster_atoms and (current_tokens + atom_tokens > partition_budget):
            boundary_hash = hasher.hexdigest()
            yield {
                "cluster_id": f"cluster_{cluster_index}",
                "atoms": current_cluster_atoms,
                "token_count": current_tokens,
                "assigned_territory": sorted(list(current_territories)),
                "boundary_hash": boundary_hash,
            }
            cluster_index += 1
            current_cluster_atoms = []
            current_tokens = 0
            current_territories = set()
            hasher = hashlib.sha256()

        current_cluster_atoms.append(atom)
        current_tokens += atom_tokens
        current_territories.update(territory)
        hasher.update(atom_id.encode("utf-8"))
        for t in territory:
            hasher.update(t.encode("utf-8"))

    if current_cluster_atoms:
        boundary_hash = hasher.hexdigest()
        yield {
            "cluster_id": f"cluster_{cluster_index}",
            "atoms": current_cluster_atoms,
            "token_count": current_tokens,
            "assigned_territory": sorted(list(current_territories)),
            "boundary_hash": boundary_hash,
        }
