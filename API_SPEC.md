# TicketFlow API Specification

> **Base URL** — edit this one line, every path below is relative to it.
>
> ```
> BASE_URL = http://localhost:8080/api/v1
> ```
>
> Example: `POST {BASE_URL}/auth/login`

---

## 1. Conventions

### 1.1 Every response is wrapped in an envelope

All endpoints (success **and** error) return this shape:

```json
{
  "timeStamp": "2026-08-23T14:02:11.123",
  "data": { /* endpoint-specific payload, null on error */ },
  "error": null
}
```

On error, `data` is `null` and `error` is populated:

```json
{
  "timeStamp": "2026-08-23T14:02:11.123",
  "data": null,
  "error": {
    "status": "BAD_REQUEST",
    "message": "Validation Failed: Email is required",
    "subErrors": null
  }
}
```

`error.status` is one of the standard HTTP status enum names: `BAD_REQUEST` (400),
`UNAUTHORIZED` (401), `FORBIDDEN` (403), `NOT_FOUND` (404), `CONFLICT` (409), `GONE` (410),
`INTERNAL_SERVER_ERROR` (500). The HTTP status code on the response itself matches
`error.status`.

**Everywhere below, "Response" means the value of `data` — assume it's wrapped in the
envelope above.**

### 1.2 Auth header

Every authenticated endpoint requires:

```
Authorization: Bearer <accessToken>
```

`accessToken` comes from `POST /auth/login`. It expires after 10 minutes — call
`POST /auth/refresh` with the refresh token to get a new one (see §2).

### 1.3 Pagination

Any endpoint with `page`/`size` query params returns a Spring `Page` object:

```json
{
  "content": [ /* array of the item type */ ],
  "totalElements": 42,
  "totalPages": 5,
  "number": 0,
  "size": 10,
  "numberOfElements": 10,
  "first": true,
  "last": false,
  "empty": false,
  "sort": { "sorted": false, "unsorted": true, "empty": true },
  "pageable": { /* internal, ignore */ }
}
```

`page` is 0-indexed. Defaults: `page=0`, `size=10`.

### 1.4 Enums (exact string values used everywhere)

| Enum | Values |
|---|---|
| `role` (register) | `CUSTOMER`, `ORGANISER` (`ADMIN` is seeded, never self-registered) |
| `type` (event) | `MOVIE`, `CONCERT` |
| Show `status` | `SCHEDULED`, `CANCELLED`, `COMPLETED` |
| Seat `status` (seat map) | `AVAILABLE`, `HELD`, `RESERVED` (pulled into a waitlist pool — not directly bookable, treat like unavailable), `OFFERED`, `BOOKED` |
| Booking `status` | `HELD`, `CONFIRMED`, `CANCELLED`, `EXPIRED` |
| Waitlist entry `status` | `WAITING`, `OFFERED`, `FULFILLED`, `EXPIRED`, `CANCELLED` |

### 1.5 Dates/times — everything is UTC, no exceptions

The backend computes, stores, and returns **UTC only** — this is pinned at the JVM level
(`TimeZone.setDefault(UTC)`) specifically so it's identical regardless of which machine or
region the app runs on. It does not infer a timezone from the request. **All conversion to
a viewer's local time is a frontend responsibility.**

| Field(s) | Type | Format | Example | Contains a zone marker? |
|---|---|---|---|---|
| `timeStamp` (every envelope), `holdExpiresAt`, `joinedAt` | `LocalDateTime` | `"YYYY-MM-DDTHH:mm:ss.SSS"` | `"2026-08-24T06:32:06.412"` | **No** — no `Z`, no offset |
| `showDate` / `showTime` | `LocalDate` / `LocalTime` | `"YYYY-MM-DD"` / `"HH:mm:ss"` | `"2026-09-01"` / `"19:00:00"` | No zone concept at all |

Because there's no `Z` suffix, parsing a timestamp field with plain `new Date(str)` in JS
treats it as **local time** — silently wrong by your viewer's UTC offset. Tell the parser
it's UTC explicitly, e.g. append `Z` before parsing (`new Date(str + 'Z')`), or use
`dayjs.utc(str)` / an equivalent library call.

