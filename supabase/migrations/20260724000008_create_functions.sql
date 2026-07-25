-- Atomic rate limiting function
-- Returns true if request is allowed, false if rate limited

CREATE OR REPLACE FUNCTION atomic_check_rate_limit(
  p_user_id uuid,
  p_ip_hash text,
  p_endpoint text,
  p_group_id bigint,
  p_max_requests integer,
  p_window_seconds integer
)
RETURNS boolean AS $$
DECLARE
  v_window_start timestamptz;
  v_current_count integer;
BEGIN
  v_window_start := date_trunc('hour', now())
    + (EXTRACT(minute FROM now())::int / p_window_seconds * p_window_seconds || ' seconds')::interval;

  -- Try to insert or increment for user
  IF p_user_id IS NOT NULL THEN
    INSERT INTO api_rate_limits (user_id, endpoint, group_id, request_count, window_start)
    VALUES (p_user_id, p_endpoint, p_group_id, 1, v_window_start)
    ON CONFLICT (user_id, endpoint, window_start)
    DO UPDATE SET request_count = api_rate_limits.request_count + 1
    RETURNING request_count INTO v_current_count;

    IF v_current_count > p_max_requests THEN
      RETURN false;
    END IF;
  END IF;

  -- Try to insert or increment for IP
  IF p_ip_hash IS NOT NULL THEN
    INSERT INTO api_rate_limits (ip_hash, endpoint, group_id, request_count, window_start)
    VALUES (p_ip_hash, p_endpoint, p_group_id, 1, v_window_start)
    ON CONFLICT (ip_hash, endpoint, window_start)
    DO UPDATE SET request_count = api_rate_limits.request_count + 1
    RETURNING request_count INTO v_current_count;

    IF v_current_count > p_max_requests THEN
      RETURN false;
    END IF;
  END IF;

  -- Try to insert or increment for group
  IF p_group_id IS NOT NULL THEN
    INSERT INTO api_rate_limits (group_id, endpoint, request_count, window_start)
    VALUES (p_group_id, p_endpoint, 1, v_window_start)
    ON CONFLICT (group_id, endpoint, window_start)
    DO UPDATE SET request_count = api_rate_limits.request_count + 1
    RETURNING request_count INTO v_current_count;

    IF v_current_count > p_max_requests THEN
      RETURN false;
    END IF;
  END IF;

  RETURN true;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
