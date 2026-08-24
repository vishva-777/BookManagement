# Day 55 — Terraform Day 1: What IaC Is, Install, Basic Syntax (Beginner-Friendly Notes)

## Goal of the Day
Understand what "Infrastructure as Code" actually means and why it matters, install Terraform, and run the full create → verify → destroy lifecycle on a real AWS resource — entirely through code, no console clicking.

---

## 1. The Problem Terraform Solves

Rebuilding infrastructure manually (like the VPC setup from Day 52) has real risks:
- Takes time, even when you've done it before
- Easy to make mistakes (typos, missed steps) on a rebuild
- Impossible to guarantee identical results across environments (dev/staging/prod) or teammates

**Analogy: building furniture.**
- Clicking through the AWS console = building furniture from memory, no instructions — fast once, risky to repeat.
- Terraform = a written instruction manual (a text file) that says exactly what to build. Anyone, anywhere, any number of times, gets the *exact same result*.

**Core idea — Infrastructure as Code (IaC):** write infrastructure requirements in a text file (code), and a tool reads that file and builds it automatically. Same code → same result, every time, regardless of who runs it.

---

## 2. Why Not Just Use a Bash Script?

| | Bash script | Terraform |
|---|---|---|
| Behavior on re-run | Runs commands blindly again — creates duplicates or errors | Checks what already exists, only changes what's different |
| Memory of past runs | None | Has a **state file** tracking everything it manages |

**Idempotency** — the property that running the same code multiple times always leads to the same end result, never duplicates. This is the core reason companies trust Terraform over ad-hoc scripts.

---

## 3. Basic HCL Syntax

Terraform files use **HCL** (HashiCorp Configuration Language) — designed to be human-readable.

**Two fundamental block types:**

**`provider` block** — declares which cloud you're working with:
```hcl
provider "aws" {
  region = "eu-north-1"
}
```
Only one word in quotes (`"aws"`) — no nickname needed, since you're just declaring *which platform* to work with, not creating multiple distinct things.

**`resource` block** — declares something to actually create:
```hcl
resource "aws_instance" "my_server" {
  ami           = "ami-0c55b159cbfafe1f0"
  instance_type = "t3.micro"
}
```
General pattern:
```hcl
<BLOCK_TYPE> "<TYPE>" "<NAME>" {
  key = value
}
```
- `"aws_instance"` (or `"aws_s3_bucket"`, `"aws_vpc"`, etc.) = the **AWS resource type** — tells Terraform which AWS "part" to create. Fixed keyword Terraform recognizes.
- `"my_server"` = a **nickname you choose** — purely for Terraform's internal reference. AWS never sees this name.

**Two blocks with different nicknames = two separate resources created**, even if their settings are identical:
```hcl
resource "aws_instance" "web_server" { ... }
resource "aws_instance" "db_server" { ... }
```
This creates **2 EC2 instances** — each block, wrapped in its own `{ }`, is an independent instruction.

---

## 4. Installing Terraform (WSL2 / Ubuntu)

Used HashiCorp's official apt repository (more reliable than snap — avoids sandboxing/version-lag issues):

```bash
sudo apt-get update && sudo apt-get install -y gnupg software-properties-common curl

wget -O- https://apt.releases.hashicorp.com/gpg | sudo gpg --dearmor -o /usr/share/keyrings/hashicorp-archive-keyring.gpg

echo "deb [signed-by=/usr/share/keyrings/hashicorp-archive-keyring.gpg] https://apt.releases.hashicorp.com $(lsb_release -cs) main" | sudo tee /etc/apt/sources.list.d/hashicorp.list

sudo apt update && sudo apt-get install -y terraform
```

Verify:
```bash
terraform -version
```
Result: Terraform v1.15.9 on linux_amd64.

**Authentication check** — Terraform reuses the same AWS CLI credentials already configured:
```bash
aws sts get-caller-identity
```
Confirmed authenticated as `vishva-admin`, account `902685117068`. No separate Terraform-specific AWS setup needed.

---

## 5. The Core Terraform Workflow

### Step 1: Project setup
```bash
mkdir ~/terraform-day55
cd ~/terraform-day55
nano main.tf
```

**`main.tf`** — the standard starting filename (Terraform reads any `.tf` file in the folder):
```hcl
provider "aws" {
  region = "eu-north-1"
}

resource "aws_s3_bucket" "my_first_bucket" {
  bucket = "vishva-terraform-day55-bucket"
}
```

### Step 2: `terraform init`
Downloads the **provider plugin** — a translator that converts `resource "aws_s3_bucket"` code into real AWS API calls. Terraform's core program knows nothing about AWS specifically until this plugin is installed.
```bash
terraform init
```
Result: installed `hashicorp/aws v6.61.0`, created `.terraform.lock.hcl` (locks the exact provider version for consistency — include this file in version control).

### Step 3: `terraform plan`
A **safe preview/dry-run** — shows exactly what Terraform is about to do, *before* touching real infrastructure or spending money. Like proofreading an email before hitting Send.
```bash
terraform plan
```
Result: `Plan: 1 to add, 0 to change, 0 to destroy.` — confirmed exactly one S3 bucket would be created, nothing unexpected.

### Step 4: `terraform apply`
Actually creates the resource. Shows the plan again, then requires typing the full word `yes` (not just Enter) as a deliberate safety speed bump against accidental changes.
```bash
terraform apply
```
Result: `Apply complete! Resources: 1 added, 0 changed, 0 destroyed.`

Verified directly via AWS CLI:
```bash
aws s3 ls | grep vishva-terraform-day55-bucket
```
Bucket confirmed present in real AWS account.

### Step 5: The State File
After apply, a new file appears: **`terraform.tfstate`** — Terraform's "memory" of everything it manages. This is how Terraform knows the bucket already exists.

**Proof of idempotency:** ran `terraform plan` again with zero code changes:
```bash
terraform plan
```
Result: `No changes. Your infrastructure matches the configuration.` — confirms Terraform compares real infrastructure against code and only acts on genuine differences.

### Step 6: `terraform destroy`
Removes the resource **and** updates the state file to match — keeping Terraform's memory in sync with reality.
```bash
terraform destroy
```
Typed `yes` to confirm. Bucket removed cleanly.

**Why always destroy through Terraform, never manually via console:** if you delete a resource manually, the state file doesn't know — it still thinks the resource exists. Next `terraform plan`/`apply` becomes confused or produces wrong results, because Terraform's internal memory is now out of sync with real-world AWS. (Analogy: a notebook that says "I have ₹500" when you've already spent it without updating the notebook — every decision made from that notebook afterward is wrong.)

---

## 6. Key Files in a Terraform Project

| File | Purpose |
|---|---|
| `main.tf` | Your infrastructure code |
| `.terraform/` | Downloaded provider plugins (created by `init`) |
| `.terraform.lock.hcl` | Locks exact provider versions — commit this to version control |
| `terraform.tfstate` | Terraform's memory of what it manages — critical, must stay in sync with real infrastructure |

---

## Key Takeaway (One-Line Summary)

**Terraform lets you describe infrastructure as code instead of clicking through a console — `init` sets up the tools, `plan` previews safely before anything real happens, `apply` builds it and remembers what it built via a state file, and `destroy` tears it down cleanly while keeping that memory in sync — giving you the same reliable, repeatable result every single time, unlike manual clicks or blind scripts.**