**The one field that flows the other direction** — the frontend *sending* a date/time to
the backend — is `showDate`/`showTime` in `POST /organiser/events/{eventId}/shows` (§4). The
backend's "is this show upcoming" logic (which drives both search/browse filtering and the
`400` rejection of holding/waitlisting a past show, §5–7) compares those fields against
`LocalDate.now()`/`LocalTime.now()` — UTC "now". **Convert the organiser's local input to
UTC before sending it**, or a show meant for "7pm IST" gets stored and compared as if it
meant "7pm UTC" (off by 5.5 hours — potentially flipping whether it even counts as
upcoming). No other request body in this API currently accepts a date/time field.

---

## 2. Auth — `/auth`

### `POST {BASE_URL}/auth/register`
No auth required.

**Request**
```json
{
  "name": "Jane Doe",
  "email": "jane@example.com",
  "password": "at-least-8-chars",
  "role": "CUSTOMER"
}
```

**Response** `201 Created`
```json
{
  "userId": 12,
  "email": "jane@example.com",
  "role": "CUSTOMER"
}
```

**Errors**: `400` — email already registered, role invalid/not self-registerable, or
validation failure.

---

### `POST {BASE_URL}/auth/login`
No auth required.

**Request**
```json
{
  "email": "jane@example.com",
  "password": "at-least-8-chars"
}
```

**Response** `200 OK`
```json
{
  "accessToken": "eyJhbGciOiJIUzI1NiJ9...",
  "refreshToken": "eyJhbGciOiJIUzI1NiJ9..."
}
```

**Errors**: `401` — bad credentials.

---

### `POST {BASE_URL}/auth/refresh`
No `Authorization` header needed. Requires header:
```
x-refresh-token: <refreshToken>
```
No request body.

**Response** `200 OK` — same shape as login (`accessToken` + `refreshToken`, both rotated).

**Errors**: `401` — refresh token invalid/expired/reused (session revoked).

---

### `POST {BASE_URL}/auth/logout`
Requires `Authorization: Bearer <accessToken>` **and** header:
```
x-refresh-token: <refreshToken>
```
No request body.

**Response** `204 No Content` — empty body.

---

## 3. Admin — `/admin/venues` (role: `ADMIN`)

### `POST {BASE_URL}/admin/venues`
**Request**
```json
{
  "name": "PVR Cinemas - Forum Mall",
  "address": "21 Hosur Road",
  "city": "Bangalore"
}
```

**Response** `201 Created`
```json
{
  "id": 1,
  "name": "PVR Cinemas - Forum Mall",
  "address": "21 Hosur Road",
  "city": "Bangalore",
  "active": true
}
```

---

### `GET {BASE_URL}/admin/venues`
No body.

**Response** `200 OK` — array of the venue object shown above (**not paginated**).

---

### `GET {BASE_URL}/admin/venues/{venueId}`
**Response** `200 OK` — single venue object. **Errors**: `404`.

---

### `POST {BASE_URL}/admin/venues/{venueId}/seats/bulk`
Adds rows of seats to a venue. Category names are created automatically the first time they're
used for this venue.

**Request**
```json
{
  "rows": [
    { "rowLabel": "A", "categoryName": "Premium", "seatCount": 10 },
    { "rowLabel": "B", "categoryName": "Standard", "seatCount": 20 }
  ]
}
```
`seatCount` seats are created per row, numbered `1..seatCount` (label = `rowLabel + seatNumber`, e.g. `A1`, `A2`, ...).

**Response** `201 Created` — array of created seats:
```json
[
  { "id": 101, "rowLabel": "A", "seatNumber": 1, "label": "A1", "categoryName": "Premium" },
  { "id": 102, "rowLabel": "A", "seatNumber": 2, "label": "A2", "categoryName": "Premium" }
]
```

**Errors**: `400` — a row/seat-number combination already exists for this venue.

---

### `GET {BASE_URL}/admin/venues/{venueId}/seats`
**Response** `200 OK` — array of seat objects (same shape as above, **not paginated**).

---

## 4. Organiser — `/organiser/events` (role: `ORGANISER`)

### `POST {BASE_URL}/organiser/events`
**Request**
```json
{
  "title": "Dune: Part Three",
  "type": "MOVIE",
  "description": "Optional free text"
}
```
`type` must be `MOVIE` or `CONCERT`. `description` is optional.

**Response** `201 Created`
```json
{
  "id": 5,
  "organiserId": 3,
  "title": "Dune: Part Three",
  "type": "MOVIE",
  "description": "Optional free text",
  "active": true,
  "shows": []
}
```

---

