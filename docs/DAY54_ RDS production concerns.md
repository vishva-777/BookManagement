# Day 54 — RDS Production Concerns: Backups, Snapshots, Restore Practice (Beginner-Friendly Notes)

## Goal of the Day
Understand how RDS actually protects your data — and prove it by rehearsing a real disaster-recovery scenario: enable backups, create a snapshot, simulate an accidental change, restore from the snapshot, and verify the recovery actually worked.

---

## 1. The Core Analogy: Photocopying an Important Notebook

- Never copying your notebook → lose everything if it's destroyed.
- Photocopying once a month → recover most work, but lose anything written after the last copy.
- A machine that **automatically copies every page as you write** → recover almost everything, up to nearly the exact moment before a mistake.

RDS backups work like that automatic photocopier. But — and this is the key mental model for today — **the photocopier doesn't watch what you write and undo mistakes for you.** It just keeps copying, including your mistakes. Recovering from a mistake is something **you** must actively do.

---

## 2. Myth-Busting: What Backups Do NOT Do

**Backups do not automatically fix anything.** If someone accidentally deletes a row right now:
- AWS does **not** notice and auto-restore it.
- You must **actively** notice the problem, find a backup from before it happened, and **manually trigger a restore**.
- A restore creates a **brand new RDS instance** — it does **not** repair the existing one in place.

This is different from **Auto Scaling Groups** (which automatically replace unhealthy EC2 instances) or **Multi-AZ RDS deployments** (which automatically fail over to a standby during a crash — a separate, more advanced feature from backups, worth its own future day).

---

## 3. Two Types of RDS Backups

| Type | Who triggers it | How long it lasts |
|---|---|---|
| **Automated Backups** | AWS, on a schedule | Expires after the retention period (auto-deleted) |
| **Manual Snapshots** | You, whenever you want | Lasts forever until you manually delete it |

**Rule of thumb:** if you want to keep a backup permanently (e.g., before a risky change), always take a **manual snapshot** — automated backups will eventually age out and disappear.

---

## 4. Point-In-Time Recovery (PITR)

Analogy: a security camera that saves a full recording once a day, **plus** keeps a continuous log of every single event in between.

- AWS takes a **daily automated snapshot**.
- AWS also **continuously streams transaction logs** (every insert/update/delete) between snapshots.
- Result: you can restore to **almost any specific second** within your retention window — not just to the most recent daily snapshot.

---

## 5. The Restore Endpoint Gotcha

Restoring a snapshot or doing a PITR restore **creates a brand-new RDS instance** with a **new hostname/endpoint**. It does not overwrite the original.

**This means after any restore, you must:**
1. Verify the new instance has the correct data.
2. **Update your app's connection details** (host/endpoint) to point to the new instance.
3. Restart the app.

**Callback to Day 53:** because `DB_URL` is now built dynamically from `host`/`port`/`dbname` fields stored in Secrets Manager (instead of hardcoded), recovering from a real incident is much simpler now — just update the `host` field in the secret and restart. No hunting through code for hardcoded connection strings.

---

## 6. Hands-On Steps We Executed

### Step 1: Discovered Automated Backups Were OFF
Checked `vishva-database-3` → Maintenance & backups tab → found:
- Automated backups: **Disabled**
- Snapshots: **0**

This meant **zero backup protection existed** this whole time, despite assuming "AWS does it automatically." Important lesson: never assume — always check the actual settings.

### Step 2: Took a Manual Snapshot
- RDS Console → `vishva-database-3` → Take snapshot
- Name: `day54snapshot`
- Note: AWS snapshot names must be lowercase alphanumeric + hyphens only, can't end with a hyphen, no double hyphens, no underscores.

### Step 3: Enabled Automated Backups
- Databases → `vishva-database-3` → Modify → Backup section
- Tried to set retention to 7 days → **blocked**: *"The specified backup retention period exceeds the maximum available to free tier customers."*
- **Free tier caps automated backup retention at 1 day.** Set retention = 1, applied immediately.
- Real-world lesson: account tier/plan directly affects your disaster-recovery options — something to plan around even in paid production environments (cost vs. retention trade-offs).

### Step 4: Simulated a "Mistake"
Created a test record via the live app's API to act as data that should NOT appear after restoring an earlier snapshot:
```bash
curl -X POST http://localhost:8090/login -H "Content-Type: application/json" -d '{"username":"admin","password":"xxx"}'

curl -X POST http://localhost:8090/books \
  -H "Content-Type: application/json" \
  -H "Authorization: Bearer <token>" \
  -d '{"title":"DAY54_TEST_BOOK","description":"Testing backup restore","price":10}'
```
Confirmed it existed in the live database before proceeding.

