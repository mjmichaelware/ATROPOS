"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { createSupabaseBrowserClient } from "@/lib/auth/browser";
import { sanitizeReturnPath } from "@/lib/auth/redirects";

const schema = z
  .object({
    email: z.string().email(),
    password: z.string().min(12).regex(/[A-Z]/).regex(/[a-z]/).regex(/[0-9]/),
    confirm: z.string(),
  })
  .refine((value) => value.password === value.confirm, { path: ["confirm"], message: "Passwords must match." });

type Values = z.infer<typeof schema>;

export function SignUpForm({ nextPath = "/projects" }: { nextPath?: string }) {
  const [message, setMessage] = useState<string | null>(null);
  const [showPassword, setShowPassword] = useState(false);
  const form = useForm<Values>({ resolver: zodResolver(schema), defaultValues: { email: "", password: "", confirm: "" } });
  const safeNext = sanitizeReturnPath(nextPath);

  async function onSubmit(values: Values) {
    setMessage(null);
    const supabase = createSupabaseBrowserClient();
    const { data, error } = await supabase.auth.signUp({
      email: values.email,
      password: values.password,
      options: { emailRedirectTo: `${window.location.origin}/auth/callback?next=${encodeURIComponent(safeNext)}` },
    });
    if (error) {
      setMessage("Account could not be created. Check your details or try again later.");
      return;
    }
    if (data.session) {
      window.location.assign(safeNext);
      return;
    }
    setMessage("Check your email to confirm your account, then sign in.");
    form.reset();
  }

  return (
    <form className="sg-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Field id="email" label="Email" type="email" autoComplete="email" error={form.formState.errors.email?.message} {...form.register("email")} />
      <Field
        id="password"
        label="Password"
        type={showPassword ? "text" : "password"}
        autoComplete="new-password"
        error={form.formState.errors.password?.message}
        description="Use at least 12 characters with uppercase, lowercase, and a number."
        {...form.register("password")}
      />
      <Field
        id="confirm"
        label="Confirm password"
        type={showPassword ? "text" : "password"}
        autoComplete="new-password"
        error={form.formState.errors.confirm?.message}
        {...form.register("confirm")}
      />
      <Button type="button" variant="ghost" onClick={() => setShowPassword((value) => !value)} aria-pressed={showPassword}>
        {showPassword ? "Hide password" : "Show password"}
      </Button>
      {message ? <p role="status">{message}</p> : null}
      <Button type="submit" loading={form.formState.isSubmitting}>
        Create account
      </Button>
    </form>
  );
}