### `GET {BASE_URL}/organiser/events?page=0&size=10`
**Response** `200 OK` — paginated (§1.3), `content` items shaped like the event object above
(with `shows: []` — this endpoint doesn't include show details).

---

### `GET {BASE_URL}/organiser/events/{eventId}`
Shows **all** of this event's shows, past and future — unlike the public detail view (§5),
which only ever lists upcoming ones.

**Response** `200 OK` — event object **with its shows populated**:
```json
{
  "id": 5,
  "organiserId": 3,
  "title": "Dune: Part Three",
  "type": "MOVIE",
  "description": "Optional free text",
  "active": true,
  "shows": [
    {
      "id": 9,
      "eventId": 5,
      "eventTitle": "Dune: Part Three",
      "venueId": 1,
      "venueName": "PVR Cinemas - Forum Mall",
      "showDate": "2026-09-01",
      "showTime": "19:00:00",
      "status": "SCHEDULED",
      "categoryPrices": [
        { "categoryName": "Premium", "price": 500.00 },
        { "categoryName": "Standard", "price": 250.00 }
      ]
    }
  ]
}
```

**Errors**: `404`.

---

### `POST {BASE_URL}/organiser/events/{eventId}/shows`
Creates a show at a venue and snapshots that venue's entire seat map into per-show seats,
priced per category. Only the event's own organiser may call this.

To let the organiser pick `venueId` and see which category names need a price, call
`GET {BASE_URL}/venues` and `GET {BASE_URL}/venues/{venueId}/seats` first (§5) — venues are
created by an admin, not the organiser, so this is how the organiser discovers them (no auth
needed for either call).

**Request**
```json
{
  "venueId": 1,
  "showDate": "2026-09-01",
  "showTime": "13:30:00",
  "categoryPrices": [
    { "categoryName": "Premium", "price": 500.00 },
    { "categoryName": "Standard", "price": 250.00 }
  ]
}
```
Every seat category that exists on the venue **must** have a price entry here, or the request
fails with `400`.

**`showDate`/`showTime` must be in UTC** (see §1.5) — convert the organiser's local input
before sending. The example above is "7pm IST" sent as its UTC equivalent, `13:30:00`.
Sending local wall-clock time as if it were UTC will make the show's actual scheduled time
wrong by your organiser's UTC offset, and can incorrectly flip whether it counts as
"upcoming" in search/browse and the hold/waitlist guards.

**Response** `201 Created` — the show object shown above.

**Errors**: `400` — venue has no seats yet, or a category is missing a price. `403` — not
this event's organiser. `404` — event or venue not found.

---

### `GET {BASE_URL}/organiser/events/{eventId}/report`
Owner-only.

**Response** `200 OK`
```json
{
  "eventId": 5,
  "title": "Dune: Part Three",
  "confirmedBookings": 128,
  "cancelledBookings": 4,
  "totalRevenue": 32000.00
}
```

**Errors**: `403` — not this event's organiser.

---

## 5. Browse — `/events`, `/venues`, `/shows` (public, no auth required)

**Only upcoming shows are ever visible through this section.** "Upcoming" is computed
server-side against UTC "now" (§1.5) — an event whose every show has already happened won't
appear in search results, and its detail view's `shows` array won't include those past
shows either. Compare §4's organiser-only detail view, which sees the full history.

### `GET {BASE_URL}/venues`
No params. **Response** `200 OK` — array (**not paginated**):
```json
[
  { "id": 1, "name": "PVR Cinemas - Forum Mall", "address": "21 Hosur Road", "city": "Bangalore", "active": true }
]
```

---

### `GET {BASE_URL}/venues/{venueId}`
**Response** `200 OK` — a single venue object as above. **Errors**: `404`.

---

### `GET {BASE_URL}/venues/{venueId}/seats`
The venue's static seat layout — mainly useful to an organiser deciding what
`categoryName`s to price when creating a show (§4), or to an admin double-checking what
they've configured so far.

**Response** `200 OK` — array (**not paginated**):
```json
[
  { "id": 101, "rowLabel": "A", "seatNumber": 1, "label": "A1", "categoryName": "Premium" },
  { "id": 102, "rowLabel": "A", "seatNumber": 2, "label": "A2", "categoryName": "Premium" }
]
```

---

### `GET {BASE_URL}/events?type=MOVIE&city=Bangalore&q=aveng&page=0&size=10`
All query params optional. `q` is a **fuzzy/typo-tolerant** search on the event title
(Postgres trigram similarity) — `q=aveng` or even `q=avngers` will still match "Avengers".
Results are ordered by match strength when `q` is given. `type`/`city` stay exact-match
filters, combined with `q` via AND. Only events with at least one upcoming show are
returned (see the note at the top of this section).

**Response** `200 OK` — paginated (§1.3); `content` items are event objects with
`shows: []` (call the detail endpoint below for show/pricing info).

---

### `GET {BASE_URL}/events/{eventId}`
**Response** `200 OK` — same shape as §4's organiser detail endpoint, **except** `shows`
only lists upcoming ones (see the note at the top of this section).

---

### `GET {BASE_URL}/shows/{showId}/seatmap`
**Poll this on an interval to keep a seat-map UI live** (no WebSocket in this version).

**Response** `200 OK`
```json
{
  "showId": 9,
  "seats": [
    {
      "showSeatId": 501,
      "rowLabel": "A",
      "seatNumber": 1,
      "label": "A1",
      "categoryId": 3,
      "categoryName": "Premium",
      "price": 500.00,
      "status": "AVAILABLE"
    },
    {
      "showSeatId": 502,
      "rowLabel": "A",
      "seatNumber": 2,
      "label": "A2",
      "categoryId": 3,
      "categoryName": "Premium",
      "price": 500.00,
      "status": "HELD"
    }
  ]
}
```
`status` is one of `AVAILABLE` / `HELD` / `RESERVED` / `OFFERED` / `BOOKED` — only `AVAILABLE`
seats can be selected for a new hold. `RESERVED` means the seat has been pulled into that
category's shared waitlist pool (see §7) — render it the same as unavailable, just like
`HELD`/`OFFERED`/`BOOKED`. Use `showSeatId` (not `label`) when calling `/bookings/hold`, and
`categoryId` (not `categoryName`) when calling `/waitlist`.

**Errors**: `404` — show not found.

---

## 6. Bookings — `/bookings` (role: `CUSTOMER`)

### `POST {BASE_URL}/bookings/hold`
Holds one or more seats for `BOOKING_HOLD_TTL_MINUTES` (default 10 min). Fails immediately —
no partial holds — if any requested seat is no longer `AVAILABLE`.

**Request**
```json
{
  "showId": 9,
  "showSeatIds": [501, 502]
}
```

**Response** `201 Created`
```json
{
  "id": 77,
  "showId": 9,
  "eventTitle": "Dune: Part Three",
  "status": "HELD",
  "bookingReference": null,
  "customerName": null,
  "customerEmail": null,
  "customerPhone": null,
  "amount": 1000.00,
  "holdExpiresAt": "2026-08-23T14:12:00.000",
  "qrCodeBase64": null,
  "seats": [
    { "seatLabel": "A1", "categoryName": "Premium", "price": 500.00 },
    { "seatLabel": "A2", "categoryName": "Premium", "price": 500.00 }
  ]
}
```
`qrCodeBase64` is `null` until the booking is `CONFIRMED`. Once set, it's a raw base64 PNG —
render it as `<img src="data:image/png;base64,${qrCodeBase64}">` (this only needs to work in
your app/browser UI, which is fine with data URIs — the confirmation **email** embeds the same
QR a different way, as a CID attachment, since Gmail and most webmail clients strip inline
`data:` image URIs).
`bookingReference` / `customerName` / `customerEmail` / `customerPhone` are only populated
once the booking is `CONFIRMED`.

**Errors**: `404` — show or one of the seats doesn't exist. `400` — a seat doesn't belong to
this show, **or the show has already happened** (checked server-side regardless of what the
UI shows — see §5's note on upcoming-only browsing). `409` — one or more seats are no longer
available (someone else got there first, or mid-lock contention — **retry the request**).

---

### `POST {BASE_URL}/bookings/{bookingId}/confirm`
Must be called before `holdExpiresAt`. Sends the QR-code ticket email on success.

**Request**
```json
{
  "customerName": "Jane Doe",
  "customerEmail": "jane@example.com",
  "customerPhone": "+919876543210"
}
```

**Response** `200 OK` — same booking shape as hold's response, now with
`"status": "CONFIRMED"`, `bookingReference` set (e.g. `"TKT-3F9A1B2C"`), `holdExpiresAt: null`,
the three customer fields populated, and `qrCodeBase64` populated (see §6's hold response
note above for how to render it).

**Errors**: `404` — booking not found. `403` — booking belongs to another customer. `409` —
booking isn't in `HELD` status, or the hold already expired (select seats again).

---

### `POST {BASE_URL}/bookings/{bookingId}/cancel`
No body. Only a `CONFIRMED` booking can be cancelled. Frees the seat(s) — which either makes
them `AVAILABLE` again or triggers a waitlist offer, see §7.

**Response** `200 OK` — booking object with `"status": "CANCELLED"`.

**Errors**: `404`, `403`, `400` (not currently confirmed).

---

### `GET {BASE_URL}/bookings?page=0&size=10`
My booking history, most recent first. Only `CONFIRMED` and `CANCELLED` bookings are
returned — a still-`HELD` mid-checkout attempt or an `EXPIRED` abandoned hold isn't
"history" and won't show up here (use the response from `/bookings/hold` /
`/bookings/{id}/confirm` directly to track an in-progress checkout instead).

**Response** `200 OK` — paginated (§1.3); `content` items are booking objects (as above).

---

### `GET {BASE_URL}/bookings/{bookingId}`
Viewable by the booking's own customer **or** the organiser of the event it belongs to.

**Response** `200 OK` — single booking object.

**Errors**: `404`, `403`.

---

## 7. Waitlist — `/waitlist` (role: `CUSTOMER`)

Waitlisting is **quantity-based and all-or-nothing**: you ask for N seats in a category
together, and you only get offered something once N seats have accumulated for you — never a
partial offer of fewer than you asked for. See `SYSTEM_DESIGN.md` for the full mechanics
(shared per-category pool, skip-ahead fairness, accumulation timeout).

### `POST {BASE_URL}/waitlist`
Only allowed when there currently aren't enough directly-`AVAILABLE` seats in this category to
cover `quantity` — if there are, just book directly instead.

**Request**
```json
{
  "showId": 9,
  "categoryId": 3,
  "quantity": 3
}
```
`categoryId` — read it off any seat of the desired category from `GET /shows/{id}/seatmap`
(§5, each seat carries its `categoryId` + `categoryName`). `quantity` is 1–20.

**Response** `201 Created`
```json
{
  "id": 44,
  "showId": 9,
  "eventTitle": "Dune: Part Three",
  "categoryId": 3,
  "categoryName": "Premium",
  "requestedQuantity": 3,
  "status": "WAITING",
  "joinedAt": "2026-08-23T14:20:00.000"
}
```

**Errors**: `400` — enough seats are actually already available for that quantity, you're
already waiting for this show+category, **or the show has already happened**. `404` — show
or category not found.

---

### `GET {BASE_URL}/waitlist?page=0&size=10`
My waitlist entries, most recently joined first.

**Response** `200 OK` — paginated (§1.3); `content` items shaped like the entry object above.
`status` transitions `WAITING` → `OFFERED` (all `requestedQuantity` seats accumulated at
once) → `FULFILLED` (accepted in time) or `EXPIRED` (missed the offer window, *or* gave up
waiting entirely — see `WAITLIST_ACCUMULATION_TIMEOUT_MINUTES`, default 60 min).

---

### `POST {BASE_URL}/waitlist/offers/{token}/accept`
`token` comes from the link in the "N seats just opened up!" email — it identifies the whole
group of seats offered together, not just one. No body. Must be called by the same customer
the offer was made to, before the offer's expiry (`WAITLIST_OFFER_TTL_MINUTES`, default
15 min).

**Response** `200 OK` — a normal `HELD` booking covering **all** the offered seats at once
(same shape as `/bookings/hold`'s response, `seats` has `requestedQuantity` entries) —
proceed to `POST /bookings/{bookingId}/confirm` exactly as with a regular hold.

**Errors**: `404` — invalid token. `403` — offer belongs to a different customer. `410 Gone` —
offer already accepted/expired.

---

## 8. Typical end-to-end flows

**Booking a seat directly:**
`GET /shows/{id}/seatmap` → `POST /bookings/hold` → `POST /bookings/{id}/confirm` (poll the
seat map in between to reflect the `HELD` status to other users).

**Waitlist:**
`POST /waitlist` with the quantity you want (when not enough are directly available) → wait
for the "N seats opened up" email, sent only once that many have accumulated together → `POST
/waitlist/offers/{token}/accept` → `POST /bookings/{id}/confirm` (same TTL rules as a direct
hold from here on).
