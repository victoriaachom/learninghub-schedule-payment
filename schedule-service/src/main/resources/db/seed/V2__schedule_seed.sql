-- Demo data, loaded only by the dev profile (config-service adds classpath:db/seed).
-- Dates are relative to the day the database is first created, so the current
-- term is always in progress: it starts on the Monday five weeks earlier and runs 60 days.

INSERT INTO term (id, name, start_date, end_date, is_current, max_credits, quarter, academic_year) VALUES
  ('q1-2026', 'Q1 · Fall 2026',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '60 days')::date,
      TRUE, 30, 1, '2026-2027'),
  ('q2-2026', 'Q2 · Winter 2026',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '63 days')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '123 days')::date,
      FALSE, 30, 2, '2026-2027'),
  ('q3-2027', 'Q3 · Winter 2027',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '140 days')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '200 days')::date,
      FALSE, 30, 3, '2026-2027'),
  ('q4-2027', 'Q4 · Spring 2027',
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '203 days')::date,
      (date_trunc('week', CURRENT_DATE) - INTERVAL '5 weeks' + INTERVAL '263 days')::date,
      FALSE, 30, 4, '2026-2027');

-- One slot per Q1 course. Names, grade levels and teachers live in enrollment-service.
INSERT INTO class_session (course_id, term_id, days, start_time, end_time) VALUES
  ('ap-chemistry',   'q1-2026', '["Mon","Wed","Fri"]', '08:00', '09:00'),
  ('ap-physics-2',   'q1-2026', '["Tue","Thu"]',       '09:00', '10:00'),
  ('ap-precalc',     'q1-2026', '["Mon","Tue","Wed"]', '10:00', '11:00'),
  ('ap-calc-bc',     'q1-2026', '["Thu","Fri"]',       '11:00', '12:00'),
  ('ap-english-lit', 'q1-2026', '["Mon","Wed","Fri"]', '13:00', '14:00'),
  ('ap-cs-a',        'q1-2026', '["Tue","Thu"]',       '15:00', '16:00');
