#!/usr/bin/env python3
# SPDX-License-Identifier: AGPL-3.0-only
"""
Atropos Arrow & Parquet Unified Schema Specification
Classification: Phase 20 Mathematical Extension · Tier-1 Data Center Schema
"""

try:
    import pyarrow as pa
except ImportError:
    pa = None

def get_atropos_arrow_schema():
    if pa is None:
        raise RuntimeError("PyArrow is required to initialize the schema.")

    # 1. Standard Process-Supervised Reasoning Step Schema
    step_annotation_schema = pa.struct([
        pa.field("step_index", pa.int32(), nullable=False),
        pa.field("step_anchor_token_idx", pa.int64(), nullable=False),
        pa.field("step_content", pa.string(), nullable=False),
        pa.field("ast_mutation_type", pa.dictionary(pa.int8(), pa.string()), nullable=True),
        pa.field("prm_label", pa.float32(), nullable=False), # Continuous [-1.0, 1.0] or binary {0, 1}
        pa.field("monte_carlo_value", pa.float32(), nullable=False), # Q(s, a) rollout estimate
        pa.field("is_sound", pa.bool_(), nullable=False),
        pa.field("smt_solver_discharged", pa.bool_(), nullable=False)
    ])

    # 2. Complete Interoperable Training & Telemetry Record
    return pa.schema([
        # Primary Identifiers & Provenance
        pa.field("trajectory_id", pa.string(), nullable=False),
        pa.field("turn_index", pa.int32(), nullable=False),
        pa.field("timestamp_utc_epoch_ms", pa.int64(), nullable=False),
        pa.field("schema_version", pa.string(), nullable=False),

        # Standard Instruction Fine-Tuning & Multi-Turn Chat Format
        pa.field("messages", pa.list_(pa.struct([
            pa.field("role", pa.dictionary(pa.int8(), pa.string()), nullable=False),
            pa.field("content", pa.string(), nullable=False),
            pa.field("tool_calls", pa.list_(pa.struct([
                pa.field("id", pa.string(), nullable=False),
                pa.field("type", pa.string(), nullable=False),
                pa.field("function_name", pa.string(), nullable=False),
                pa.field("arguments_json", pa.string(), nullable=False)
            ])), nullable=True)
        ])), nullable=False),

        # Preference Alignment Schemas (DPO / KTO / ORPO)
        pa.field("prompt", pa.string(), nullable=False),
        pa.field("chosen", pa.string(), nullable=False),
        pa.field("rejected", pa.string(), nullable=False),
        pa.field("preference_margin", pa.float32(), nullable=False),

        # Token-Level Pre-Tokenized Buffers
        pa.field("input_ids", pa.list_(pa.int32()), nullable=False),
        pa.field("attention_mask", pa.list_(pa.int8()), nullable=False),
        pa.field("labels", pa.list_(pa.int64()), nullable=False),
        pa.field("loss_mask", pa.list_(pa.float32()), nullable=False),

        # Process Supervision / PRM Steps
        pa.field("steps", pa.list_(step_annotation_schema), nullable=False),

        # Reinforcement Learning & Bellman MDP Telemetry
        pa.field("observation_state_hash", pa.string(), nullable=False),
        pa.field("action_vector_id", pa.string(), nullable=False),
        pa.field("scalar_reward", pa.float32(), nullable=False),
        pa.field("reward_components", pa.struct([
            pa.field("base_outcome", pa.float32(), nullable=False),
            pa.field("curiosity_kl", pa.float32(), nullable=False),
            pa.field("pain_penalty", pa.float32(), nullable=False),
            pa.field("habituation_factor", pa.float32(), nullable=False),
            pa.field("myelination_scale", pa.float32(), nullable=False),
            pa.field("developmental_gain", pa.float32(), nullable=False),
            pa.field("length_penalty", pa.float32(), nullable=False)
        ]), nullable=False),
        pa.field("logprob", pa.float32(), nullable=False),
        pa.field("ref_logprob", pa.float32(), nullable=False),
        pa.field("value_estimate", pa.float32(), nullable=False),
        pa.field("generalized_advantage", pa.float32(), nullable=False),
        pa.field("terminated", pa.bool_(), nullable=False),
        pa.field("truncated", pa.bool_(), nullable=False),

        # ATROPOS Neurodevelopmental / Synaptic Graph Telemetry
        pa.field("synaptic_edges", pa.list_(pa.struct([
            pa.field("edge_id", pa.string(), nullable=False),
            pa.field("pre_synaptic_id", pa.string(), nullable=False),
            pa.field("post_synaptic_id", pa.string(), nullable=False),
            pa.field("weight", pa.float32(), nullable=False),
            pa.field("stdp_delta_ms", pa.int32(), nullable=False),
            pa.field("hebbian_trace", pa.float32(), nullable=False)
        ])), nullable=True),
        pa.field("developmental_phase", pa.dictionary(pa.int8(), pa.string()), nullable=False),
        pa.field("evsi_value", pa.float32(), nullable=False),
        pa.field("kl_prediction_error", pa.float32(), nullable=False)
    ])

if __name__ == "__main__":
    if pa is not None:
        schema = get_atropos_arrow_schema()
        print(f"Validated Atropos Arrow Schema with {len(schema)} root fields.")
    else:
        print("Atropos Arrow Schema defined (PyArrow not locally installed in runtime).")
