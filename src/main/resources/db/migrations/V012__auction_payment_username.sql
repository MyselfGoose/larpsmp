-- Durable auction payment recipient (in-game /larp auction payee).

ALTER TABLE auction_runtime
    ADD COLUMN IF NOT EXISTS payment_username TEXT NOT NULL DEFAULT 'auctionhost';

UPDATE auction_runtime
SET payment_username = 'auctionhost'
WHERE payment_username IS NULL OR BTRIM(payment_username) = '';