### Step 5: Restored the Snapshot
- Snapshots → `day54snapshot` → Restore
- Gave the new instance a distinct identifier: `vishva-database-3-restore-test`
- Restore created a **second, independent RDS instance** — the original `vishva-database-3` was completely untouched throughout.

### Step 6: Verified the Restore
Connected directly via MySQL client from the EC2 bastion to the **new restored instance's endpoint**:
```bash
mysql -h vishva-database-3-restore-test.xxxxx.rds.amazonaws.com -u admin -p -e "SELECT * FROM Bookdb.books;"
```
Result: only pre-existing rows (`The Art of War`, `Good Book`) appeared — `DAY54_TEST_BOOK` was **absent**, exactly as expected, proving the restore captured the correct earlier point in time.

### Step 7: Cleaned Up
Deleted `vishva-database-3-restore-test` (Actions → Delete → typed the instance name to confirm → skipped final snapshot, since it was only a test instance). This stops ongoing compute charges for the extra instance.

---

## 7. Real Debugging / Discovery Along the Way

**Discovery: the RDS instance hosts multiple old databases.**
`SHOW DATABASES;` on `vishva-database-3` revealed several leftover databases from earlier practice projects: `Bookdb`, `coursedb`, `hospitaldb`, `studentdb`, `studentdb1`, `taskdb`, `taskmanager1`. This single RDS instance had been reused across many past days' exercises.

**Discovery: the app's actual database was misconfigured.**
When verifying the restore, `DAY54_TEST_BOOK` was checked against `Bookdb` — but it wasn't there, on either the original or restored instance. Querying `taskmanager1` instead showed it correctly. This revealed that the Secrets Manager secret (set up Day 53) had `dbname: taskmanager1`, which was simply what AWS auto-detected as the RDS instance's current default database at the time — not a deliberate choice.

**Root cause:** the intended, "real" database for this project was actually `Bookdb` (containing genuine earlier project data — "The Art of War," "Good Book"), while `taskmanager1` was an old leftover database from a different exercise that the app had been mistakenly writing into.

**Fix:** Updated the Secrets Manager secret's `dbname` field from `taskmanager1` → `Bookdb`, restarted the Docker container (no rebuild needed, since this is just a runtime-fetched value), and confirmed via API that the app now correctly serves `Bookdb`'s real data.

**Lesson:** Always verify *which* database your app is actually pointing to before trusting test results — especially on a shared RDS instance with many old databases from past exercises. A restore/backup process can be working perfectly while you're accidentally checking the wrong table entirely.

**Note on Day 53 notes:** Day 53's documentation correctly reflects what was configured *at that time* (`taskmanager1`) — it's an accurate historical record, not an error to rewrite. This Day 54 fix is documented here as the correction, the same way a real engineering team would log a fix as a new entry rather than editing history.

---

## 8. Final Proof Table

| Step | Result |
|---|---|
| Automated backups before today | Disabled (0 backups existed) |
| Manual snapshot taken | `day54snapshot` — success |
| Automated backups enabled | 1-day retention (free tier max) |
| Test record created | `DAY54_TEST_BOOK` in live DB |
| Snapshot restored | New instance `vishva-database-3-restore-test` created, original untouched |
| Restored instance checked | Test record **absent** — restore proven correct |
| Cleanup | Test instance deleted |
| Bonus fix | Corrected Secrets Manager `dbname` from `taskmanager1` → `Bookdb` |

---

## 9. Cleanup Checklist Going Forward

- Old, unused manual snapshots from earlier days (e.g., `vishva-database-11-snapshot`, `vishva-database-1111-snapshot`) are still sitting in the account and cost a small ongoing storage fee. Worth reviewing and deleting the ones no longer needed — this was noted but deferred, not yet done.
- `day54snapshot` is worth keeping for a few more days as a safety net, then can be deleted once confidence in ongoing automated backups is established.

---

## Key Takeaway (One-Line Summary)

**RDS won't rescue you automatically — automated backups and manual snapshots only exist if you turn them on and use them, restoring always creates a new instance you must manually switch your app to, and even a working backup/restore process can look "broken" if you're checking the wrong database — always verify what your app is actually connected to.**