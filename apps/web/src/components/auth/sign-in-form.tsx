"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import type { Route } from "next";
import Link from "next/link";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { createSupabaseBrowserClient } from "@/lib/auth/browser";
import { sanitizeReturnPath } from "@/lib/auth/redirects";

const schema = z.object({
  email: z.string().email(),
  password: z.string().min(8),
});

type Values = z.infer<typeof schema>;

export function SignInForm({ nextPath = "/projects" }: { nextPath?: string }) {
  const [message, setMessage] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "" } });
  const safeNext = sanitizeReturnPath(nextPath);

  async function onSubmit(values: Values) {
    setMessage(null);
    const supabase = createSupabaseBrowserClient();
    const { error } = await supabase.auth.signInWithPassword(values);
    if (error) {
      setMessage("Sign-in could not be completed. Check your credentials or try again later.");
      return;
    }
    window.location.assign(safeNext);
  }

  async function sendMagicLink() {
    const email = form.getValues("email");
    if (!z.string().email().safeParse(email).success) {
      form.setError("email", { message: "Enter a valid email first." });
      return;
    }
    const supabase = createSupabaseBrowserClient();
    await supabase.auth.signInWithOtp({ email, options: { emailRedirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(safeNext)}` } });
    setMessage("If the address can sign in, a secure link will be sent.");
  }

  return (
    <form className="sg-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Field id="email" label="Email" type="email" autoComplete="email" error={form.formState.errors.email?.message} {...form.register("email")} />
      <Field id="password" label="Password" type={showPassword ? "text" : "password"} autoComplete="current-password" error={form.formState.errors.password?.message} {...form.register("password")} />
      <Button type="button" variant="ghost" onClick={() => setShowPassword((value) => !value)} aria-pressed={showPassword}>
        {showPassword ? "Hide password" : "Show password"}
      </Button>
      {message ? <p role="status">{message}</p> : null}
      <Button type="submit" loading={form.formState.isSubmitting}>
        Sign in
      </Button>
      <Button type="button" variant="secondary" onClick={sendMagicLink}>
        Send magic link
      </Button>
      <Link href={"/auth/recovery" as Route}>Forgot password?</Link>
    </form>
  );
}
