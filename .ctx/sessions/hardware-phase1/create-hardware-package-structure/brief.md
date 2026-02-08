# Session Brief: create-hardware-package-structure

Type: feature
Phase: hardware-phase1
Complexity: low
Created: 2026-01-18

## Objective

Create the hardware/ package directory structure for scales and printer integration.

## Background

The Zagot+ app needs to integrate with scales (via TCP/WiFi) and thermal printer (via Bluetooth). This session creates the foundation package structure per HARDWARE_INTEGRATION_PLAN.md section 2.1.

## Requirements

- [ ] Create hardware/ package under com.zagot.zagotplus
- [ ] Create hardware/scales/ subpackage
- [ ] Create hardware/scales/protocol/ subpackage
- [ ] Create hardware/printer/ subpackage  
- [ ] Create hardware/printer/escpos/ subpackage

## Context Files

Load these files before starting:
- `HARDWARE_INTEGRATION_PLAN.md` (section 2.1 Package Structure)

## Implementation Notes

Just create empty directories. No code files yet - those come in subsequent sessions.

Expected structure:
```
android/app/src/main/kotlin/com/zagot/zagotplus/
├── hardware/
│   ├── scales/
│   │   └── protocol/
│   └── printer/
│       └── escpos/
```

## TDD

Mode: optional (no code to test)

## Success Criteria

- [ ] All directories exist
- [ ] Structure matches HARDWARE_INTEGRATION_PLAN.md section 2.1

## Out of Scope

- Creating any .kt files (done in later sessions)
- HardwareModule.kt (done in integrate-with-viewmodels session)

## Dependencies

- Requires: none
- Blocks: implement-scales-interfaces, implement-printer-interfaces
