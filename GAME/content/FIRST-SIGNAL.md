# Build 0.0.1 — First Signal

## Initial state

```text
CASH: 250
PRESSURE: 0
ACCESS: 0
ANGIE TRUST: 0
FLAGS: NONE
```

## Opening screen

```text
╔══════════════════════════════╗
║ EL B0Zzzz // LOCAL ARCHIVE   ║
║ SIGNAL STATUS: UNREAD        ║
╚══════════════════════════════╝

A folded note sits beside a dead charger.
A single message waits behind a cracked icon.

ANGIE:
“Good. You found the archive before it found you.
Read the note, then decide whether you want to answer.”

1. READ THE NOTE
2. OPEN ANGIE’S MESSAGE
3. LEAVE THE ARCHIVE
4. OPEN FICTION NOTICE
```

## Turn rules

- This is a fictional game scene.
- Present 2–4 choices per turn.
- Do not introduce permanent canon without explicit creator approval.
- Mark unapproved expansion ideas as `[DRAFT]`, `[RUMOR]`, or `[NON-CANON]`.
- Update only changed game values after a choice.
- End each turn waiting for a numbered player choice or a typed command.

## First choice outcomes

### 1 — Read the note

```text
FLAG ADDED: NOTE_READ
PRESSURE: +1

THE NOTE:
CALL ANGIE WHEN THE RAIN STOPS.

The ink has bled into the paper like it was written in a moving car.
Outside the archive, the Z-rain has started to drift.

1. OPEN ANGIE’S MESSAGE
2. POCKET THE NOTE
3. RETURN TO ARCHIVE
```

### 2 — Open Angie’s message

```text
FLAG ADDED: ANGIE_THREAD_OPEN
ANGIE TRUST: +1

ANGIE:
“Good. You answered the message, not the story somebody else wrote about it.
Now: read the note, or tell me why you are pretending you did.”

1. READ THE NOTE
2. ASK WHO SENT IT
3. CLOSE THREAD
```

### 3 — Leave the archive

```text
FLAG ADDED: FIRST_SIGNAL_DEFERRED
ACCESS: -1

The screen sleeps without shutting off.
Somewhere behind the glass, the unread icon keeps blinking.

[ RETURN TO TITLE ]
```

### 4 — Open fiction notice

Display the launcher abridgment from `CANON/FICTION-PROTOCOL-OC-00NULL.md`, then return to the opening screen.
