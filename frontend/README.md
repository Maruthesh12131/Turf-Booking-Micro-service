# TurfBook frontend (React + Vite)

Talks only to the API gateway (`VITE_API_BASE_URL`, default `http://localhost:8000`).

```
cp .env.example .env     # only VITE_API_BASE_URL; never put secrets in VITE_* variables
npm install
npm run dev              # http://localhost:5173 (allowed by the gateway's CORS_ALLOWED_ORIGINS)
npm run build && npm test
```

Backend first (see the root README): PostgreSQL, RabbitMQ (`docker compose up -d rabbitmq`), then the four services and the gateway.

## API map

| Feature | Endpoint | Role |
|---|---|---|
| Sign up | `POST /users/create` (email, name, password, role, phoneNumber, businessName?) | public |
| Log in | `POST /users/login` → raw JWT string; then `GET /users/{email}` to confirm it and get the role | public / own |
| Turf list + location search | `GET /turfs?location=&page=&size=` (server-side, debounced) | public |
| Turf details, courts | `GET /turfs/{id}` | public |
| Time slots | `GET /turfs/{id}/timeslots` (all upcoming; dates derived client-side) | CUSTOMER, OWNER |
| Book | `POST /bookings` `{slotIds}` + required `Idempotency-Key` | CUSTOMER |
| Booking status (polled) / history | `GET /bookings/{id}`, `GET /bookings?page=&size=` | CUSTOMER |
| Cancel (whole or per slot) | `PUT /bookings/{id}` `{cancelSlotIds?}` | CUSTOMER |
| Owner profile / verification status | `GET/PUT/POST /owners/me`, `/owners` | OWNER |
| Owner turfs: create / edit / delete | `POST /turfs`, `GET /turfs/mine`, `PUT /turfs/{id}`, `DELETE /turfs/{id}` | OWNER |
| Generate / block / free slots | `POST /turfs/courts/{n}/timeslots`, `PUT /turfs/{id}/timeslots/{slotId}/status` | OWNER |
| Owner bookings | `GET /bookings/owner?turfId=&date=&page=&size=` | OWNER |
| Verify / reject owner | `PUT /owners/{ownerId}/verification` | ADMIN |
| Delete any turf | `DELETE /turfs/{id}` | ADMIN |

## Behaviour notes

* Token: the backend returns the JWT in the response body, so it is kept in `sessionStorage` (per tab, cleared on tab close). Any script on the origin could read it; the app renders no raw HTML and never logs tokens. There is no refresh endpoint (tokens last 1 hour), so the app signs out at expiry or on a 401.
* Roles come from the server-confirmed `/users/{email}` response; route guards are only UI convenience, the services enforce access.
* Booking: one court + one date, max 10 slots (backend rules). A booking is shown as confirmed only when `GET /bookings/{id}` says `CONFIRMED`; `PENDING_RESERVATION` is polled every 2 s (max ~1 min). The idempotency key is reused only while an attempt's outcome is unknown. No payment exists: bookings show "Payment not collected".
* Customers see slots as free/unavailable only (backend hides held/booked/blocked); "Past" is derived from the device clock.

## Missing backend capabilities (nothing faked in the UI)

* Turf images, ratings, reviews, facilities: not in any DTO, so cards use a neutral placeholder and omit them.
* Admin: no endpoint to list owners/pending verifications (verify by owner ID only); no admin view of users, bookings or hidden turfs; admins cannot read time slots.
* Courts cannot be added/removed after a turf is created (`PUT /turfs/{id}` updates existing courts only).
* No endpoint to edit the user account (name/phone) or change password; no logout/token-revocation or refresh endpoint.
* No payment integration. No turf name in booking responses (only `turfId`).
* Signup has no server-side password or email rules (`SignupDTO` is unvalidated), so the form only requires a password and a well-formed email.
