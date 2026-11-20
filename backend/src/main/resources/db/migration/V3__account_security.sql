-- UC-402 gives new users a *temporary* password, so they must choose their own at first login.
-- credentials_changed_at invalidates older tokens after a password change or reset.
ALTER TABLE app_user
    ADD COLUMN must_change_password   BOOLEAN     NOT NULL DEFAULT FALSE,
    ADD COLUMN credentials_changed_at TIMESTAMPTZ NOT NULL DEFAULT now();
