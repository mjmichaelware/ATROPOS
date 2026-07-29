from typing import Dict, List, Any, Optional
from .compiler_fingerprints import generate_fingerprint

class SourceAuthorityNoMatch(LookupError):
    def __init__(self, document_id: str):
        self.document_id = document_id
        super().__init__(f"source authority not found: {document_id}")


class SourceAuthorityHashMismatch(ValueError):
    def __init__(self, document_id: str, expected_sha256: str, observed_sha256: str):
        self.document_id = document_id
        self.expected_sha256 = expected_sha256
        self.observed_sha256 = observed_sha256
        super().__init__(
            "source authority hash mismatch for "
            f"{document_id}: expected {expected_sha256}, observed {observed_sha256}"
        )


class SourceAuthority:
    def __init__(
        self,
        document_id: str,
        tier: int,  # 1 is highest priority, higher numbers have lower priority
        version: str,
        effective_date: str,
        owner: str,
        is_approved: bool = True,
        artifact_sha256: Optional[str] = None,
    ):
        self.document_id = document_id
        self.tier = tier
        self.version = version
        self.effective_date = effective_date
        self.owner = owner
        self.is_approved = is_approved
        self.artifact_sha256 = artifact_sha256

    def to_dict(self) -> Dict[str, Any]:
        return {
            "document_id": self.document_id,
            "tier": self.tier,
            "version": self.version,
            "effective_date": self.effective_date,
            "owner": self.owner,
            "is_approved": self.is_approved,
            "artifact_sha256": self.artifact_sha256,
        }

class AuthorityRegistry:
    def __init__(self):
        self.authorities: Dict[str, SourceAuthority] = {}
        self.supersession_relations: Dict[str, str] = {}  # newer_doc_id -> older_doc_id

    def register_authority(self, auth: SourceAuthority):
        self.authorities[auth.document_id] = auth

    def register_supersession(self, newer_doc_id: str, older_doc_id: str):
        self.supersession_relations[newer_doc_id] = older_doc_id

    def to_manifest(self) -> Dict[str, Any]:
        authorities = [
            self.authorities[document_id].to_dict()
            for document_id in sorted(self.authorities)
        ]
        supersessions = [
            {
                "newer_doc_id": newer_doc_id,
                "older_doc_id": self.supersession_relations[newer_doc_id],
            }
            for newer_doc_id in sorted(self.supersession_relations)
        ]
        payload = {
            "schema_version": "source-authority-registry-v1",
            "authorities": authorities,
            "supersessions": supersessions,
        }
        return {
            **payload,
            "manifest_sha256": generate_fingerprint(payload),
        }

    def require_authority(
        self,
        document_id: str,
        observed_sha256: Optional[str] = None,
    ) -> SourceAuthority:
        authority = self.authorities.get(document_id)
        if authority is None:
            raise SourceAuthorityNoMatch(document_id)
        if (
            observed_sha256 is not None
            and authority.artifact_sha256 is not None
            and authority.artifact_sha256 != observed_sha256
        ):
            raise SourceAuthorityHashMismatch(
                document_id,
                authority.artifact_sha256,
                observed_sha256,
            )
        return authority

    def resolve_precedence(self, doc_a: str, doc_b: str) -> Optional[str]:
        """
        Returns the document ID that has higher precedence.
        Returns None if there is a conflict that cannot be resolved deterministically.
        Precedence rules:
        1. Higher tier wins (e.g. tier 1 wins over tier 2)
        2. If same tier, check supersession relations (newer supersedes older)
        3. If same tier and no supersession, check effective dates (newer date wins)
        """
        auth_a = self.authorities.get(doc_a)
        auth_b = self.authorities.get(doc_b)

        if not auth_a and not auth_b:
            return None
        if auth_a and not auth_b:
            return doc_a
        if auth_b and not auth_a:
            return doc_b

        assert auth_a is not None
        assert auth_b is not None

        # Check approval status
        if auth_a.is_approved and not auth_b.is_approved:
            return doc_a
        if auth_b.is_approved and not auth_a.is_approved:
            return doc_b

        # Rule 1: Tier wins (lower tier number is higher precedence)
        if auth_a.tier < auth_b.tier:
            return doc_a
        elif auth_b.tier < auth_a.tier:
            return doc_b

        # Rule 2: Supersession
        if self.supersession_relations.get(doc_a) == doc_b:
            return doc_a
        if self.supersession_relations.get(doc_b) == doc_a:
            return doc_b

        # Rule 3: Effective Date
        if auth_a.effective_date > auth_b.effective_date:
            return doc_a
        elif auth_b.effective_date > auth_a.effective_date:
            return doc_b

        # No deterministic resolution
        return None
