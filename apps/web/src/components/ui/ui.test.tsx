import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { describe, expect, it, vi } from "vitest";
import { Button } from "./button";
import { Input } from "./input";
import { Label } from "./label";
import { StatusBadge } from "./status-badge";

describe("ui primitives", () => {
  it("supports accessible button states", async () => {
    const onClick = vi.fn();
    render(<Button onClick={onClick}>Run</Button>);
    await userEvent.click(screen.getByRole("button", { name: "Run" }));
    expect(onClick).toHaveBeenCalledOnce();
    render(<Button loading>Run</Button>);
    expect(screen.getByRole("button", { name: "Working..." })).toHaveAttribute("aria-busy", "true");
  });

  it("associates label, input, description, and error", () => {
    render(
      <>
        <Label htmlFor="name">Name</Label>
        <p id="name-help">Project display name</p>
        <p id="name-error">Required</p>
        <Input id="name" descriptionId="name-help" errorId="name-error" />
      </>,
    );
    const input = screen.getByLabelText("Name");
    expect(input).toHaveAttribute("aria-describedby", "name-help name-error");
    expect(input).toHaveAttribute("aria-invalid", "true");
  });

  it("status is not color-only", () => {
    render(<StatusBadge tone="warning" label="Unavailable" />);
    expect(screen.getByText("Unavailable")).toBeInTheDocument();
  });
});
