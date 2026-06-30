```markdown
# DropSauce Development Patterns

> Auto-generated skill from repository analysis

## Overview
This skill teaches you how to contribute effectively to the DropSauce Kotlin codebase. You'll learn the project's coding conventions, commit patterns, and the main workflows for feature development and bug fixing across domain, data, and UI layers. The guide also covers how to structure your code and write tests in alignment with the repository's standards.

## Coding Conventions

### File Naming
- **PascalCase** is used for file names.
  - Example: `FeedViewModel.kt`, `AppSettings.kt`

### Imports
- **Relative imports** are preferred.
  - Example:
    ```kotlin
    import org.koitharu.kotatsu.tracker.domain.model.TrackedItem
    ```

### Exports
- **Named exports** are used (Kotlin default).
  - Example:
    ```kotlin
    class FeedViewModel { ... }
    ```

### Commit Messages
- **Conventional commits** with prefixes:
  - `feat`: For new features
  - `fix`: For bug fixes
- **Average length:** ~49 characters
  - Example: `feat: add support for new feed filter`

## Workflows

### Feature Development with ViewModel and Domain Layer
**Trigger:** When adding a new tracked feature or feed functionality  
**Command:** `/new-feature-feed`

1. **Update or create domain models and use cases**
    - Add or modify files in `domain/model` and `domain/` directories.
    - Example:
      ```kotlin
      data class NewFeatureModel(val id: String, val value: Int)
      ```
2. **Modify or add DAO/data access classes**
    - Update or create classes in `core/db/dao/` or `tracker/data/`.
3. **Update repository logic**
    - Adjust repository classes to support new feature logic.
4. **Implement or update ViewModel for UI**
    - Edit `FeedViewModel.kt` to expose new data or handle new actions.
    - Example:
      ```kotlin
      class FeedViewModel : ViewModel() {
          fun loadNewFeature() { ... }
      }
      ```
5. **Add or update UI adapters and models**
    - Update files in `ui/feed/adapter/` and `ui/feed/model/`.
6. **Update supporting utility or mapping classes**
    - Edit or create files in `core/util/ext/`.
7. **Modify resource files if needed**
    - Adjust XML files in `res/values/` for strings, colors, etc.

---

### Bugfix Across Domain, Repository, and ViewModel
**Trigger:** When fixing a bug affecting tracked data or feed presentation  
**Command:** `/bugfix-feed`

1. **Update repository logic to fix bug**
    - Edit repository files in `tracker/data/`.
2. **Modify DAO/data access classes as needed**
    - Update files in `core/db/dao/` or related data access layers.
3. **Update ViewModel to ensure correct UI state**
    - Adjust `FeedViewModel.kt` to fix state management or data flow.
    - Example:
      ```kotlin
      class FeedViewModel : ViewModel() {
          fun refreshFeed() { /* bugfix logic */ }
      }
      ```
4. **Adjust settings or preferences if relevant**
    - Update `AppSettings.kt` in `core/prefs/` if the bug is settings-related.

## Testing Patterns

- **Test file pattern:** `*.test.*`
- **Testing framework:** Unknown (not detected)
- **Typical test location:** Same directory as the code or a parallel test directory.
- **Example:**
  ```kotlin
  // FeedViewModel.test.kt
  class FeedViewModelTest {
      @Test
      fun testNewFeature() { ... }
  }
  ```

## Commands

| Command           | Purpose                                                      |
|-------------------|--------------------------------------------------------------|
| /new-feature-feed | Start a new feature spanning domain, data, and UI layers     |
| /bugfix-feed      | Begin a bugfix across repository, DAO, and ViewModel logic   |
```
