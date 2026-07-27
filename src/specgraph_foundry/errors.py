class SpecGraphError(Exception):
    """Base application error."""


class ValidationError(SpecGraphError):
    """Input violates an invariant."""


class NotFoundError(SpecGraphError):
    """Requested record does not exist."""


class ConflictError(SpecGraphError):
    """Requested mutation conflicts with stored state."""
