import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { stubMatchMedia } from "@/test/match-media";
import { SplashIntro } from "./splash-intro";

describe("SplashIntro", () => {
  it("shows the intro and dismisses on the skip button", async () => {
    stubMatchMedia();
    render(<SplashIntro />);
    expect(screen.getByRole("button", { name: /skip intro/i })).toBeInTheDocument();
    await userEvent.click(screen.getByRole("button", { name: /skip intro/i }));
    expect(screen.queryByRole("button", { name: /skip intro/i })).not.toBeInTheDocument();
  });

  it("has no serious or critical axe violations", async () => {
    stubMatchMedia();
    const { container } = render(<SplashIntro />);
    await expectNoSeriousAxeViolations(container);
  });
});
