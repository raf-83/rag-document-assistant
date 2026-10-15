-- AI Document Vault
-- Etap 3 - Auth i bezpieczeństwo użytkownika
-- Migracja zakłada pustą albo zresetowaną bazę dev w zakresie tabeli users.
-- Jeśli masz już istniejących użytkowników, trzeba najpierw przygotować backfill password_hash.

ALTER TABLE users
    ADD COLUMN password_hash VARCHAR(255),
    ADD COLUMN email_verified_at TIMESTAMP WITH TIME ZONE,
    ADD COLUMN last_login_at TIMESTAMP WITH TIME ZONE;

-- Przejście z uproszczonego statusu Etapu 2 do statusów Etapu 3.
-- Dla środowiska dev/mapowania historycznych rekordów przyjmujemy:
-- INACTIVE -> PENDING_VERIFICATION
UPDATE users
SET status = 'PENDING_VERIFICATION'
WHERE status = 'INACTIVE';

ALTER TABLE users
    ALTER COLUMN password_hash SET NOT NULL;
