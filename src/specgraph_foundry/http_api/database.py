import json

from ..database import (
    Database,
    ManagedConnection,
    PostgresConnection,
)
from .models import Principal


class AuthenticatedPostgresConnection(
    PostgresConnection
):
    def __init__(
        self,
        database_url: str,
        principal: Principal,
    ) -> None:
        super().__init__(database_url)

        claims = json.dumps(
            {
                "sub": principal.user_id,
                "role": "authenticated",
                "email": principal.email,
            },
            sort_keys=True,
            separators=(",", ":"),
        )

        try:
            self._connection.execute(
                "SET LOCAL ROLE authenticated"
            )

            self._connection.execute(
                """
                SELECT set_config(
                    'request.jwt.claim.sub',
                    %s,
                    true
                )
                """,
                (principal.user_id,),
            )

            self._connection.execute(
                """
                SELECT set_config(
                    'request.jwt.claim.role',
                    'authenticated',
                    true
                )
                """
            )

            self._connection.execute(
                """
                SELECT set_config(
                    'request.jwt.claims',
                    %s,
                    true
                )
                """,
                (claims,),
            )

        except Exception:
            self._connection.close()
            raise


class RequestScopedDatabase(Database):
    def __init__(
        self,
        base: Database,
        principal: Principal,
    ) -> None:
        super().__init__(
            path=base.path,
            database_url=base.database_url,
            owner_id=principal.user_id,
        )

        self.principal = principal

    def connect(
        self,
    ) -> (
        ManagedConnection
        | PostgresConnection
    ):
        if self.database_url is not None:
            return (
                AuthenticatedPostgresConnection(
                    self.database_url,
                    self.principal,
                )
            )

        return super().connect()
