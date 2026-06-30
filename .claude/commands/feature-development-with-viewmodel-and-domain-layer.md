---
name: feature-development-with-viewmodel-and-domain-layer
description: Workflow command scaffold for feature-development-with-viewmodel-and-domain-layer in DropSauce.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /feature-development-with-viewmodel-and-domain-layer

Use this workflow when working on **feature-development-with-viewmodel-and-domain-layer** in `DropSauce`.

## Goal

Implements a new feature that requires changes across domain logic, data access, and UI ViewModel, often including new models and adapters.

## Common Files

- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/domain/model/*.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/domain/*.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/data/*.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/ui/feed/FeedViewModel.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/ui/feed/adapter/*.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/ui/feed/model/*.kt`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Update or create domain models and use cases
- Modify or add DAO/data access classes
- Update repository logic
- Implement or update ViewModel for UI
- Add or update UI adapters and models

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.