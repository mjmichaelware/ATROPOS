/* SPDX-License-Identifier: AGPL-3.0-only */

export enum OpticalStanding {
  IN_TERRITORY_SHARP = "IN_TERRITORY_SHARP",
  OUT_OF_TERRITORY_RECEDED = "OUT_OF_TERRITORY_RECEDED",
  ATTESTATION_DRIFT_WARNED = "ATTESTATION_DRIFT_WARNED",
}

export interface OpticalMaterialProperties {
  standing: OpticalStanding;
  opacity: number;
  filter: string;
  badgeLabel: string | null;
  borderClass: string;
}

/**
 * Resolves optical material properties according to ATROPOS "territory as material" HIG rules:
 * - In-territory: 100% opacity, sharp font, emerald/accent border.
 * - Out-of-territory: 60% opacity, grayscale desaturation (grayscale(100%)), textual badge "Outside territory".
 * - Attestation drift: 100% opacity, amber warning border, textual badge "Attestation drift".
 */
export function resolveOpticalStanding(
  filePath: string,
  declaredTerritories: string[],
  isAttested: boolean = true,
  isDrifted: boolean = false
): OpticalMaterialProperties {
  const normalizedPath = filePath.replace(/\\/g, "/").replace(/^\/+/, "");

  const inTerritory =
    declaredTerritories.length === 0 ||
    declaredTerritories.some((territory) => {
      const normalizedTerritory = territory.replace(/\\/g, "/").replace(/^\/+/, "").replace(/\/+$/, "");
      return (
        normalizedTerritory === "" ||
        normalizedTerritory === "." ||
        normalizedPath === normalizedTerritory ||
        normalizedPath.startsWith(normalizedTerritory + "/")
      );
    });

  if (!inTerritory) {
    return {
      standing: OpticalStanding.OUT_OF_TERRITORY_RECEDED,
      opacity: 0.6,
      filter: "grayscale(100%)",
      badgeLabel: "Outside territory",
      borderClass: "border-slate-800 opacity-60",
    };
  }

  if (isDrifted || !isAttested) {
    return {
      standing: OpticalStanding.ATTESTATION_DRIFT_WARNED,
      opacity: 1.0,
      filter: "none",
      badgeLabel: "Attestation drift",
      borderClass: "border-amber-500/80 shadow-[0_0_10px_rgba(245,158,11,0.2)]",
    };
  }

  return {
    standing: OpticalStanding.IN_TERRITORY_SHARP,
    opacity: 1.0,
    filter: "none",
    badgeLabel: "In territory",
    borderClass: "border-emerald-500/70 shadow-[0_0_8px_rgba(16,185,129,0.15)]",
  };
}
