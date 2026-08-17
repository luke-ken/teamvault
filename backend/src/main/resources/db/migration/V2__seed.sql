-- Dev seed: two tenants with disjoint members, so cross-tenant isolation is
-- demonstrable (and testable) from the first run. Password for both users:
-- "devpass12" (bcrypt). Fixed UUIDs on purpose: curl examples and tests can
-- reference them.

INSERT INTO company (id, name, tier) VALUES
    ('11111111-1111-1111-1111-111111111111', 'Acme GmbH', 'SMALL'),
    ('22222222-2222-2222-2222-222222222222', 'Globex AG', 'LARGE');

INSERT INTO app_user (id, email, password_hash, display_name) VALUES
    ('aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa', 'alice@acme.example',
     '$2y$10$Zr9sk70LN0AGKm2BjEiLbOyVZ.EaQQJ8y7YTnJneuOiIVZg6m9SVq', 'Alice Admin'),
    ('bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb', 'bob@globex.example',
     '$2y$10$Zr9sk70LN0AGKm2BjEiLbOyVZ.EaQQJ8y7YTnJneuOiIVZg6m9SVq', 'Bob Member');

INSERT INTO membership (id, user_id, company_id, role) VALUES
    ('a1111111-1111-1111-1111-111111111111',
     'aaaaaaaa-aaaa-aaaa-aaaa-aaaaaaaaaaaa',
     '11111111-1111-1111-1111-111111111111', 'ADMIN'),
    ('b2222222-2222-2222-2222-222222222222',
     'bbbbbbbb-bbbb-bbbb-bbbb-bbbbbbbbbbbb',
     '22222222-2222-2222-2222-222222222222', 'MEMBER');
