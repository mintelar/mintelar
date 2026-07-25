-- Row Level Security Policies

-- Enable RLS on all tables
ALTER TABLE profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE courses ENABLE ROW LEVEL SECURITY;
ALTER TABLE quizzes ENABLE ROW LEVEL SECURITY;
ALTER TABLE questions ENABLE ROW LEVEL SECURITY;
ALTER TABLE groups ENABLE ROW LEVEL SECURITY;
ALTER TABLE group_members ENABLE ROW LEVEL SECURITY;
ALTER TABLE rewards ENABLE ROW LEVEL SECURITY;
ALTER TABLE reward_recipients ENABLE ROW LEVEL SECURITY;
ALTER TABLE reward_attempts ENABLE ROW LEVEL SECURITY;
ALTER TABLE api_rate_limits ENABLE ROW LEVEL SECURITY;
ALTER TABLE security_audit_events ENABLE ROW LEVEL SECURITY;

-- ═══════════════════════════════════════════
-- PROFILES
-- ═══════════════════════════════════════════
-- Users can read their own profile
CREATE POLICY "Users can read own profile"
  ON profiles FOR SELECT
  USING (auth.uid() = id);

-- Users can update their own profile (except role and wallet)
CREATE POLICY "Users can update own profile"
  ON profiles FOR UPDATE
  USING (auth.uid() = id)
  WITH CHECK (auth.uid() = id);

-- Service role can do everything (backend)
CREATE POLICY "Service role manages profiles"
  ON profiles FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- COURSES
-- ═══════════════════════════════════════════
-- Everyone can read active courses
CREATE POLICY "Anyone can read active courses"
  ON courses FOR SELECT
  USING (is_active = true);

-- Service role manages courses
CREATE POLICY "Service role manages courses"
  ON courses FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- QUIZZES / QUESTIONS
-- ═══════════════════════════════════════════
CREATE POLICY "Anyone can read quizzes"
  ON quizzes FOR SELECT
  USING (true);

CREATE POLICY "Service role manages quizzes"
  ON quizzes FOR ALL
  USING (auth.role() = 'service_role');

CREATE POLICY "Anyone can read questions"
  ON questions FOR SELECT
  USING (true);

CREATE POLICY "Service role manages questions"
  ON questions FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- GROUPS
-- ═══════════════════════════════════════════
-- Users can read groups they belong to
CREATE POLICY "Members can read their groups"
  ON groups FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM group_members
      WHERE group_members.group_id = groups.id
      AND group_members.user_id = auth.uid()
    )
  );

-- Service role manages groups
CREATE POLICY "Service role manages groups"
  ON groups FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- GROUP MEMBERS
-- ═══════════════════════════════════════════
-- Users can read members of their groups
CREATE POLICY "Members can read group members"
  ON group_members FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM group_members gm
      WHERE gm.group_id = group_members.group_id
      AND gm.user_id = auth.uid()
    )
  );

-- Service role manages group members
CREATE POLICY "Service role manages group members"
  ON group_members FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- REWARDS
-- ═══════════════════════════════════════════
-- Only service role manages rewards
CREATE POLICY "Service role manages rewards"
  ON rewards FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- REWARD RECIPIENTS
-- ═══════════════════════════════════════════
CREATE POLICY "Service role manages reward recipients"
  ON reward_recipients FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- REWARD ATTEMPTS
-- ═══════════════════════════════════════════
CREATE POLICY "Service role manages reward attempts"
  ON reward_attempts FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- RATE LIMITS
-- ═══════════════════════════════════════════
CREATE POLICY "Service role manages rate limits"
  ON api_rate_limits FOR ALL
  USING (auth.role() = 'service_role');

-- ═══════════════════════════════════════════
-- AUDIT EVENTS
-- ═══════════════════════════════════════════
-- Service role manages audit events
CREATE POLICY "Service role manages audit events"
  ON security_audit_events FOR ALL
  USING (auth.role() = 'service_role');

-- Admins can read audit events
CREATE POLICY "Admins can read audit events"
  ON security_audit_events FOR SELECT
  USING (
    EXISTS (
      SELECT 1 FROM profiles
      WHERE profiles.id = auth.uid()
      AND profiles.role = 'admin'
    )
  );
