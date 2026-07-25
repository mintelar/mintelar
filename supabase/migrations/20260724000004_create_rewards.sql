-- Rewards, Reward Recipients, and Reward Attempts tables

CREATE TABLE IF NOT EXISTS rewards (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  group_id bigint NOT NULL REFERENCES groups(id) ON DELETE RESTRICT,
  idempotency_key text NOT NULL UNIQUE,
  status text NOT NULL DEFAULT 'pending'
    CHECK (status IN ('pending', 'processing', 'submitted', 'confirmed', 'failed')),
  total_amount numeric(78, 0),
  transaction_hash text UNIQUE,
  block_number bigint,
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE UNIQUE INDEX idx_rewards_group_id ON rewards(group_id);
CREATE INDEX idx_rewards_status ON rewards(status);
CREATE INDEX idx_rewards_idempotency_key ON rewards(idempotency_key);

CREATE TRIGGER rewards_updated_at
  BEFORE UPDATE ON rewards
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();

CREATE TABLE IF NOT EXISTS reward_recipients (
  id bigserial PRIMARY KEY,
  reward_id uuid NOT NULL REFERENCES rewards(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE RESTRICT,
  wallet_address text NOT NULL,
  amount numeric(78, 0) NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_reward_recipients_reward_id ON reward_recipients(reward_id);

CREATE TABLE IF NOT EXISTS reward_attempts (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  reward_id uuid NOT NULL REFERENCES rewards(id) ON DELETE CASCADE,
  action text NOT NULL,
  status text NOT NULL,
  error_message text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_reward_attempts_reward_id ON reward_attempts(reward_id);
