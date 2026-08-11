PROJECT_ID="specgraph-foundry"

export SUPABASE_SERVICE_ROLE_KEY="$(
  gcloud secrets versions access latest \
    --project "$PROJECT_ID" \
    --secret SUPABASE_SERVICE_ROLE_KEY
)"

export SUPABASE_URL="$(
  gcloud secrets versions access latest \
    --project "$PROJECT_ID" \
    --secret SUPABASE_URL
)"

export PYTHONPATH="$PWD/src"

echo "SUPABASE_URL=$SUPABASE_URL"
echo "service role key length: ${#SUPABASE_SERVICE_ROLE_KEY}"
