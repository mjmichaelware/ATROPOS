#!/bin/bash
exec > >(tee /storage/emulated/0/Download/github_diagnostic_report.txt) 2>&1
echo "=== 1. GH AUTH STATUS ==="
gh auth status
echo "=== 2. GH AUTH TOKEN ==="
gh auth token || echo "No token returned"
echo "=== 3. GH API USER ==="
gh api user
echo "=== 4. GH API USER ORGS ==="
gh api user/orgs
echo "=== 5. GH API USER REPOS ==="
gh api user/repos --paginate --jq '.[].full_name'
echo "=== 6. GIT CONFIG LIST ==="
git config --list
echo "=== 7. GIT REMOTE -V ==="
git remote -v
echo "=== 8. GIT BRANCH -A ==="
git branch -a
echo "=== 9. GH SEARCH REPOS ATROPOS ==="
gh search repos ATROPOS --json fullName,owner,url
echo "=== 10. GH REPO VIEW MJMICHAELWARE/ATROPOS ==="
gh repo view MichaelAPWare/ATROPOS
echo "=== 11. GH REPO VIEW MICHAELAPWARE/ATROPOS ==="
gh repo view MichaelAPWare/ATROPOS
echo "=== 12. GH RELEASE LIST MJMICHAELWARE/ATROPOS ==="
gh release list -R MichaelAPWare/ATROPOS
echo "=== 13. GH RELEASE LIST MICHAELAPWARE/ATROPOS ==="
gh release list -R MichaelAPWare/ATROPOS
echo "=== 14. GH RUN LIST MJMICHAELWARE/ATROPOS ==="
gh run list -R MichaelAPWare/ATROPOS --limit 10
echo "=== 15. GH RUN LIST MICHAELAPWARE/ATROPOS ==="
gh run list -R MichaelAPWare/ATROPOS --limit 10
