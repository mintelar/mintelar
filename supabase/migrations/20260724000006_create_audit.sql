-- Security audit events table

CREATE TABLE IF NOT EXISTS security_audit_events (
  id uuid PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id uuid,
  user_role text,
  action text NOT NULL,
  group_id bigint,
  details jsonb,
  ip_hash text,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_audit_user_id ON security_audit_events(user_id);
CREATE INDEX idx_audit_action ON security_audit_events(action);
CREATE INDEX idx_audit_group_id ON security_audit_events(group_id);
CREATE INDEX idx_audit_created_at ON security_audit_events(created_at);
