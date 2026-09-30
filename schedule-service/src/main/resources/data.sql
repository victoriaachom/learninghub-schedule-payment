-- Dev-only seed data (ignored under the prod profile).
-- Term dates are relative to today so "today", "week" and the term bar always
-- have classes to show in a demo, whatever day you run it.
-- The current term starts on the Monday five weeks ago and runs 60 days (to a Friday).

INSERT INTO term (id, name, start_date, end_date, is_current, max_credits, quarter, academic_year) VALUES
  ('q1-2026', 'Q1 · Fall 2026',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '60 days')::date,
      true, 30, 1, '2026-2027'),
  ('q2-2026', 'Q2 · Winter 2026',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '63 days')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '123 days')::date,
      false, 30, 2, '2026-2027'),
  ('q3-2027', 'Q3 · Winter 2027',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '140 days')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '200 days')::date,
      false, 30, 3, '2026-2027'),
  ('q4-2027', 'Q4 · Spring 2027',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '203 days')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '263 days')::date,
      false, 30, 4, '2026-2027');

-- Q1 class sessions. course_id and teacher_username are shared ids from
-- enrollment-service and user-service. start_date / end_date left empty = whole term.
INSERT INTO class_session (term_id, course_id, subject, grade_level, color, teacher, teacher_username, days, start_time, end_time) VALUES
  ('q1-2026', 'ap-chemistry',   'AP Chemistry',          'Junior', 'red',    'Dr Priya Patel',   'ppatel',    '["Mon","Wed","Fri"]', '08:00', '09:00'),
  ('q1-2026', 'ap-physics-2',   'AP Physics 2',          'Junior', 'purple', 'Mr David Carter',  'dcarter',   '["Tue","Thu"]',       '09:00', '10:00'),
  ('q1-2026', 'ap-precalc',     'AP Pre Calculus',       'Junior', 'blue',   'Dr John Anderson', 'janderson', '["Mon","Tue","Wed"]', '10:00', '11:00'),
  ('q1-2026', 'ap-calc-bc',     'AP Calculus BC',        'Senior', 'blue',   'Dr John Anderson', 'janderson', '["Thu","Fri"]',       '11:00', '12:00'),
  ('q1-2026', 'ap-english-lit', 'AP English Literature', 'Junior', 'green',  'Ms Sarah Wilson',  'swilson',   '["Mon","Wed","Fri"]', '13:00', '14:00'),
  ('q1-2026', 'ap-cs-a',        'AP Computer Science A', 'Junior', 'orange', 'Mr Kevin Lee',     'klee',      '["Tue","Thu"]',       '15:00', '16:00');
