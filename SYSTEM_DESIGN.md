# System Design — Seat Holds, Concurrency, and the Waitlist

## Data model for a live seat

Each show snapshots its venue's static seat map into `show_seat` rows: one per seat per show,
carrying `status` (`AVAILABLE` / `HELD` / `RESERVED` / `OFFERED` / `BOOKED`),
`heldByBookingId`, `holdExpiresAt`, and a `@Version` column — the single source of truth for
whether a seat can be selected right now. The seat-map endpoint just reads it; every mutation
below goes through it under a row lock.

## Seat hold + TTL

Holding seats (`POST /bookings/hold`) does four things inside one transaction:

1. `SELECT ... FOR UPDATE` the requested `show_seat` rows, **ordered by id ascending**. The
   ordering matters: if two requests both touch seats A and B, locking in the same order
   means both requests block on the same first row instead of deadlocking against each other.
2. Verify every locked seat is still `AVAILABLE`. If not, abort with a 409 — cleanly, because
   nothing has been mutated yet.
3. Flip each seat to `HELD`, set `holdExpiresAt = now + BOOKING_HOLD_TTL_MINUTES` (default
   10, configurable), and stamp it with the new booking's id.
4. Create the `Booking` (status `HELD`) and its `BookingSeat` link rows.

A ShedLock-guarded `@Scheduled` job runs every 30 seconds, finds bookings with
`status = HELD AND holdExpiresAt < now`, releases their seats (see below), and marks the
booking `EXPIRED`. ShedLock (lock stored in Postgres) ensures only one instance runs this
even if the app is horizontally scaled. Abandoning checkout needs no explicit "cancel" —
this cron is the mechanism.

## Concurrency protection

The `SELECT ... FOR UPDATE` in step 1 above is the entire guarantee. If two customers request
overlapping seats at nearly the same instant, Postgres serializes them: the first transaction
gets the lock and proceeds; the second blocks until the first commits, then re-reads the
now-`HELD` status and fails step 2's availability check. Neither request can observe a stale
"still available" view of a seat the other just claimed — this is enforced by row locking,
not application-level flags, so it holds across multiple app instances too.

Optimistic locking (`@Version`) on `Booking` and `ShowSeat` is a second safety net for the
rarer case of two transactions touching a row without sharing the same explicit lock (e.g. a
confirm racing the expiry cron) — one save fails with an optimistic-lock exception instead of
silently overwriting the other's state.

## Freeing a seat, and the waitlist's group math

Freed seats for a `(show, category)` route through `SeatReleaseService.freeSeat(seat)`. If
nobody's waiting, the seat just becomes `AVAILABLE` again — done. If someone is, the seat
becomes `RESERVED`: pulled out of general circulation into a shared, anonymous pool for that
category. No individual waiter "owns" a pooled seat — the pool belongs to the category, not
to a person, until it's large enough to fulfill someone.

Every time the pool changes, `tryFulfillQueue` walks the `WAITING` entries in join order and
checks whether the pool now covers each one's `requestedQuantity` in one shot. If yes, it
pulls exactly that many seats out, bundles them into **one offer group** (one token, one
`SeatOffer` row per seat), marks the entry `OFFERED`, and evaluates the *remaining* pool
against the *next* entry. If a request needs more than the pool holds, it's skipped (stays
`WAITING`) rather than blocking the pass — a 1-seat request behind a stalled 5-seat one still
gets served the moment 1 seat is free. Leftover pool after one pass just stays `RESERVED`.
Fulfillment is deliberately all-or-nothing — nobody is ever offered fewer seats than asked.

This one mechanism is called from three places — `cancelBooking`, the hold-expiry cron, and
the offer-expiry cron — which is what makes "waitlist gets auto-assigned on cancellation" and
"an unclaimed offer cascades to whoever's next" fall out of the same code path instead of
needing separate cascade logic for each.

Two safety valves prevent the pool from becoming a black hole: a cron expires anyone
`WAITING` longer than `WAITLIST_ACCUMULATION_TIMEOUT_MINUTES` (default 60), guarding against
a request for more seats than will ever realistically free up together; and once a
category's queue empties entirely, any leftover `RESERVED` pool seats drain back to
`AVAILABLE`.

## Time-limited offer handling

Accepting an offer (`POST /waitlist/offers/{token}/accept`) locks every `SeatOffer` row
sharing that token plus their seats (both id-ordered, so this can't deadlock against the hold
path), validates all are `PENDING`, unexpired, and belong to the calling customer, then hands
the whole seat list to `BookingHoldFactory` — the same factory `holdSeats` uses — to create
one fresh `HELD` booking covering all of them with a full new checkout window. From here it's
an ordinary booking: the customer confirms, and if they stall, the same hold-expiry cron
expires it and frees every seat again — re-entering the same pool logic above.
