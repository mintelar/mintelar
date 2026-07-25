-- Rate limiting table (persistent, atomic counters)

CREATE TABLE IF NOT EXISTS api_rate_limits (
  id bigserial PRIMARY KEY,
  user_id uuid,
  ip_hash text,
  endpoint text NOT NULL,
  group_id bigint,
  request_count integer NOT NULL DEFAULT 1,
  window_start timestamptz NOT NULL DEFAULT now(),
  created_at timestamptz NOT NULL DEFAULT now()
);

-- Unique constraints for atomic upserts
CREATE UNIQUE INDEX idx_rate_limit_user_endpoint
  ON api_rate_limits(user_id, endpoint, window_start)
  WHERE user_id IS NOT NULL;

CREATE UNIQUE INDEX idx_rate_limit_ip_endpoint
  ON api_rate_limits(ip_hash, endpoint, window_start)
  WHERE ip_hash IS NOT NULL;

CREATE UNIQUE INDEX idx_rate_limit_group_endpoint
  ON api_rate_limits(group_id, endpoint, window_start)
  WHERE group_id IS NOT NULL;

-- Cleanup function: delete old rate limit entries
CREATE OR REPLACE FUNCTION cleanup_old_rate_limits()
RETURNS void AS $$
BEGIN
  DELETE FROM api_rate_limits
  WHERE window_start < now() - interval '2 hours';
END;
$$ LANGUAGE plpgsql;
