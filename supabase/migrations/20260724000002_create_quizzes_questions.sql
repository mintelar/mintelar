-- Quizzes and Questions tables

CREATE TABLE IF NOT EXISTS quizzes (
  id bigserial PRIMARY KEY,
  course_id bigint NOT NULL REFERENCES courses(id) ON DELETE CASCADE,
  title text NOT NULL,
  passing_score integer NOT NULL DEFAULT 70
    CHECK (passing_score > 0 AND passing_score <= 100),
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_quizzes_course_id ON quizzes(course_id);

CREATE TABLE IF NOT EXISTS questions (
  id bigserial PRIMARY KEY,
  quiz_id bigint NOT NULL REFERENCES quizzes(id) ON DELETE CASCADE,
  question_text text NOT NULL,
  options jsonb NOT NULL,
  correct_answer text NOT NULL,
  created_at timestamptz NOT NULL DEFAULT now()
);

CREATE INDEX idx_questions_quiz_id ON questions(quiz_id);
