import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import { stubMatchMedia } from "@/test/match-media";
import Home from "./page";
import NotFound from "./not-found";

vi.mock("@/lib/auth/server", () => ({
  getVerifiedUser: () => Promise.resolve(null),
}));

vi.mock("next/navigation", () => ({
  redirect: vi.fn(),
}));

describe("root page", () => {
  it("renders the marketing landing page with sign-in and sign-up entry points", async () => {
    stubMatchMedia();
    render(await Home());
    expect(screen.getByRole("heading", { name: "SpecGraph", level: 1 })).toBeInTheDocument();
    expect(screen.getByRole("link", { name: "Sign in" })).toHaveAttribute("href", "/auth/sign-in");
    expect(screen.getByRole("link", { name: "Create account" })).toHaveAttribute("href", "/auth/sign-up");
  });

  it("renders not-found state", () => {
    render(<NotFound />);
    expect(screen.getByRole("heading", { name: "Not found" })).toBeInTheDocument();
  });
});
