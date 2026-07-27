from datetime import UTC, datetime
from typing import Dict, List, Any, Optional

def utc_now() -> str:
    return datetime.now(UTC).isoformat()

class ProvEntity:
    def __init__(self, entity_id: str, entity_type: str, label: str, attributes: Optional[Dict[str, Any]] = None):
        self.entity_id = entity_id
        self.entity_type = entity_type
        self.label = label
        self.attributes = attributes or {}

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.entity_id,
            "type": self.entity_type,
            "label": self.label,
            "attributes": self.attributes
        }

class ProvActivity:
    def __init__(self, activity_id: str, activity_type: str, label: str, start_time: Optional[str] = None, end_time: Optional[str] = None):
        self.activity_id = activity_id
        self.activity_type = activity_type
        self.label = label
        self.start_time = start_time or utc_now()
        self.end_time = end_time

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.activity_id,
            "type": self.activity_type,
            "label": self.label,
            "start_time": self.start_time,
            "end_time": self.end_time
        }

class ProvAgent:
    def __init__(self, agent_id: str, agent_type: str, label: str):
        self.agent_id = agent_id
        self.agent_type = agent_type
        self.label = label

    def to_dict(self) -> Dict[str, Any]:
        return {
            "id": self.agent_id,
            "type": self.agent_type,
            "label": self.label
        }

class ProvGraph:
    def __init__(self):
        self.entities: Dict[str, ProvEntity] = {}
        self.activities: Dict[str, ProvActivity] = {}
        self.agents: Dict[str, ProvAgent] = {}
        self.relations: List[Dict[str, str]] = []

    def add_entity(self, entity_id: str, entity_type: str, label: str, attributes: Optional[Dict[str, Any]] = None) -> ProvEntity:
        entity = ProvEntity(entity_id, entity_type, label, attributes)
        self.entities[entity_id] = entity
        return entity

    def add_activity(self, activity_id: str, activity_type: str, label: str) -> ProvActivity:
        activity = ProvActivity(activity_id, activity_type, label)
        self.activities[activity_id] = activity
        return activity

    def add_agent(self, agent_id: str, agent_type: str, label: str) -> ProvAgent:
        agent = ProvAgent(agent_id, agent_type, label)
        self.agents[agent_id] = agent
        return agent

    def wasGeneratedBy(self, entity_id: str, activity_id: str):
        self.relations.append({
            "subject": entity_id,
            "relation": "prov:wasGeneratedBy",
            "object": activity_id
        })

    def used(self, activity_id: str, entity_id: str):
        self.relations.append({
            "subject": activity_id,
            "relation": "prov:used",
            "object": entity_id
        })

    def wasAssociatedWith(self, activity_id: str, agent_id: str):
        self.relations.append({
            "subject": activity_id,
            "relation": "prov:wasAssociatedWith",
            "object": agent_id
        })

    def wasAttributedTo(self, entity_id: str, agent_id: str):
        self.relations.append({
            "subject": entity_id,
            "relation": "prov:wasAttributedTo",
            "object": agent_id
        })

    def wasDerivedFrom(self, generated_entity_id: str, used_entity_id: str):
        self.relations.append({
            "subject": generated_entity_id,
            "relation": "prov:wasDerivedFrom",
            "object": used_entity_id
        })

    def to_json_ld(self) -> Dict[str, Any]:
        """
        Generate W3C PROV-O compatible JSON-LD graph.
        """
        context = {
            "prov": "http://www.w3.org/ns/prov#",
            "rdf": "http://www.w3.org/1999/02/22-rdf-syntax-ns#",
            "rdfs": "http://www.w3.org/2000/01/rdf-schema#",
            "Entity": "prov:Entity",
            "Activity": "prov:Activity",
            "Agent": "prov:Agent",
            "label": "rdfs:label",
            "wasGeneratedBy": {
                "@id": "prov:wasGeneratedBy",
                "@type": "@id"
            },
            "used": {
                "@id": "prov:used",
                "@type": "@id"
            },
            "wasAssociatedWith": {
                "@id": "prov:wasAssociatedWith",
                "@type": "@id"
            },
            "wasDerivedFrom": {
                "@id": "prov:wasDerivedFrom",
                "@type": "@id"
            }
        }

        graph = []
        for entity_id, entity in self.entities.items():
            node = {
                "@id": entity_id,
                "@type": "Entity",
                "label": entity.label,
            }
            # Add extra attributes
            for k, v in entity.attributes.items():
                node[k] = v
            graph.append(node)

        for act_id, act in self.activities.items():
            graph.append({
                "@id": act_id,
                "@type": "Activity",
                "label": act.label,
                "prov:startedAtTime": act.start_time,
                "prov:endedAtTime": act.end_time
            })

        for agent_id, agent in self.agents.items():
            graph.append({
                "@id": agent_id,
                "@type": "Agent",
                "label": agent.label
            })

        for rel in self.relations:
            # Map relations onto JSON-LD properties
            # Find subject node in graph
            for node in graph:
                if node["@id"] == rel["subject"]:
                    prop_name = rel["relation"].split(":")[-1]
                    node[prop_name] = rel["object"]
                    break

        return {
            "@context": context,
            "@graph": graph
        }
