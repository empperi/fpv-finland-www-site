# Slack -> website sync (maintainer guide)

A weekly GitHub Actions job (`.github/workflows/slack-sync.yml`) turns Slack threads into a **pull request**
that adds or updates events (`events.edn`) and drafts new articles (`resources/public/posts/`). It never pushes
to `master`; a human reviews and merges the PR.

## How it works

1. Lists public channels, joins the ones the bot is not in, reads history for the last `SYNC_LOOKBACK_DAYS` (60).
2. A root message is picked up when a user on the allowlist added the `to-www-site` reaction and the bot has not
   yet added `white_check_mark` (that is the "already processed" marker). Reactions on replies inside a thread are
   not looked at: react on the first message of the thread.
3. The whole thread is anonymised (user ids, names, e-mails removed; authors become "Jäsen 1", "Jäsen 2", ...)
   and sent to Gemini, one request per thread. Permalinks are only used in the PR body.
4. Gemini returns structured JSON (`events`, `event_updates`, `articles`, `none_reason`; required fields are enforced by the response schema and converted internally to add_event/update_event/add_article/none). The Clojure code validates
   everything (dates, lengths, no HTML, no `[:plugin` syntax, only http/https links, update targets must match
   exactly once) and writes the files itself. Gemini never edits files. Rejections are listed in the PR body.
5. After the PR is created (`mark-done` needs only `SLACK_BOT_TOKEN`), `lein slack-sync mark-done sync-result/processed.json` adds the checkmark reaction.
   Threads that failed because of API/network/malformed-output errors are not marked and are retried next run.
   Threads whose output failed validation are marked (to avoid retry loops) and flagged in the PR body.
   If a run makes no file changes, only threads Gemini classified as "nothing to publish" are marked.

`events.edn` formatting is preserved: a new event is inserted before the first later-dated entry (the file is
assumed to be roughly date sorted), otherwise appended at the end; updates replace only that entry's block.
Every write is re-read with `clojure.edn` and compared with the expected data; a mismatch aborts the run.

## Debugging

`SYNC_DEBUG=true` (local troubleshooting only; do not set it in the workflow) writes each Gemini prompt and raw
response to `sync-result/debug/<channel>-<ts>-<n>.txt` / `.json` (n = 1 initial, 2 repair). The files contain the
anonymised thread text; `sync-result/` is git-ignored.

## Run time

On the free Gemini tier a single thread can take several minutes. The log prints progress lines:
`gemini: thread C.../...: request 1 (initial) ...` when a request starts, `done in N s` (or `failed in N s`) when
it ends, and `request 2 (repair)` for the single repair attempt after validation errors. `gemini: HTTP 429, retrying
in N s (2/3)` means rate limiting; `timed out after 300 s, retrying (2/2)` means the request timed out (Gemini:
300 s timeout, a timeout is retried once). Slack calls use a 90 s timeout. The job times out after 90 minutes.

## Slack app setup

1. https://api.slack.com/apps -> Create New App -> From scratch.
2. OAuth & Permissions -> Bot Token Scopes:
   `channels:read`, `channels:history`, `channels:join`, `reactions:read`, `reactions:write`.
   (No `users:read`: user profiles are deliberately never fetched.)
3. Install to workspace, copy the **Bot User OAuth Token** (`xoxb-...`).
4. Add the custom emoji `:to-www-site:` (Customize workspace -> Emoji). `:white_check_mark:` is a standard emoji.
   Names can be changed with `SYNC_REACTION` / `SYNC_DONE_REACTION`.
5. The bot only sees public channels. It joins them automatically (that is visible to members as "added an
   integration"/joined message).

Finding Slack user IDs for the allowlist: open the person's profile in Slack -> `...` (More) -> "Copy member ID"
(looks like `U01ABCDEF`). Put a comma separated list in the `SLACK_ALLOWED_USER_IDS` repository variable.

## GitHub configuration

Secrets (Settings -> Secrets and variables -> Actions):

| Name | Purpose |
|------|---------|
| `SLACK_BOT_TOKEN` | Slack bot token |
| `GEMINI_API_KEY` | Google AI Studio API key |
| `BOT_PR_TOKEN` | Fine-grained PAT or GitHub App token with Contents and Pull requests read/write on this repo. Needed because PRs opened with the default `GITHUB_TOKEN` do not trigger the Firebase preview workflow. |

Variables:

| Name | Purpose |
|------|---------|
| `SLACK_ALLOWED_USER_IDS` | Comma separated Slack member IDs whose reactions count |
| `GEMINI_MODEL` | Gemini model name, e.g. `gemini-2.5-flash` (default if empty) |

Optional environment variables (workflow `env`): `SYNC_LOOKBACK_DAYS`, `SYNC_REACTION`, `SYNC_DONE_REACTION`.

The schedule is Saturday 21:00 UTC. Run it manually from the Actions tab (`workflow_dispatch`); tick **dry_run**
to see the diff and PR body in the job summary without any push, PR or Slack write.

## Running locally

```
export SLACK_BOT_TOKEN=xoxb-...
export GEMINI_API_KEY=...
export SLACK_ALLOWED_USER_IDS=U01ABCDEF,U02GHIJKL
export GEMINI_MODEL=gemini-2.5-flash
export SYNC_DRY_RUN=true      # no Slack writes (no joins, no reactions)
lein slack-sync               # edits events.edn / posts in the working tree, writes sync-result/
git diff
```

`sync-result/pr-body.md` and `sync-result/processed.json` (git-ignored) are the PR text and the list of messages
to mark. Revert the working tree afterwards with `git checkout -- events.edn && git clean -fd resources/public/posts`.

Offline demo with recorded responses on a scratch copy of the repo files:

```
lein slack-sync --fixture test/fixtures/slack-sync.json --root /path/to/copy
```

The sync code lives in `src/fpvfinland/sync/` and is not loaded by the site build. `lein test` covers it without
network access.
