## 2024-05-20 - [Batch inserting Tags to resolve N+1 Queries]
**Learning:** Repeated invocation of `@Insert` mappings inside iterating scopes within Room database DAOs introduces high overhead, resulting in an N+1 query problem.
**Action:** Always map iterable elements into a concrete structure (e.g. `List`) and perform bulk operations using Room’s variadic/collection-supported batch insertions directly.
