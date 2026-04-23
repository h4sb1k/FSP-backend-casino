INSERT INTO users (username, passwordHash, vipTier, role, balance) VALUES
    ('aleksey_m',  '$2b$12$AA.Ag6Ftn5.XXpxANKf0tOSQGIDgjyOgjqsGW7sMf/iZ4S.pu15py', 'GOLD',     'USER',  50000),
    ('vip_player', '$2b$12$AA.Ag6Ftn5.XXpxANKf0tOSQGIDgjyOgjqsGW7sMf/iZ4S.pu15py', 'PLATINUM', 'ADMIN', 200000),
    ('lucky77',    '$2b$12$AA.Ag6Ftn5.XXpxANKf0tOSQGIDgjyOgjqsGW7sMf/iZ4S.pu15py', 'SILVER',   'USER',  15000),
    ('new_player', '$2b$12$AA.Ag6Ftn5.XXpxANKf0tOSQGIDgjyOgjqsGW7sMf/iZ4S.pu15py', 'STANDARD', 'USER',  3000),
    ('pro_gamer',  '$2b$12$AA.Ag6Ftn5.XXpxANKf0tOSQGIDgjyOgjqsGW7sMf/iZ4S.pu15py', 'GOLD',     'USER',  75000);

INSERT INTO admin_config (id) VALUES (1) ON CONFLICT DO NOTHING;

INSERT INTO rooms (status, tier, maxSlots, entryFee, prizePoolPct, boostEnabled, boostCost, boostMultiplier)
VALUES
    ('WAITING', 'STANDARD', 4, 100,  80, TRUE,  50,   2.0),
    ('WAITING', 'SILVER',   6, 500,  80, TRUE,  200,  2.0),
    ('WAITING', 'GOLD',     8, 2000, 75, TRUE,  800,  2.5),
    ('WAITING', 'STANDARD', 6, 200,  80, FALSE, NULL, 1.0);
