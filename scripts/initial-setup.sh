#!/usr/bin/env bash
#
# initial-setup.sh — one-shot, re-runnable bootstrap for a fresh finance-tracker
# deployment. Talks to a RUNNING instance over its REST API and:
#
#   1. (optional) connects + syncs SimpleFIN
#   2. imports the PC Financial Mastercard CSV (it's CSV-only for now)
#   3. shapes the accounts to match the Monarch layout (rename / type / logo /
#      merge supplementary cards / hide investment + cash accounts)
#   4. creates the June 2026 budget from the Monarch plan
#   5. prints the resulting budget + account summary
#
# Every step is idempotent, so re-running it on the server is safe. The hard
# "June 1 2026" import floor is enforced by the APP (set IMPORT_MIN_POSTED_DATE
# in the app's environment — see docker-compose.yml), not here.
#
# Requirements: bash, curl, jq.
#
# Usage:
#   BASE_URL=http://localhost:8080 \
#   CSV_FILE=~/Downloads/report.csv \
#   SIMPLEFIN_TOKEN=... \            # optional; omit if already connected
#   ./scripts/initial-setup.sh
#
set -euo pipefail

BASE_URL="${BASE_URL:-http://localhost:8080}"
SCRIPT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")" && pwd)"
CSV_FILE="${CSV_FILE:-${SCRIPT_DIR}/report.csv}"
BUDGET_MONTH="${BUDGET_MONTH:-2026-06}"
# Credit-card balances are negative in our signed convention (debt owed), to
# match what SimpleFIN returns for the other cards.
PC_BALANCE="${PC_BALANCE:--3352.67}"
SIMPLEFIN_TOKEN="${SIMPLEFIN_TOKEN:-}"

# --- June 2026 budget (category name | planned $), from the Monarch plan ---
BUDGET=(
    "Paychecks|11519"
    "Motorhome|400"
    "Car Insurance|351"
    "Auto Payment|758"
    "Rent|3155"
    "Home Insurance|72"
    "Internet & Cable|73"
    "Phone|127"
    "Groceries|1200"
    "Travel & Vacation|191"
    "Entertainment/Dining|600"
    "Pets|120"
    "Subscriptions|219"
    "Gym|97"
    "Fun and Stuff|200"
    "Daycare|673"
    "Swim|91"
    "Medical|464"
    "Fitness|126"
    "Financial Fees|33"
    "CC Debt|1599"
    "Financed Airline Ticket|467"
    "Miscellaneous|400"
)

# --- Desired account shape (Monarch). match=case-insensitive substring of the
#     current account name; the row is applied with a single PATCH. ---
#     match|new name|type|website
ACCOUNT_SPECS=(
    "Signature No Limit|RBC Signature No Limit Banking (...6459)|CHECKING|rbcroyalbank.com"
    "Cobalt|Amex Cobalt|CREDIT_CARD|americanexpress.com"
    "Platinum|Amex Platinum|CREDIT_CARD|americanexpress.com"
    "More Rewards|More Rewards RBC Visa Infinite (...3551)|CREDIT_CARD|rbcroyalbank.com"
    "Personal Loan|Personal Loan (...9001)|LOAN|rbcroyalbank.com"
)

# Merge supplementary cards into their main card: sourceMatch -> targetMatch.
# Adjust these to your real SimpleFIN account names if they differ.
MERGES=(
    "1012|Cobalt"
    "1019|Platinum"
)

# Accounts Monarch doesn't show — hidden so the lists match (investments + cash).
HIDE_PATTERNS=("CASH" "FHSA" "RRSP" "HISA" "RESP" "TFSA" "SELF_DIRECTED" "MANAGED" "AUTOMATED")

log()  { printf '\033[1;34m==>\033[0m %s\n' "$*"; }
warn() { printf '\033[1;33mWARN\033[0m %s\n' "$*" >&2; }
die()  { printf '\033[1;31mERROR\033[0m %s\n' "$*" >&2; exit 1; }

for tool in curl jq; do
    command -v "$tool" >/dev/null 2>&1 || die "$tool is required"
done

# ----------------------------------------------------------------------------
log "Waiting for API at ${BASE_URL} ..."
for _ in $(seq 1 60); do
    if curl -sf "${BASE_URL}/api/ping" >/dev/null 2>&1; then
        break
    fi
    sleep 1
done
curl -sf "${BASE_URL}/api/ping" >/dev/null 2>&1 || die "API never became ready"

# ----------------------------------------------------------------------------
# 1. SimpleFIN (optional)
if [[ -n "${SIMPLEFIN_TOKEN}" ]]; then
    log "Connecting SimpleFIN ..."
    curl -sf -X POST "${BASE_URL}/api/simplefin/setup" \
        -H 'Content-Type: application/json' \
        -d "$(jq -nc --arg t "${SIMPLEFIN_TOKEN}" '{token:$t}')" >/dev/null \
        || warn "SimpleFIN setup failed (token already used or invalid?)"
fi

if curl -sf "${BASE_URL}/api/simplefin/status" 2>/dev/null | jq -e '.connected' >/dev/null 2>&1; then
    log "Syncing SimpleFIN ..."
    sync_res="$(curl -sf -X POST "${BASE_URL}/api/simplefin/sync" || true)"
    if [[ -n "${sync_res}" ]]; then
        echo "    new=$(jq -r '.newCount' <<<"$sync_res") dedup=$(jq -r '.dedupCount' <<<"$sync_res") accounts=$(jq -r '.accountCount' <<<"$sync_res")"
    fi
else
    log "SimpleFIN not connected — skipping sync (set SIMPLEFIN_TOKEN to connect)."
