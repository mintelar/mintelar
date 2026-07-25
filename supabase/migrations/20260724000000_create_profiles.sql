-- Profiles table linked to auth.users
-- No duplicate users table. Profiles extends auth.users via uuid.

CREATE TABLE IF NOT EXISTS profiles (
  id uuid PRIMARY KEY REFERENCES auth.users(id) ON DELETE CASCADE,
  full_name text,
  wallet_address text,
  role text NOT NULL DEFAULT 'student'
    CHECK (role IN ('student', 'auditor', 'admin')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_profiles_wallet_address ON profiles(wallet_address);
CREATE INDEX idx_profiles_role ON profiles(role);

-- Prevent duplicate wallet addresses across profiles
CREATE UNIQUE INDEX idx_profiles_wallet_unique
  ON profiles(wallet_address)
  WHERE wallet_address IS NOT NULL AND wallet_address != '';

-- Updated_at trigger
CREATE OR REPLACE FUNCTION update_updated_at()
RETURNS TRIGGER AS $$
BEGIN
  NEW.updated_at = now();
  RETURN NEW;
END;
$$ LANGUAGE plpgsql;

CREATE TRIGGER profiles_updated_at
  BEFORE UPDATE ON profiles
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();
