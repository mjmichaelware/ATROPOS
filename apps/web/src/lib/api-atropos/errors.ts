/**
 * ATROPOS API Errors
 */

export class AtroposApiError extends Error {
  constructor(
    public code: string,
    message: string,
    public details?: Record<string, any>
  ) {
    super(message);
    this.name = 'AtroposApiError';
  }
}

export class NotFoundError extends AtroposApiError {
  constructor(resource: string, id: string) {
    super('NOT_FOUND', `${resource} not found: ${id}`);
  }
}

export class UnauthorizedError extends AtroposApiError {
  constructor() {
    super('UNAUTHORIZED', 'Authentication required');
  }
}

export class ValidationError extends AtroposApiError {
  constructor(details: Record<string, string>) {
    super('VALIDATION_ERROR', 'Validation failed', details);
  }
}
