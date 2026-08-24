# Day 56 — Terraform Day 2: Recreating a Piece of Real Infra as Code (Beginner-Friendly Notes)

## Goal of the Day
Take a real, manually-created piece of infrastructure (`vishva-server-1`, an EC2 instance) and recreate an equivalent version of it purely through Terraform code — safely, without touching the real running server — to practice matching code to real-world specs.

---

## 1. The Real-World Skill: Importing/Matching Existing Infrastructure

Most companies don't start with a blank slate — they already have infrastructure built manually (console clicks) that needs to eventually be managed by Terraform. This is one of the most valuable real Terraform skills.

**The risk to understand first:** if you "import" a real resource into Terraform's state but your code doesn't *exactly* match its real settings, running `terraform apply` afterward can try to **modify or even destroy** the real resource to force it to match the (wrong) code.

**Example:** if the real EC2 is `t3.micro` but your code says `t3.small`, `terraform apply` would try to **resize your real, running server** to match the code.

**Safer approach used today:** instead of importing the real `vishva-server-1` directly, we wrote Terraform code that recreates an **equivalent, separate practice resource** with the same specs — same skill, zero risk to production.

---

## 2. Gathering Real Specs Before Writing Code

Pulled exact configuration from the AWS Console (EC2 → `vishva-server-1` → Details/Security tabs):

| Setting | Value |
|---|---|
| AMI | `ami-067bcf851477ebb78` (Ubuntu 24.04) |
| Instance type | `t3.micro` |
| Key pair | `vishva-key` |
| VPC | `vpc-05fd264e49446f45b` |
| Subnet | `subnet-0a84b8519f5764501` |
| Security group | `sg-00cb7efdbb2ce9d84` (launch-wizard-1) |

**Decision point:** rather than referencing the real, existing security group (which would create a dependency on manually-managed infrastructure), chose to have Terraform create a **new, minimal security group** it fully owns — closer to real-world best practice, where Terraform should generally manage everything it touches rather than partially depending on console-created resources.

---

## 3. The Terraform Code

```hcl
provider "aws" {
  region = "eu-north-1"
}

resource "aws_security_group" "practice_sg" {
  name        = "terraform-practice-sg"
  description = "Practice SG created by Terraform - Day 56"
  vpc_id      = "vpc-05fd264e49446f45b"

  ingress {
    from_port   = 22
    to_port     = 22
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }

  egress {
    from_port   = 0
    to_port     = 0
    protocol    = "-1"
    cidr_blocks = ["0.0.0.0/0"]
  }
}

resource "aws_instance" "practice_server" {
  ami                    = "ami-067bcf851477ebb78"
  instance_type          = "t3.micro"
  key_name               = "vishva-key"
  subnet_id              = "subnet-0a84b8519f5764501"
  vpc_security_group_ids = [aws_security_group.practice_sg.id]

  tags = {
    Name = "terraform-practice-server-day56"
  }
}
```

---

## 4. New Concept: Resource References

Look closely at this line:
```hcl
vpc_security_group_ids = [aws_security_group.practice_sg.id]
```

Instead of a hardcoded security group ID, this references **another resource's future output**. Since the security group doesn't exist yet (Terraform hasn't created it), there's no real ID to type — AWS only generates that ID *after* creation.

**What this reference actually does:**
1. Tells Terraform: "whatever ID gets generated for `practice_sg`, plug it in here automatically"
2. **Automatically determines creation order** — Terraform builds a dependency graph from these references. It knows the security group must be created *first* (to get a real ID) before the EC2 instance that needs it.

**Why not hardcode a guessed ID instead?** A guessed ID (like `sg-12345678`) wouldn't correspond to any real security group — AWS would reject the EC2 creation with a "security group not found" error, since that ID simply doesn't exist.

**Proof this worked in practice** — the apply output showed strict order:
```
aws_security_group.practice_sg: Creating...
aws_security_group.practice_sg: Creation complete after 9s [id=sg-0802f0fd4293017d5]
aws_instance.practice_server: Creating...
aws_instance.practice_server: Creation complete after 18s [id=i-072be6252499f00c0]
```
The EC2 instance only started creating *after* the security group finished — entirely automatic, never manually specified.

---

## 5. Debugging Real Syntax Errors (Caught by `terraform plan`)

Two typos were caught before anything touched AWS:

| Typo | Correct | Error shown |
|---|---|---|
| `vpc=` | `vpc_id =` | `An argument named "vpc" is not expected here.` |
| `form_port` | `from_port` | `Did you mean "from_port"?` |

**Lesson:** `terraform plan` catches structural/naming mistakes immediately, before any real infrastructure is touched — exactly the safety net it's designed to be.

**Another mistake caught:** initially placed an `ingress` block inside `aws_instance` instead of `aws_security_group`. Important concept: `ingress`/`egress` rules belong to the **security group** (the "gate"), never to the EC2 instance itself — the instance just *points to* a security group via `vpc_security_group_ids`, it doesn't define its own firewall rules directly.

---

## 6. Updating Infrastructure Through Code (`~ update in-place`)

Simulated a real change request: add port 80 (HTTP) to the security group, in addition to the existing port 22 (SSH).

Added a second `ingress` block:
```hcl
  ingress {
    from_port   = 80
    to_port     = 80
    protocol    = "tcp"
    cidr_blocks = ["0.0.0.0/0"]
  }
```

Running `terraform plan` again showed:
```
~ update in-place
Plan: 0 to add, 1 to change, 0 to destroy.
```

**Key lesson:** because the security group's `id` stayed the same (same resource, just modified), Terraform recognized this as an **update**, not a brand-new resource. Applying it took 3 seconds and didn't even touch the EC2 instance — no restart needed.

**This is the real value of Terraform for ongoing infrastructure management:** it calculates the *minimal* set of changes needed to match updated code, rather than tearing everything down and rebuilding from scratch.

---

## 7. Teardown Order (`terraform destroy`)

```
aws_instance.practice_server: Destroying...
aws_instance.practice_server: Destruction complete after 22s
aws_security_group.practice_sg: Destroying...
aws_security_group.practice_sg: Destruction complete after 2s
Destroy complete! Resources: 2 destroyed.
```

**Notice the reverse order:** the EC2 instance was destroyed *before* the security group. This is Terraform reversing its dependency graph — it can't delete a security group that's still attached to a running instance, so destruction happens in the opposite order from creation.

---

## 8. Full Command Sequence Used Today

```bash
mkdir ~/terraform-day56 && cd ~/terraform-day56
nano main.tf              # write the code
terraform init             # download AWS provider plugin
terraform plan              # preview — caught 2 syntax errors first try
# fix errors, plan again — clean
terraform apply             # type 'yes' — creates SG then EC2, in that order
# verify via AWS CLI
nano main.tf              # add port 80 ingress rule
terraform plan              # shows "1 to change" (update in-place)
terraform apply             # modifies SG only, EC2 untouched
terraform destroy           # type 'yes' — destroys EC2 first, then SG
```

---

## Key Takeaway (One-Line Summary)

**Terraform lets you safely recreate real infrastructure specs as code without touching production, automatically figures out resource creation/destruction order from references between blocks (never manually specified), and — critically — updates only what actually changed rather than rebuilding everything, which is the real reason companies trust it for ongoing infrastructure management, not just initial setup.**