# Deployment guide

Plant-Ride ships as four container images plus PostgreSQL. **On-premise is the default target**. The same images run
unchanged on a cloud container platform; only where PostgreSQL runs and how TLS and secrets are provided change.

```
                 plant network / internet (HTTPS)
                              │
                  ┌───────────┴────────────┐   TLS termination (plant reverse proxy / cloud LB)
                  │                        │
          admin-web (nginx)        user-web, driver-web (nginx, optional browser builds)
                  │  /api proxied           │
                  └───────────┬────────────┘
                              ▼
   GPS devices ─► Traccar ─► backend (Spring Boot, :8080) ──► PostgreSQL 16
                              │
                              ├─► FCM (push, default)  or  in-house push gateway (webhook)
                              └─► SMS gateway (webhook, optional)

   Mobile apps (native builds) ─HTTPS─► backend /api
```

| Image | Source | Notes |
|---|---|---|
| `plantride/backend` | `backend/Dockerfile` | Stateless. Database migrations (Flyway) run on start-up. |
| `plantride/admin-web` | `admin-web/Dockerfile` | Static files + `/api` reverse proxy (`BACKEND_URL`) |
| `plantride/user-web`, `plantride/driver-web` | `user-app/Dockerfile`, `driver-app/Dockerfile` | Browser builds of the apps (showcases, desktop use). Phones use native builds. |

## 1. On-premise (default)

**Sizing.** For a plant with about 500 vehicles and tens of thousands of employees:
- one Linux VM with 4 vCPU and 8 GB RAM for the containers
- PostgreSQL on the same VM or the plant's database server, with 100 GB SSD

At 500 vehicles sending a fix every 10 s, GPS history grows by about 4 million rows a day. Plan partitioning and retention (section 5).

**Steps**

```bash
git clone <repo> && cd Plant-Ride
cp .env.example .env            # fill in every secret; see section 4
mkdir -p secrets                # put fcm-service-account.json here if using Firebase
docker compose up -d --build
docker compose ps               # backend should become "healthy"
```

Then:
1. Put the plant's reverse proxy (or the corporate load balancer) in front of `admin-web` (port 8081 by default) and the
   backend (port 8080, for the mobile apps), with the plant's TLS certificates. The backend honours `X-Forwarded-*`
   headers, so set them on the proxy.
2. Firewall:
   - **inbound**: the backend's `/api/tracking/**` should be reachable only from the Traccar host
   - **outbound**: if using Firebase push, allow HTTPS to `oauth2.googleapis.com` and `fcm.googleapis.com`. If the
     plant has no outbound internet, use the webhook push provider (section 3).
3. Sign in to the admin web as `admin` with `BOOTSTRAP_ADMIN_PASSWORD`, create a real admin user, then disable or
   re-password the bootstrap admin.

**Backups.** Back up PostgreSQL nightly (`pg_dump` or the plant's standard tooling), keep about 30 days, and test a restore
before go-live. The backend holds no state of its own.

**Upgrades.** Pull or build the new images and run `docker compose up -d`. Flyway applies database migrations on start. Back up the
database first; migrations are forward-only.

## 2. Cloud

Use the same images on any container platform (Azure Container Apps / AKS, AWS ECS / EKS, Google Cloud Run / GKE,
OpenShift):

| Concern | Recommendation |
|---|---|
| Database | Managed PostgreSQL 16; set `DB_URL`, `DB_USER`, `DB_PASSWORD` |
| Secrets | The platform's secret store, exposed as environment variables. Mount the FCM service account as a file. |
| TLS / routing | Managed load balancer or ingress: `/` → admin-web, `/api` → backend |
| Scaling | **Run one backend instance** for now. The ride sweeper and the demo simulator run in every instance. Rides stay correct (optimistic locking and conditional updates), but work is duplicated. Add a scheduler lock (e.g. ShedLock) before scaling out. |
| Health | Liveness and readiness probe: `GET /actuator/health` |
| Data residency | Check DPDP Act and company policy for where employee and location data may be stored |

## 3. Notifications

Push is configured with `PUSH_PROVIDER`. The apps keep polling every few seconds whichever provider is chosen,
so a push outage slows updates but never breaks rides.

| `PUSH_PROVIDER` | When to use | Settings |
|---|---|---|
| `fcm` (default) | Plant servers can reach Google | `FCM_PROJECT_ID`, `FCM_CREDENTIALS_FILE` (service account JSON with the *Firebase Cloud Messaging API* enabled) |
| `webhook` | No outbound internet; an in-house push relay or MQTT bridge delivers instead | `PUSH_WEBHOOK_URL`, `PUSH_WEBHOOK_AUTH` (sent as the `Authorization` header) |
| `none` | Demos, or polling only | — |

If `fcm` is selected without credentials, the backend logs a warning and falls back to `none` instead of failing to start.

Webhook payloads (HTTP POST, JSON):

```json
// push
{ "tokens": ["<device token>", "..."], "title": "Driver on the way", "body": "Vehicle assigned for your ride", "data": {} }
// sms (SMS_PROVIDER=webhook)
{ "to": "9876543210", "message": "Your plant ride ... OTP 1234 ..." }
```

**Mobile app builds and Firebase:**
- **Android**: download `google-services.json` from the Firebase project and build with
  `GOOGLE_SERVICES_JSON=/path/to/google-services.json`. Without it the app still works, without push.
- **iOS**: registering a raw device token sends an APNs token, which FCM can't use directly. iPhone push needs the
  Firebase iOS SDK set up (or an APNs provider, not built yet). Until then, iOS uses polling.

## 4. Configuration reference (backend environment)

| Variable | Required | Default | Purpose |
|---|---|---|---|
| `DB_URL`, `DB_USER`, `DB_PASSWORD` | yes | — | PostgreSQL connection |
| `JWT_SECRET` | yes | — | 32+ random bytes for signing sessions |
| `JWT_TTL_HOURS` | | 12 | Session length |
| `TRACKING_API_KEY` | yes | — | Shared secret the GPS gateway sends as `X-Api-Key` |
| `BOOTSTRAP_ADMIN_LOGIN` / `_PASSWORD` | first start | `admin` / — | First admin, created only on an empty database |
| `CORS_ORIGINS` | | `http://localhost:5173` | Admin web origin(s), if served from a different origin than the API |
| `PUSH_PROVIDER` … `SMS_WEBHOOK_AUTH` | | `fcm` / `none` | See section 3 |
| `SEED_DEMO`, `DEMO_SIMULATOR` | | `false` | Pilot showcase plant and simulator. **Never in production.** |

Plant-level business settings (speed limit, dispatch radius, timeouts, approval rule, visitor module, cost policies) live
in the database and are edited in *Admin → Plants & settings*.

## 5. Before production go-live

- [ ] Secrets generated and stored outside the repository; bootstrap admin password changed
- [ ] TLS on every endpoint; tracking endpoint reachable only from Traccar
- [ ] PostgreSQL backups scheduled and a restore tested
- [ ] `vehicle_position` partitioned by day, with a retention job; period agreed with Legal (DPDP Act)
- [ ] Rate limiting / lockout on `/api/auth/login` (not built yet), or SSO in its place
- [ ] Self-hosted map tiles (`MAP_TILE_URL`) if the plant layout is confidential
- [ ] Single backend instance, or a scheduler lock added before scaling out
- [ ] Monitoring on `/actuator/health`, plus alerts for GPS fix lag and dispatch backlog
