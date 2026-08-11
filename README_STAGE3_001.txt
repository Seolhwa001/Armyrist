Armyrist Stage 3 Patch 001 — Full Backup / Restore Foundation

IMPLEMENTED
- .armyrist common container v1
- formatIdentifier = ARMYRIST_DATA
- formatVersion = 1
- dataType = BACKUP
- UTF-8 JSON payload -> Base64
- Unencrypted SHA-256 integrity verification
- Optional AES-256-GCM encryption
- PBKDF2-HMAC-SHA256 / 600,000 iterations
- random 16-byte salt
- random 12-byte GCM IV
- password never persisted
- Full Backup includes:
  * Counting snapshot
  * Checklist / TimePlan / ReportTemplate / UserProfile core snapshot
  * TimePlan duration-label SharedPreferences
- Backup save through Android SAF CreateDocument
- Restore file selection through Android SAF OpenDocument
- Validate before mutation
- Restore Preview
- Replace-only Full Restore
- Crash-recovery journal for multi-SharedPreferences restore
- Interrupted restore recovers to complete old state on next launch
- Runtime repository reload after successful restore
- Notification reconcile after successful restore
- Home -> 데이터 관리 entry
- 32 MB input size guard
- Offline-only implementation

NOT YET CONNECTED IN THIS PATCH
- Individual Counting export/import
- Individual Checklist export/import
- Individual TimePlan export/import
- Individual ReportTemplate export/import
- Android "Open with Armyrist" .armyrist intent routing

These are the remaining Stage 3 integration items for Patch 002.

TEST FIRST
1. Existing data visible before backup.
2. Home -> 데이터 관리.
3. Create unencrypted backup and save .armyrist.
4. Change/create some data.
5. Restore saved backup.
6. Confirm old backed-up counts return.
7. Repeat with encryption ON.
8. Wrong password must not change current data.
9. Corrupt/truncated file must not change current data.
