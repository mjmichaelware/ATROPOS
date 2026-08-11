"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { createSupabaseBrowserClient } from "@/lib/auth/browser";

const schema = z
  .object({
    password: z.string().min(12).regex(/[A-Z]/).regex(/[a-z]/).regex(/[0-9]/),
    confirm: z.string(),
  })
  .refine((value) => value.password === value.confirm, { path: ["confirm"], message: "Passwords must match." });

export function UpdatePasswordForm() {
  const [message, setMessage] = useState<string | null>(null);
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { password: "", confirm: "" } });
  async function onSubmit(values: z.infer<typeof schema>) {
    const supabase = createSupabaseBrowserClient();
    const { error } = await supabase.auth.updateUser({ password: values.password });
    if (error) {
      setMessage("Password update could not be completed. Request a new recovery link.");
      return;
    }
    setMessage("Password updated. Sign in again to continue.");
    await supabase.auth.signOut();
  }
  return (
    <form className="sg-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Field id="password" label="New password" type="password" autoComplete="new-password" error={form.formState.errors.password?.message} description="Use at least 12 characters with uppercase, lowercase, and a number." {...form.register("password")} />
      <Field id="confirm" label="Confirm password" type="password" autoComplete="new-password" error={form.formState.errors.confirm?.message} {...form.register("confirm")} />
      {message ? <p role="status">{message}</p> : null}
      <Button type="submit" loading={form.formState.isSubmitting}>
        Update password
      </Button>
    </form>
  );
}
