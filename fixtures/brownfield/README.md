# Brownfield fixture: demo link service

A small, seeded legacy service used to exercise the brownfield scenario. These files are plain
text fixtures and are NOT compiled.

## Seeded defect

Retrying `POST /links` creates duplicate short links. `LinkService.createLink` inserts a new row
on every call and there is no idempotency: no `Idempotency-Key` header, no request fingerprint,
no unique constraint on the target URL and no idempotency record table. A client that times out
and retries gets a second, different short code for the same URL.

## Layout

- `src/main/java/demo/LinkController.java` - HTTP entry point (`POST /links`)
- `src/main/java/demo/LinkService.java` - creates the link and generates the code
- `src/main/java/demo/LinkRepository.java` - JDBC access to the `links` table
- `src/main/resources/schema.sql` - database schema
