-- Trigger: when 3/3 members approve → group becomes 'ready' for auto-reward
-- Trigger: max 3 members per group

-- 1. Add 'ready' to the groups.estado CHECK constraint
ALTER TABLE groups DROP CONSTRAINT IF EXISTS groups_estado_check;
ALTER TABLE groups ADD CONSTRAINT groups_estado_check
  CHECK (estado IN ('pending', 'approved', 'ready', 'processing', 'rewarded', 'failed'));

-- 2. Function: auto-mark group as 'ready' when all members approve
CREATE OR REPLACE FUNCTION on_group_member_approved()
RETURNS TRIGGER AS $$
DECLARE
  total_members INT;
  approved_count INT;
BEGIN
  IF NEW.approved = true THEN
    SELECT count(*) INTO total_members
    FROM group_members WHERE group_id = NEW.group_id;

    SELECT count(*) INTO approved_count
    FROM group_members WHERE group_id = NEW.group_id AND approved = true;

    IF total_members = 3 AND approved_count = 3 THEN
      UPDATE groups SET estado = 'ready', updated_at = now()
      WHERE id = NEW.group_id AND estado = 'pending';

      PERFORM pg_notify('group_completed', json_build_object(
        'group_id', NEW.group_id,
        'total_members', total_members,
        'approved_count', approved_count
      )::text);
    END IF;
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_group_member_approved
  AFTER INSERT OR UPDATE OF approved ON group_members
  FOR EACH ROW
  EXECUTE FUNCTION on_group_member_approved();

-- 3. Function: enforce max 3 members per group
CREATE OR REPLACE FUNCTION enforce_max_group_members()
RETURNS TRIGGER AS $$
DECLARE
  member_count INT;
BEGIN
  SELECT count(*) INTO member_count
  FROM group_members WHERE group_id = NEW.group_id;

  IF member_count >= 3 THEN
    RAISE EXCEPTION 'Group cannot have more than 3 members';
  END IF;
  RETURN NEW;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER trg_enforce_max_members
  BEFORE INSERT ON group_members
  FOR EACH ROW
  EXECUTE FUNCTION enforce_max_group_members();
