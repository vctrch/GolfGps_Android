# Agent GitHub setup (human, one-time)

Gives Cursor agents a **contributor** identity for `git push` / `gh pr create` without using your personal login.

## 1. Machine user

1. Create a GitHub account for the agent (e.g. `communitygolf-agent`), or a GitHub App (advanced).
2. Invite it to `vctrch/GolfGps_Android` with **Write** (push branches + open PRs). Do not grant admin unless you need it.
3. Verify the account email (or use the `users.noreply.github.com` address GitHub shows for that user).

## 2. Token

Create a fine-grained PAT (or classic `repo`) for that user:

- Contents: Read and write  
- Pull requests: Read and write  
- Metadata: Read  

## 3. Local env (not committed)

```bash
cp .cursor/agent.env.example .cursor/agent.env
# edit .cursor/agent.env and paste GH_TOKEN=...
```

Load it in the shell Cursor’s Agent uses, e.g. in `~/.zshrc`:

```bash
[ -f "$HOME/Workbench/GolfGps_Android/.cursor/agent.env" ] && set -a && source "$HOME/Workbench/GolfGps_Android/.cursor/agent.env" && set +a
```

Or export `GH_TOKEN` in Cursor’s environment for Agent terminals.

## 4. Remote + gh

```bash
cd /Users/john/Workbench/GolfGps_Android
git remote set-url origin https://github.com/vctrch/GolfGps_Android.git
gh auth setup-git   # optional; GH_TOKEN alone is enough for push/PR when set
```

## 5. Cursor Settings

**Agents → Approvals & Execution → Network:** `sandbox.json + Defaults` (this repo’s `.cursor/sandbox.json` allows `api.github.com`).

## 6. Check

```bash
test -n "$GH_TOKEN" && echo "GH_TOKEN set" || echo "GH_TOKEN missing"
gh api user --jq .login
ssh -T git@github.com  # optional; not required for HTTPS agent flow
```

`gh api user` should print the **agent** login, not your personal account, when `GH_TOKEN` is the bot token.
