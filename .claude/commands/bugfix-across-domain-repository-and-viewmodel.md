---
name: bugfix-across-domain-repository-and-viewmodel
description: Workflow command scaffold for bugfix-across-domain-repository-and-viewmodel in DropSauce.
allowed_tools: ["Bash", "Read", "Write", "Grep", "Glob"]
---

# /bugfix-across-domain-repository-and-viewmodel

Use this workflow when working on **bugfix-across-domain-repository-and-viewmodel** in `DropSauce`.

## Goal

Fixes bugs by updating repository, DAO, and ViewModel logic, often for data consistency or UI correctness.

## Common Files

- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/data/*.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/domain/*.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/tracker/ui/feed/FeedViewModel.kt`
- `app/src/main/kotlin/org/koitharu/kotatsu/core/prefs/AppSettings.kt`

## Suggested Sequence

1. Understand the current state and failure mode before editing.
2. Make the smallest coherent change that satisfies the workflow goal.
3. Run the most relevant verification for touched files.
4. Summarize what changed and what still needs review.

## Typical Commit Signals

- Update repository logic to fix bug
- Modify DAO/data access classes as needed
- Update ViewModel to ensure correct UI state
- Adjust settings or preferences if relevant

## Notes

- Treat this as a scaffold, not a hard-coded script.
- Update the command if the workflow evolves materially.