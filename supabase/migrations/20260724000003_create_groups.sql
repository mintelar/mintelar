-- Groups and Group Members tables

CREATE TABLE IF NOT EXISTS groups (
  id bigserial PRIMARY KEY,
  name text NOT NULL,
  course_id bigint NOT NULL REFERENCES courses(id) ON DELETE RESTRICT,
  estado text NOT NULL DEFAULT 'pending'
    CHECK (estado IN ('pending', 'approved', 'processing', 'rewarded', 'failed')),
  created_at timestamptz NOT NULL DEFAULT now(),
  updated_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_groups_course_id ON groups(course_id);
CREATE INDEX idx_groups_estado ON groups(estado);

CREATE TRIGGER groups_updated_at
  BEFORE UPDATE ON groups
  FOR EACH ROW
  EXECUTE FUNCTION update_updated_at();

CREATE TABLE IF NOT EXISTS group_members (
  id bigserial PRIMARY KEY,
  group_id bigint NOT NULL REFERENCES groups(id) ON DELETE CASCADE,
  user_id uuid NOT NULL REFERENCES profiles(id) ON DELETE CASCADE,
  approved boolean NOT NULL DEFAULT false,
  approved_at timestamptz,
  created_at timestamptz NOT NULL DEFAULT now(),
  UNIQUE(group_id, user_id)
);

CREATE INDEX idx_group_members_group_id ON group_members(group_id);
CREATE INDEX idx_group_members_user_id ON group_members(user_id);
CREATE INDEX idx_group_members_approved ON group_members(approved);
