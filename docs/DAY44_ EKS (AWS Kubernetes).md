Day 44: EKS (AWS Kubernetes)

Concept first — what EKS actually is:

EKS (Elastic Kubernetes Service) = AWS's managed version of Kubernetes. Instead of you manually installing and maintaining Kubernetes yourself (complex, error-prone), AWS runs the hard parts (the "control plane" — the brain of Kubernetes) for you. You just create worker nodes (EC2 instances) and deploy your apps.

However — there's a real catch worth flagging honestly: while EKS itself needs fewer "install Kubernetes" steps, it needs more AWS-specific setup steps — IAM roles, VPC subnets, a command-line tool called kubectl, and another tool called eksctl. So it's fewer Kubernetes installation steps, but a similar or higher number of AWS configuration steps.

Let's start with the first requirement: the tools we need on your machine (WSL2).

Two tools we need to install:

kubectl — the command-line tool to talk to any Kubernetes cluster (not just EKS)
eksctl — a tool specifically made to simplify creating EKS clusters (otherwise you'd need dozens of manual AWS console clicks)

Let's install kubectl first.

Step 1 — download it:

curl -LO "https://dl.k8s.io/release/$(curl -L -s https://dl.k8s.io/release/stable.txt)/bin/linux/amd64/kubectl"

Plain English: this command first asks Kubernetes' website "what's the latest stable version?" then downloads that exact version's kubectl tool.

Step 2 — make it executable and move it to a place your system can find it:

chmod +x kubectl
sudo mv kubectl /usr/local/bin/

Plain English: chmod +x marks the file as "runnable" (like giving it permission to execute), and mv moves it into a folder Linux always checks when you type a command name — same as how git, curl, mvn all just work without you specifying their full path.

Run both, then verify:

kubectl version --client
---------------------------------------------------------------------------------------------------------------------

Now let's install eksctl — the tool that simplifies EKS cluster creation.

Step 1 — download and extract it:

curl --silent --location "https://github.com/eksctl-io/eksctl/releases/latest/download/eksctl_Linux_amd64.tar.gz" | tar xz -C /tmp

Plain English: downloads eksctl's compressed package and extracts it directly into /tmp (a temporary folder).

Step 2 — move it into place and verify:

sudo mv /tmp/eksctl /usr/local/bin/
eksctl version

Now let's check one more requirement — AWS CLI. You already have this from Days 8-13, but let's confirm it's configured with valid credentials, since eksctl will use it to talk to your AWS account.

Run:

aws sts get-caller-identity

Plain English: this asks AWS "who am I, according to my current credentials?" — confirms your CLI is properly authenticated before we try creating anything.

Creating the EKS cluster.

One decision first: eksctl needs to know the region. Your existing infrastructure (EC2, RDS) is in eu-north-1 (Stockholm) — let's keep everything in the same region to avoid cross-region complications and costs.

Run this command — it'll create a small, cost-conscious cluster (2 worker nodes, small instance type):
eksctl create cluster \
  --name bookmanagement-cluster \
  --region eu-north-1 \
  --zones eu-north-1a,eu-north-1b \
  --nodegroup-name standard-workers \
  --node-type t3.micro \
  --nodes 1 \
  --nodes-min 1 \
  --nodes-max 2 \
  --managed                       (the main problem when we create an eksctl we need to use nodes and type we t3.medium before so the ec2 lanunch failure t3.micro is sutitable always 1node )
Plain English breakdown:

--name = cluster's name
--nodegroup-name = name for the group of worker EC2 instances
--node-type t3.medium = instance size (a bit bigger than your usual t3.micro, since Kubernetes itself needs some overhead)
--nodes 2 = start with 2 worker servers
--managed = AWS manages the worker node lifecycle for you

Why we're using eksctl instead of manual click in AWS manual click:

Console method: you'd manually create an IAM role for EKS, manually create/select VPC subnets across multiple availability zones, manually configure security groups, then separately create a node group with its own IAM role — many chances for misconfiguration
eksctl method: handles all of that automatically with sensible defaults, in one command(company also prefer this)

EKS creates its own brand-new infrastructure — new EC2 worker nodes, specifically for running Kubernetes Pods.