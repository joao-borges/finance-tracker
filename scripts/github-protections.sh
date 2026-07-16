#!/usr/bin/env bash
# One-shot GitHub hardening for the public repo. Run once, right after making
# the repository public (rulesets and fork-PR approval are public-repo features).
#
#   ./scripts/github-protections.sh [owner/repo]
set -euo pipefail

REPO="${1:-$(gh repo view --json nameWithOwner -q .nameWithOwner)}"

echo "==> Branch ruleset on ${REPO}: PRs to main need owner review + green CI (admins bypass)"
gh api -X POST "repos/${REPO}/rulesets" --input - <<'JSON'
{
  "name": "protect-main",
  "target": "branch",
  "enforcement": "active",
  "conditions": { "ref_name": { "include": ["~DEFAULT_BRANCH"], "exclude": [] } },
  "rules": [
    { "type": "deletion" },
    { "type": "non_fast_forward" },
    {
      "type": "pull_request",
      "parameters": {
        "required_approving_review_count": 1,
        "dismiss_stale_reviews_on_push": true,
        "require_code_owner_review": true,
        "require_last_push_approval": false,
        "required_review_thread_resolution": false,
        "allowed_merge_methods": ["merge", "squash", "rebase"]
      }
    },
    {
      "type": "required_status_checks",
      "parameters": {
        "strict_required_status_checks_policy": false,
        "required_status_checks": [ { "context": "build" } ]
      }
    }
  ],
  "bypass_actors": [
    { "actor_id": 5, "actor_type": "RepositoryRole", "bypass_mode": "always" }
  ]
}
JSON

echo "==> Fork-PR workflows always need approval from you before running"
gh api -X PUT "repos/${REPO}/actions/permissions/fork-pr-contributor-approval" \
    -f approval_policy=all_external_contributors

echo "==> Done."
