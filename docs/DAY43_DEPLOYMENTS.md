Day 43 Notes — Deployments

Deployment = tells Kubernetes "always keep X replicas of this Pod running" — constantly checks and self-heals if a Pod dies

Key insight: replacement isn't instant — there's a small gap, which is why running multiple replicas (not just 1) avoids real downtime(2, until Kubernetes notices and creates a replacement)

Analogy:

Deployment = restaurant manager enforcing "always 3 chefs"
Pod = each chef
Service = the phone number customers call (unaffected by staff changes)

Real-world parallel:

Think of a restaurant manager who was told: "always have exactly 3 chefs in the kitchen." If one chef goes home sick, the manager doesn't wait for someone to notice — they immediately call in a replacement, keeping the count at 3, always.