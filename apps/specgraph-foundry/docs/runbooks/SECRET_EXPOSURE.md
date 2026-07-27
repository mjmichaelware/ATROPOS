# Secret Exposure Runbook

## Signs of exposure

- Secret check workflow fails
- Secret detected in commit or artifact
- Unauthorized access detected

## Immediate actions

1. **Rotate exposed secrets immediately**
2. Revoke compromised credentials
3. Remove secret from source, workflow logs, and artifacts
4. Identify scope of exposure

## Prevention

- Use secret scanning in CI
- Use OIDC/WIF instead of long-lived credentials
- Never log or print secrets
- Use GitHub secrets for configuration values
- Sanitize workflow artifacts

## Recovery

1. Generate new secrets
2. Update GitHub secrets
3. Update deployment configurations
4. Verify new secrets work
5. Audit access logs for unauthorized use
