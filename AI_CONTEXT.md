# Hubitat Automation Repository — Instructions

## Purpose
This repository contains personal Hubitat automations, including apps and drivers that solve specific problems in a single home environment.

This is **not** a framework, SDK, or reusable automation platform.

Solutions should remain:

- simple
- readable
- Hubitat-native
- narrowly scoped

Breaking changes are acceptable.

---

## Architecture Model

Hubitat-native, event-driven automation architecture:

```
Hubitat platform
  → apps
  → drivers
  → capabilities
  → attributes
  → events
```

Execution model:

- single-threaded
- event-driven
- subscription-based
- scheduled when necessary

Apps and drivers are independent units.

There is:

- no shared runtime module system
- no service layer
- no domain model layer
- no dependency injection
- no external persistence

---

## Repository Structure

```
/apps
/drivers
```

Rules:

- One app or driver per file
- Files must be deployable independently into Hubitat
- No shared Groovy modules
- No cross-file imports

---

## Lifecycle Conventions

Apps and drivers should follow this lifecycle pattern:

```
installed()
updated()
initialize()
```

`updated()` should typically:

```
unschedule()
unsubscribe()
initialize()
```

Subscriptions must be recreated in `initialize()`.

---

## Coding Conventions

### General
- Prefer readability over cleverness
- Prefer simple Groovy
- Avoid abstractions unless clearly necessary
- Preserve existing patterns even if imperfect
- Make minimal changes when modifying code

Drivers should be capability-driven:

- capabilities
- attributes
- commands
- sendEvent()

Avoid internal state when attributes are appropriate.

---

## Child Devices

Child devices are allowed when modeling separate capabilities.

Use standard Hubitat patterns:

- `addChildDevice`
- `getChildDevice`

Ensure child devices exist during initialization.

---

## Logging Conventions

Avoid excessive logging.

### Debug logging
Use:
`logDebug()`

This comes from:
`mikee385.debug-library`

Do not redefine it.

---

### Info logging
Use:
`log.info`

If messages are frequent, use:
`logInfo()`

with preference-controlled logging.

---

### Warning logging
Prefer:
`log.warn`

---

## Error Handling Philosophy

Prefer **unhandled exceptions** over try/catch so errors appear clearly in Hubitat logs.

Use try/catch only when necessary for parsing or resilience.

Fail fast rather than silently masking errors.

---

## State Management

Preferred storage locations:

1. Preferences → configuration  
2. Device attributes → device state  
3. `state` → limited app state  
4. `atomicState` → avoid  

Avoid storing duplicate state.  
Avoid large state objects.

---

## Subscriptions and Scheduling

Prefer:

- event subscriptions
- capability events

Avoid polling.

Schedules should be used only when necessary.

Always reset subscriptions and schedules in `updated()`.

---

## Performance Constraints

Hubitat hubs are resource-constrained.

Strongly avoid frequent database writes:

- state writes
- attribute writes
- logging
- atomicState writes

Good:

- once every few minutes

Bad:

- once per second
- inside tight loops

Avoid:

- large attribute sets
- unnecessary `isStateChange: true`
- repeated device lookups
- large in-memory collections

---

## Versioning Philosophy

Mostly Semantic Versioning, but pragmatic.

Patch:

- No initialize required
- Internal fixes

Minor:

- Initialize required
- New subscriptions or state initialization

Major:

- New configuration required
- Device reselection
- Breaking preference changes

Versioning is subjective based on change impact.

---

# Modification Rules (Guardrails)

## SHOULD
- Make minimal surgical changes
- Preserve existing structure
- Preserve naming conventions
- Follow Hubitat lifecycle patterns
- Prefer event-driven solutions
- Optimize for readability
- Match existing logging style
- Respect performance constraints

---

## SHOULD NOT
- Introduce frameworks
- Introduce shared libraries
- Add abstraction layers
- Create service classes
- Introduce configuration systems
- Add external persistence
- Refactor unrelated code
- Convert architecture styles
- Add automated test frameworks
- Introduce cross-platform compatibility layers

---

## Refactoring Rule

If improvements are identified:

1. Perform the requested change first
2. Clearly separate improvement suggestions
3. Do not mix refactors with required changes