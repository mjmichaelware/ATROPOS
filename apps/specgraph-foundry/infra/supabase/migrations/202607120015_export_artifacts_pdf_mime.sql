-- The export-artifacts bucket's allowed_mime_types was restricted to
-- application/json, text/markdown, and text/plain. Every export now
-- also generates implementation_blueprint.pdf (application/pdf), which
-- Supabase Storage would reject as an unsupported MIME type on upload -
-- the export would fail at the storage step instead of ever being
-- marked durable/verified. The bucket insert is already an idempotent
-- upsert (on conflict (id) do update set allowed_mime_types = ...), so
-- re-running it with the corrected array updates the live bucket
-- configuration in place.

insert into storage.buckets(
    id,
    name,
    public,
    file_size_limit,
    allowed_mime_types
)
values(
    'export-artifacts',
    'export-artifacts',
    false,
    10485760,
    array[
        'application/json',
        'text/markdown',
        'text/plain',
        'application/pdf'
    ]::text[]
)
on conflict (id) do update
set public = excluded.public,
    file_size_limit = excluded.file_size_limit,
    allowed_mime_types = excluded.allowed_mime_types;