fi

# ----------------------------------------------------------------------------
# 2. PC Financial CSV
if [[ -f "${CSV_FILE}" ]]; then
    log "Importing PC Financial CSV: ${CSV_FILE}"
    imp="$(curl -sf -X POST "${BASE_URL}/api/imports/csv?format=PC_FINANCIAL" -F "file=@${CSV_FILE}")" \
        || die "CSV import failed"
    echo "    imported new=$(jq -r '.newCount' <<<"$imp") into $(jq -r '.accountCount' <<<"$imp") account(s)"
else
    warn "CSV file not found at ${CSV_FILE} — skipping PC Financial import"
fi

# ----------------------------------------------------------------------------
# 3. Shape accounts
accounts() { curl -sf "${BASE_URL}/api/accounts?includeMerged=true"; }

# id of the first account whose name contains $1 (case-insensitive)
account_id() {
    accounts | jq -r --arg m "$1" '
        [.[] | select((.name | ascii_downcase) | contains($m | ascii_downcase))][0].id // empty'
}

patch_account() {
    local id="$1" body="$2"
    curl -sf -X PATCH "${BASE_URL}/api/accounts/${id}" -H 'Content-Type: application/json' -d "$body" >/dev/null
}

log "Shaping accounts to the Monarch layout ..."

# PC Financial -> Mastercard •••• 7834 (CC, manual balance + logo).
# Matched by importRef (stable) since the display name changes after the first run.
pc_id="$(accounts | jq -r '[.[] | select(.importRef=="PC Financial")][0].id // empty')"
if [[ -n "${pc_id}" ]]; then
    patch_account "${pc_id}" "$(jq -nc --arg n "Mastercard •••• 7834" --argjson b "${PC_BALANCE}" \
        '{name:$n, type:"CREDIT_CARD", website:"pcfinancial.ca", balance:$b}')"
    echo "    PC Financial -> Mastercard •••• 7834 (\$${PC_BALANCE})"
fi

# SimpleFIN-sourced accounts
for spec in "${ACCOUNT_SPECS[@]}"; do
    IFS='|' read -r match newname type website <<<"$spec"
    id="$(account_id "$match")"
    if [[ -n "${id}" ]]; then
        patch_account "${id}" "$(jq -nc --arg n "$newname" --arg t "$type" --arg w "$website" \
            '{name:$n, type:$t, website:$w}')"
        echo "    [$match] -> $newname"
    else
        warn "no account matches '${match}' (not synced yet?) — skipping"
    fi
done

# Merge supplementary cards into their main card
for m in "${MERGES[@]}"; do
    IFS='|' read -r src tgt <<<"$m"
    src_id="$(account_id "$src")"
    tgt_id="$(account_id "$tgt")"
    if [[ -n "${src_id}" && -n "${tgt_id}" && "${src_id}" != "${tgt_id}" ]]; then
        already="$(accounts | jq -r --argjson id "$src_id" '.[] | select(.id==$id) | .mergedIntoId // empty')"
        if [[ -z "${already}" ]]; then
            curl -sf -X POST "${BASE_URL}/api/accounts/${src_id}/merge" \
                -H 'Content-Type: application/json' -d "$(jq -nc --argjson t "$tgt_id" '{targetId:$t}')" >/dev/null \
                && echo "    merged [$src] into [$tgt]" || warn "merge [$src]->[$tgt] failed"
        fi
    fi
done

# Hide investment + cash accounts Monarch doesn't list
for pat in "${HIDE_PATTERNS[@]}"; do
    while read -r id; do
        [[ -n "${id}" ]] || continue
        patch_account "${id}" '{"hidden":true}'
        echo "    hid account matching '${pat}'"
    done < <(accounts | jq -r --arg m "$pat" '.[] | select((.name|ascii_downcase)|contains($m|ascii_downcase)) | .id')
done

# ----------------------------------------------------------------------------
# 4. June budget
log "Creating ${BUDGET_MONTH} budget ..."
cats="$(curl -sf "${BASE_URL}/api/categories")" || die "could not load categories"
entries='[]'
missing=0
for row in "${BUDGET[@]}"; do
    IFS='|' read -r name amount <<<"$row"
    id="$(jq -r --arg n "$name" 'map(select(.name==$n))[0].id // empty' <<<"$cats")"
    if [[ -n "${id}" ]]; then
        entries="$(jq -c --argjson id "$id" --argjson amt "${amount}" \
            '. + [{categoryId:$id, plannedAmount:$amt}]' <<<"$entries")"
    else
        warn "budget category not found, skipping: ${name}"
        missing=$((missing + 1))
    fi
done
sent="$(jq 'length' <<<"$entries")"
curl -sf -X PUT "${BASE_URL}/api/budgets/${BUDGET_MONTH}" \
    -H 'Content-Type: application/json' -d "$entries" >/dev/null || die "budget PUT failed"
echo "    set ${sent} budget line(s) (${missing} category name(s) unmatched)"

# ----------------------------------------------------------------------------
# 5. Summary
log "Done. ${BUDGET_MONTH} budget summary:"
curl -sf "${BASE_URL}/api/budgets/${BUDGET_MONTH}/summary" | jq -r '
    "    planned income : \(.plannedIncome // 0)",
    "    planned expense: \(.plannedExpense // 0)",
    "    actual income  : \(.actualIncome // 0)",
    "    actual expense : \(.actualExpense // 0)"'

log "Visible accounts (hidden excluded, as on the dashboard):"
curl -sf "${BASE_URL}/api/accounts" | jq -r '.[] | select(.hidden|not) | "    \(.name)  [\(.type)]  bal=\(.balance // "n/a")"'
