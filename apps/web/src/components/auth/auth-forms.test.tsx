import { render, screen } from "@testing-library/react";
import userEvent from "@testing-library/user-event";
import { beforeEach, describe, expect, it, vi } from "vitest";
import { expectNoSeriousAxeViolations } from "@/test/axe";
import { RecoveryForm } from "./recovery-form";
import { SignInForm } from "./sign-in-form";
import { SignUpForm } from "./sign-up-form";
import { UpdatePasswordForm } from "./update-password-form";

const signInWithPassword = vi.fn();
const signInWithOtp = vi.fn();
const signUp = vi.fn();
const resetPasswordForEmail = vi.fn();
const updateUser = vi.fn();
const signOut = vi.fn();

vi.mock("@/lib/auth/browser", () => ({
  createSupabaseBrowserClient: () => ({
    auth: { signInWithPassword, signInWithOtp, signUp, resetPasswordForEmail, updateUser, signOut },
  }),
}));

beforeEach(() => {
  vi.stubGlobal("location", { assign: vi.fn(), origin: "http://localhost:3000" });
  signInWithPassword.mockResolvedValue({ error: null });
  signInWithOtp.mockResolvedValue({ error: null });
  signUp.mockResolvedValue({ data: { session: null }, error: null });
  resetPasswordForEmail.mockResolvedValue({ error: null });
  updateUser.mockResolvedValue({ error: null });
  signOut.mockResolvedValue({ error: null });
});

describe("auth forms", () => {
  it("signs in with email and password without exposing tokens", async () => {
    render(<SignInForm nextPath="/projects" />);
    await userEvent.type(screen.getByLabelText("Email"), "owner@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "password123");
    await userEvent.click(screen.getByRole("button", { name: "Sign in" }));
    expect(signInWithPassword).toHaveBeenCalledWith({ email: "owner@example.com", password: "password123" });
  });

  it("magic link and recovery use generic non-enumerating confirmations", async () => {
    const signIn = render(<SignInForm nextPath="/projects" />);
    await userEvent.type(screen.getByLabelText("Email"), "owner@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send magic link" }));
    expect(await screen.findByText("If the address can sign in, a secure link will be sent.")).toBeInTheDocument();
    signIn.unmount();

    render(<RecoveryForm />);
    await userEvent.type(screen.getByLabelText("Email"), "owner@example.com");
    await userEvent.click(screen.getByRole("button", { name: "Send recovery email" }));
    expect(await screen.findByText("If the address can recover access, instructions will be sent.")).toBeInTheDocument();
  });

  it("creates an account with matching passwords and shows a confirmation prompt", async () => {
    render(<SignUpForm nextPath="/projects" />);
    await userEvent.type(screen.getByLabelText("Email"), "owner@example.com");
    await userEvent.type(screen.getByLabelText("Password"), "Longpassword1");
    await userEvent.type(screen.getByLabelText("Confirm password"), "Longpassword1");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(signUp).toHaveBeenCalledWith({
      email: "owner@example.com",
      password: "Longpassword1",
      options: { emailRedirectTo: "http://localhost:3000/auth/callback?next=%2Fprojects" },
    });
    expect(await screen.findByText("Check your email to confirm your account, then sign in.")).toBeInTheDocument();
  });

  it("validates matching passwords on sign-up", async () => {
    render(<SignUpForm nextPath="/projects" />);
    await userEvent.type(screen.getByLabelText("Password"), "Longpassword1");
    await userEvent.type(screen.getByLabelText("Confirm password"), "Differentpass1");
    await userEvent.click(screen.getByRole("button", { name: "Create account" }));
    expect(await screen.findByText("Passwords must match.")).toBeInTheDocument();
    expect(signUp).not.toHaveBeenCalled();
  });

  it("validates password update confirmation", async () => {
    render(<UpdatePasswordForm />);
    await userEvent.type(screen.getByLabelText("New password"), "Longpassword1");
    await userEvent.type(screen.getByLabelText("Confirm password"), "Differentpass1");
    await userEvent.click(screen.getByRole("button", { name: "Update password" }));
    expect(await screen.findByText("Passwords must match.")).toBeInTheDocument();
  });

  it("has no serious or critical axe violations", async () => {
    const { container } = render(<SignInForm nextPath="/projects" />);
    await expectNoSeriousAxeViolations(container);
  });

  it("sign-up form has no serious or critical axe violations", async () => {
    const { container } = render(<SignUpForm nextPath="/projects" />);
    await expectNoSeriousAxeViolations(container);
  });
});
