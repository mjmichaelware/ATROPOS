"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { projectRoute } from "@/components/navigation/routes";
import { createProjectApiClient, createProject } from "@/lib/projects/api";
import { projectCreateSchema } from "@/lib/projects/schemas";
import { writeRecentProjectId } from "@/lib/projects/selection";

const formSchema = projectCreateSchema.omit({ slug: true });
type FormValues = { name: string; description: string };

function slugify(name: string): string {
  const slug = name
    .toLowerCase()
    .trim()
    .replace(/[^a-z0-9]+/g, "-")
    .replace(/^-+|-+$/g, "")
    .slice(0, 80);
  return slug || "project";
}

export function ProjectCreateForm() {
  const router = useRouter();
  const form = useForm<FormValues>({ resolver: zodResolver(formSchema), defaultValues: { name: "", description: "" } });
  async function onSubmit(values: FormValues) {
    try {
      const response = await createProject(createProjectApiClient(), { ...values, slug: slugify(values.name) });
      writeRecentProjectId(window.localStorage, response.body.id);
      router.push(projectRoute(response.body.id));
    } catch {
      form.setError("root", { message: "Project could not be created. Try a different name." });
    }
  }
  return (
    <form className="sg-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Field id="name" label="Name" error={form.formState.errors.name?.message} {...form.register("name")} />
      <Field id="description" label="Description" error={form.formState.errors.description?.message} {...form.register("description")} />
      {form.formState.errors.root?.message ? <p role="alert">{form.formState.errors.root.message}</p> : null}
      <Button type="submit" loading={form.formState.isSubmitting}>
        Create project
      </Button>
    </form>
  );
}
