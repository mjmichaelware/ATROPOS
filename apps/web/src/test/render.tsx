import { render, type RenderOptions } from "@testing-library/react";
import type { ReactElement } from "react";
import { AppProviders } from "@/components/providers/app-providers";

export function renderWithProviders(ui: ReactElement, options?: RenderOptions) {
  return render(<AppProviders>{ui}</AppProviders>, options);
}
