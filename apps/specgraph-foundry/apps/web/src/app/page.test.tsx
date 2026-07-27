import { render, screen } from "@testing-library/react";
import { describe, expect, it, vi } from "vitest";
import Home from "./page";
import NotFound from "./not-found";

vi.mock("@/lib/auth/server", () => ({
  getVerifiedUser: () => Promise.resolve(null),
}));

vi.mock("next/navigation", () => ({
  redirect: vi.fn(),
}));

describe("root page", () => {
  it("renders unavailable fallback without live API", async () => {
    vi.stubGlobal("fetch", vi.fn(async () => Promise.reject(new Error("offline"))));
    render(await Home());
    expect(screen.getByText("Backend Unavailable")).toBeInTheDocument();
    expect(screen.getByRole("heading", { name: "Authority-first delivery starts here." })).toBeInTheDocument();
  });

  it("renders not-found state", () => {
    render(<NotFound />);
    expect(screen.getByRole("heading", { name: "Not found" })).toBeInTheDocument();
  });
});
