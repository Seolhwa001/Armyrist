# TimePlan v2 implementation boundary
Architecture Handover No.004 is the source of truth.

- UI: emit user intent and render state.
- Domain/calculation/candidate/conflict: Android-UI independent.
- Candidate: transient; no persistence mutation before Confirm.
- Local v1 migration and Portable v1 migration: separate input paths.
- Add concrete files/classes only when their responsibility is actually implemented.
