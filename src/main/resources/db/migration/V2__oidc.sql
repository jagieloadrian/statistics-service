-- Provider sign-in: an account has a password, a provider identity, or both.
ALTER TABLE users
    ALTER COLUMN password_hash DROP NOT NULL,
    ADD COLUMN oidc_issuer  text,
    ADD COLUMN oidc_subject text,
    ADD CONSTRAINT users_oidc_identity UNIQUE (oidc_issuer, oidc_subject),
    ADD CONSTRAINT users_can_sign_in CHECK (password_hash IS NOT NULL OR oidc_subject IS NOT NULL);
