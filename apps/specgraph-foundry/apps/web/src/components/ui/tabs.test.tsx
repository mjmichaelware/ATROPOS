import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { useState } from "react";
import { beforeEach, describe, expect, it } from "vitest";
import { stubMatchMedia } from "@/test/match-media";
import { Tabs } from "./tabs";

beforeEach(() => {
  stubMatchMedia();
});

function ControlledTabs() {
  const [value, setValue] = useState<"one" | "two" | "three">("one");
  return (
    <Tabs
      label="Example tabs"
      value={value}
      onChange={setValue}
      tabs={[
        { value: "one", label: "One", panel: <p>Panel one</p> },
        { value: "two", label: "Two", panel: <p>Panel two</p> },
        { value: "three", label: "Three", panel: <p>Panel three</p> },
      ]}
    />
  );
}

describe("Tabs keyboard behavior (WAI-ARIA APG, manual activation)", () => {
  it("only the selected tab is in the natural tab order", () => {
    render(<ControlledTabs />);
    expect(screen.getByRole("tab", { name: "One" })).toHaveAttribute("tabindex", "0");
    expect(screen.getByRole("tab", { name: "Two" })).toHaveAttribute("tabindex", "-1");
    expect(screen.getByRole("tab", { name: "Three" })).toHaveAttribute("tabindex", "-1");
  });

  it("ArrowRight/ArrowLeft move focus between tabs without activating them", async () => {
    const user = userEvent.setup();
    render(<ControlledTabs />);
    screen.getByRole("tab", { name: "One" }).focus();
    await user.keyboard("{ArrowRight}");
    expect(screen.getByRole("tab", { name: "Two" })).toHaveFocus();
    expect(screen.getByText("Panel one")).toBeInTheDocument();
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("tab", { name: "One" })).toHaveFocus();
  });

  it("ArrowRight wraps from the last tab to the first", async () => {
    const user = userEvent.setup();
    render(<ControlledTabs />);
    screen.getByRole("tab", { name: "One" }).focus();
    await user.keyboard("{ArrowLeft}");
    expect(screen.getByRole("tab", { name: "Three" })).toHaveFocus();
  });

  it("Home and End jump to the first and last tab", async () => {
    const user = userEvent.setup();
    render(<ControlledTabs />);
    screen.getByRole("tab", { name: "Two" }).focus();
    await user.keyboard("{End}");
    expect(screen.getByRole("tab", { name: "Three" })).toHaveFocus();
    await user.keyboard("{Home}");
    expect(screen.getByRole("tab", { name: "One" })).toHaveFocus();
  });

  it("Enter/Space activates the focused tab and shows its panel", async () => {
    const user = userEvent.setup();
    render(<ControlledTabs />);
    screen.getByRole("tab", { name: "One" }).focus();
    await user.keyboard("{ArrowRight}");
    await user.keyboard("{Enter}");
    expect(screen.getByRole("tab", { name: "Two" })).toHaveAttribute("aria-selected", "true");
    expect(screen.getByText("Panel two")).toBeInTheDocument();
  });

  it("exposes an accurate tab/panel control relationship via aria-controls and aria-labelledby", () => {
    render(<ControlledTabs />);
    const tab = screen.getByRole("tab", { name: "One" });
    const panel = screen.getByRole("tabpanel");
    expect(tab).toHaveAttribute("aria-controls", panel.id);
    expect(panel).toHaveAttribute("aria-labelledby", tab.id);
  });
});
