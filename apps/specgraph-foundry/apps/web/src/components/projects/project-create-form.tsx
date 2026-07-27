"use client";

import { zodResolver } from "@hookform/resolvers/zod";
import { useRouter } from "next/navigation";
import { useForm } from "react-hook-form";
import { Button } from "@/components/ui/button";
import { Field } from "@/components/ui/field";
import { projectRoute } from "@/components/navigation/routes";
import { createProjectApiClient, createProject } from "@/lib/projects/api";
import { projectCreateSchema, type ProjectCreateInput } from "@/lib/projects/schemas";
import { writeRecentProjectId } from "@/lib/projects/selection";

export function ProjectCreateForm() {
  const router = useRouter();
  const form = useForm<ProjectCreateInput>({ resolver: zodResolver(projectCreateSchema), defaultValues: { slug: "", name: "", description: "" } });
  async function onSubmit(values: ProjectCreateInput) {
    try {
      const response = await createProject(createProjectApiClient(), values);
      writeRecentProjectId(window.localStorage, response.body.id);
      router.push(projectRoute(response.body.id));
    } catch {
      form.setError("root", { message: "Project could not be created. Check the slug and try again." });
    }
  }
  return (
    <form className="sg-form" onSubmit={form.handleSubmit(onSubmit)} noValidate>
      <Field id="slug" label="Slug" error={form.formState.errors.slug?.message} {...form.register("slug")} />
      <Field id="name" label="Name" error={form.formState.errors.name?.message} {...form.register("name")} />
      <Field id="description" label="Description" error={form.formState.errors.description?.message} {...form.register("description")} />
      {form.formState.errors.root?.message ? <p role="alert">{form.formState.errors.root.message}</p> : null}
      <Button type="submit" loading={form.formState.isSubmitting}>
        Create project
      </Button>
    </form>
  );
}
