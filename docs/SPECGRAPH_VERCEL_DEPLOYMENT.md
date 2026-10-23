# SpecGraph Foundry — Vercel Production Deployment Guide

## Overview
SpecGraph Foundry is an independent compiler and execution DAG platform maintained alongside the ATROPOS Sovereign Engine. It possesses its own separate Vercel production deployment lifecycle.

---

## Dual Production Topology

1. **Atropos Sovereign Web Workstation**:
   - Repository: `SWE-CLI-MJ/Atropos-SWE-CLI`
   - Vercel Root Directory: `apps/web`
   - Purpose: Real-time autonomous workstation, Claude Code console, Live Preview Studio (Google AI Studio parity), terminal mirror, and engine bridge.
   
2. **SpecGraph Foundry Production**:
   - Repository: `SWE-CLI-MJ/Atropos-SWE-CLI`
   - Vercel Root Directory: `apps/specgraph-foundry/apps/web` (or `apps/specgraph-foundry`)
   - Purpose: Visual DAG builder, source document compiler, research lattice, and handoff generator.

---

## Deploying SpecGraph Foundry to Vercel

### Step 1: Create a New Project in Vercel
1. Log into your Vercel Dashboard and click **"Add New..."** → **"Project"**.
2. Select the repository: `SWE-CLI-MJ/Atropos-SWE-CLI`.

### Step 2: Configure Root Directory
1. Expand **"Root Directory"** and click **Edit**.
2. Select: `apps/specgraph-foundry/apps/web` (or `apps/specgraph-foundry`).
3. Framework Preset: **Next.js**.

### Step 3: Environment Variables
Configure the following environment variables in Vercel:
- `NEXT_PUBLIC_SUPABASE_URL`: Your Supabase Project URL (e.g. `https://<id>.supabase.co`)
- `NEXT_PUBLIC_SUPABASE_ANON_KEY`: Your Supabase Public Anonymous Key
- `NEXT_PUBLIC_SPECGRAPH_API_URL`: Your deployed SpecGraph Python API URL or local bridge
- `ATROPOS_WEB_URL`: (Optional) URL of the Atropos Sovereign Web Workstation for cross-navigation

### Step 4: Deploy
Click **"Deploy"**. The build will run Next.js compilation, package the serverless runtime, and publish the live production URL.
