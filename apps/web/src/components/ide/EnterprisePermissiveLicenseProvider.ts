/**
 * EnterprisePermissiveLicenseProvider.ts
 *
 * Implements ATOM AI6: ENTERPRISE PERMISSIVE LICENSE-FILTERED MODEL PROVIDER
 * Enforces enterprise compliance filtering on completions, verifying permissive
 * licensing (MIT, Apache-2.0, BSD-2-Clause, BSD-3-Clause, ISC) before rendering
 * code completion suggestions.
 */

export interface LicenseVerificationResult {
  isPermissive: boolean;
  detectedLicense: string;
  confidence: number;
  attributionRequired: boolean;
  sourceRepository?: string;
  rejectionReason?: string;
}

export interface CodeCompletionProposal {
  id: string;
  insertText: string;
  sourceLicense?: string;
  originatingRepo?: string;
  permissiveOnlyEnforced: boolean;
}

export class EnterprisePermissiveLicenseProvider {
  private static readonly PERMISSIVE_LICENSES = new Set([
    'MIT',
    'Apache-2.0',
    'BSD-2-Clause',
    'BSD-3-Clause',
    'ISC',
    'Unlicense',
    'CC0-1.0'
  ]);

  private static readonly COPYLEFT_OR_RESTRICTIVE = new Set([
    'GPL-2.0',
    'GPL-3.0',
    'AGPL-3.0',
    'LGPL-2.1',
    'LGPL-3.0',
    'SSPL',
    'BUSL-1.1'
  ]);

  /**
   * Verify license validity for code completion proposal
   */
  public verifyLicense(licenseId: string, repoUrl?: string): LicenseVerificationResult {
    const normalized = licenseId.trim();
    if (EnterprisePermissiveLicenseProvider.PERMISSIVE_LICENSES.has(normalized)) {
      return {
        isPermissive: true,
        detectedLicense: normalized,
        confidence: 0.98,
        attributionRequired: normalized !== 'Unlicense' && normalized !== 'CC0-1.0',
        sourceRepository: repoUrl
      };
    }

    if (EnterprisePermissiveLicenseProvider.COPYLEFT_OR_RESTRICTIVE.has(normalized)) {
      return {
        isPermissive: false,
        detectedLicense: normalized,
        confidence: 0.99,
        attributionRequired: true,
        sourceRepository: repoUrl,
        rejectionReason: `Non-permissive copyleft license detected (${normalized}); rejected by enterprise compliance policy.`
      };
    }

    // Default unknown licenses to rejected if permissive filtering is strict
    return {
      isPermissive: false,
      detectedLicense: normalized || 'UNKNOWN',
      confidence: 0.5,
      attributionRequired: true,
      sourceRepository: repoUrl,
      rejectionReason: 'Unknown license cannot be certified as permissive.'
    };
  }

  /**
   * Filter an array of completion proposals, retaining only those matching permissive licenses.
   */
  public filterProposals(proposals: CodeCompletionProposal[]): CodeCompletionProposal[] {
    return proposals.filter((p) => {
      if (!p.permissiveOnlyEnforced) return true;
      if (!p.sourceLicense) return false;
      const result = this.verifyLicense(p.sourceLicense, p.originatingRepo);
      return result.isPermissive;
    });
  }

  public static defaultProvider(): EnterprisePermissiveLicenseProvider {
    return new EnterprisePermissiveLicenseProvider();
  }
}
