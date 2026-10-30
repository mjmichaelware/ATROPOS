"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useState } from "react";
import { useForm } from "react-hook-form";
import { z } from "zod";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { createSupabaseBrowserClient } from "@/lib/auth/browser";

const schema = z.object({ email: z.string().email() });

export function RecoveryForm() {
  const [sent, setSent] = useState(false);
  const form = useForm<z.infer<typeof schema>>({ resolver: zodResolver(schema), defaultValues: { email: "" } });
  async function onSubmit(values: z.infer<typeof schema>) {
    const supabase = createSupabaseBrowserClient();
    await supabase.auth.resetPasswordForEmail(values.email, { redirectTo: `${window.location.origin}/auth/update-password` });
    setSent(true);
  }
  return (
    <form className="sg-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Field id="email" label="Email" type="email" autoComplete="email" error={form.formState.errors.email?.message} {...form.register("email")} />
      {sent ? <p role="status">If the address can recover access, instructions will be sent.</p> : null}
      <Button type="submit" loading={form.formState.isSubmitting}>
        Send recovery email
      </Button>
    </form>
  );
}
