# Day 57 — S3 Advanced: Versioning & Lifecycle Policies (Beginner-Friendly Notes)

## Goal of the Day
Understand how S3 protects (or doesn't protect) files from accidental overwrites, and how to automatically manage storage costs for old file versions using lifecycle policies.

---

## 1. The Core Analogy: Library Book Copies

Imagine a library book with a return slip. Every time someone checks it out, edits it, and returns it, the librarian **doesn't destroy the old copy** — she keeps every past version on a shelf: "Version 1," "Version 2," etc. If the latest copy gets messed up, you can always go back and grab an older one.

**That's exactly what S3 Versioning does.**

---

## 2. Without Versioning: Permanent Data Loss

By default, S3 versioning is **OFF**. Uploading a file with the same name as an existing one **permanently overwrites** it — identical to saving over a file on your laptop. No undo, no backup.

**Proved this directly:**
```bash
echo "This is version 1 of my file" > testfile.txt
aws s3 cp testfile.txt s3://bucket/testfile.txt

echo "This is version 2 - the original is now gone" > testfile.txt
aws s3 cp testfile.txt s3://bucket/testfile.txt

aws s3 cp s3://bucket/testfile.txt download.txt
cat download.txt
# Output: "This is version 2 - the original is now gone"
```
Version 1 is gone with zero way to recover it.

---

## 3. Enabling Versioning

```bash
aws s3api put-bucket-versioning --bucket <bucket-name> --versioning-configuration Status=Enabled
```

Verify:
```bash
aws s3api get-bucket-versioning --bucket <bucket-name>
# {"Status": "Enabled"}
```

**With versioning ON**, repeating the same overwrite scenario:
```bash
echo "Fresh test - Version 1" > versiontest.txt
aws s3 cp versiontest.txt s3://bucket/versiontest.txt

echo "Fresh test - Version 2" > versiontest.txt
aws s3 cp versiontest.txt s3://bucket/versiontest.txt
```

Now **both** versions exist simultaneously in the bucket, each with a unique **Version ID**.

**Checking via console:** S3 Console → bucket → toggle **"Show versions"** (top of the object list) → reveals every version of every file, with the older ones shown indented under the current one.

---

## 4. Downloading: Latest vs. Specific Version

**Default download always gets the latest version:**
```bash
aws s3 cp s3://bucket/versiontest.txt latest.txt
cat latest.txt
# "Fresh test - Version 2"
```

**Recovering an OLD version requires its specific Version ID:**
```bash
aws s3api get-object --bucket <bucket-name> --key versiontest.txt --version-id <VERSION_ID> older-version.txt
cat older-version.txt
# "Fresh test - Version 1" — fully recovered!
```

**Key lesson:** anything overwritten *after* versioning is enabled can always be pulled back by its Version ID — a real safety net that plain S3 doesn't provide by default.

---

## 5. Why Lifecycle Policies Matter (Cost, Not Clutter)

Common misconception: versioning creates "clutter" that's hard to find. **Not true** — S3 always shows the latest version by default in normal browsing; old versions stay out of the way.

**The real issue is cost.** Every version — old and new — takes up real storage space, and AWS bills for **all of it**, not just the latest. A file updated 100 times means 100 billed copies unless something cleans up the old ones.

**Lifecycle policies solve this:** automated rules that either:
1. **Transition** old versions to cheaper storage (e.g., Glacier — cold storage, cheaper but slower to access)
2. **Expire** (permanently delete) old versions after a set number of days

---

## 6. Building a Lifecycle Policy

**Simple version — delete old versions after 7 days:**
```json
{
  "Rules": [
    {
      "ID": "DeleteOldVersionsAfter7Days",
      "Status": "Enabled",
      "Filter": {},
      "NoncurrentVersionExpiration": {
        "NoncurrentDays": 7
      }
    }
  ]
}
```

**Key safety property:** `NoncurrentVersionExpiration` only ever targets **non-current (old)** versions. The current/latest version of any file is never touched by this rule — matching the library analogy of clearing old shelf copies while keeping the actively-borrowed book safe.

**Realistic production pattern — archive first, delete later:**
```json
{
  "Rules": [
    {
      "ID": "ArchiveThenDeleteOldVersions",
      "Status": "Enabled",
      "Filter": {},
      "NoncurrentVersionTransitions": [
        {
          "NoncurrentDays": 30,
          "StorageClass": "GLACIER"
        }
      ],
      "NoncurrentVersionExpiration": {
        "NoncurrentDays": 90
      }
    }
  ]
}
```
- **Day 30:** old version moves to Glacier (cheap, cold storage)
- **Day 90:** old version permanently deleted, even from Glacier

**Apply and verify:**
```bash
aws s3api put-bucket-lifecycle-configuration --bucket <bucket-name> --lifecycle-configuration file://lifecycle-policy.json
aws s3api get-bucket-lifecycle-configuration --bucket <bucket-name>
```

**Important operational note:** lifecycle rules don't run instantly — AWS evaluates them **once per day** in the background. Setting "7 days" doesn't mean deletion happens at the exact second the file turns 7 days old; it happens on AWS's own daily schedule.

---

## 7. Real Typos Debugged Along the Way

AWS's parameter validation caught real mistakes before anything broke:

| Typo | Correct | How it was caught |
|---|---|---|
| `"Eanbled"` | `"Enabled"` | Would have silently failed to activate — caught before applying |
| `"NoncurrentVersionsExpiration"` (extra `s`) | `"NoncurrentVersionExpiration"` | `Parameter validation failed: Unknown parameter... must be one of: ...` |

**Lesson:** AWS CLI's parameter validation is a real safety net — it lists the exact valid parameter names when you get one wrong, similar to how `terraform plan` catches HCL syntax errors before touching real infrastructure.

---

## 8. A CLI Quirk Worth Knowing

`aws s3api list-object-versions --bucket <name> --prefix <key>` returned a mysterious `badly formed help string` error, even with correctly formed commands and flags. Confirmed via `aws s3api list-buckets` that the CLI installation itself was healthy — this was isolated specifically to that one subcommand (likely a CLI version-specific bug, v2.31.35).

**Workaround used:** the S3 **web console's "Show versions" toggle** gives the same information reliably, and is often the simpler path when a specific CLI subcommand misbehaves.

---

## 9. Cleanup Gotcha: Versioned Buckets Won't Delete Easily

```bash
aws s3 rm s3://<bucket> --recursive
```
This only deletes **current** versions. Attempting `aws s3api delete-bucket` afterward failed:
```
BucketNotEmpty: The bucket you tried to delete is not empty. You must delete all versions in the bucket.
```

**This is actually a live demonstration of versioning working correctly** — old versions genuinely persist even after a normal delete. Cleanup required explicitly removing all versions via the S3 console (toggle "Show versions" → select all → Delete), only then did `delete-bucket` succeed.

**Lesson:** a versioned bucket is never truly "empty" from a normal `rm`, until every version of every object is explicitly removed.

---

## Key Takeaway (One-Line Summary)

**S3 Versioning turns "overwritten and gone forever" into "always recoverable by Version ID" — but every version costs storage money forever unless a Lifecycle Policy automatically archives (to Glacier) and eventually expires old versions on a schedule, giving you the safety of version history without paying to keep everything indefinitely.**