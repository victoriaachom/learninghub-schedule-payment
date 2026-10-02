-- Demo data, loaded only by the dev profile (config-service adds classpath:db/seed).
-- Dates are relative to the day the database is first created.
-- Users: sstudent (Sam), jsmith (John), mgarcia (Maria, has an overdue Q1 invoice).
-- Term ids match schedule-service; course ids match enrollment-service.

INSERT INTO billing_profile (username, name, email, address) VALUES
  ('sstudent', 'Sam Student',  'sam.student@learninghub.edu', '1200 E University Blvd, Tucson, AZ 85721'),
  ('jsmith',   'John Smith',   'john.smith@gmail.com',         '1234 Oak Ave, Sunnyvale, CA 94086'),
  ('mgarcia',  'Maria Garcia', 'maria.garcia@gmail.com',       '88 Palm St, Phoenix, AZ 85004');

INSERT INTO payment_method (username, brand, last4, expires, is_default) VALUES
  ('sstudent', 'Visa',       '4242', '08/2028', TRUE),
  ('sstudent', 'Mastercard', '5678', '03/2027', FALSE),
  ('jsmith',   'Visa',       '1111', '11/2027', TRUE),
  ('mgarcia',  'Amex',       '0005', '06/2029', TRUE);

-- Invoices (ids 1-7 in this order)
INSERT INTO invoice (username, term_id, description, amount, due_date, status, paid_on, method_id) VALUES
  ('sstudent', 'q1-2026', 'Q1 · Fall 2026 Tuition',   1600.00, CURRENT_DATE - 10,  'Paid',   CURRENT_DATE - 12,
      (SELECT id FROM payment_method WHERE username = 'sstudent' AND last4 = '4242')),
  ('sstudent', 'q2-2026', 'Q2 · Winter 2026 Tuition', 1600.00, CURRENT_DATE + 26,  'Unpaid', NULL, NULL),
  ('sstudent', 'q3-2027', 'Q3 · Winter 2027 Tuition', 1600.00, CURRENT_DATE + 110, 'Unpaid', NULL, NULL),
  ('jsmith',   'q1-2026', 'Q1 · Fall 2026 Tuition',    960.00, CURRENT_DATE - 10,  'Paid',   CURRENT_DATE - 15,
      (SELECT id FROM payment_method WHERE username = 'jsmith' AND last4 = '1111')),
  ('jsmith',   'q2-2026', 'Q2 · Winter 2026 Tuition',  960.00, CURRENT_DATE + 26,  'Unpaid', NULL, NULL),
  ('mgarcia',  'q1-2026', 'Q1 · Fall 2026 Tuition',    640.00, CURRENT_DATE - 10,  'Unpaid', NULL, NULL),
  ('mgarcia',  'q2-2026', 'Q2 · Winter 2026 Tuition',  640.00, CURRENT_DATE + 26,  'Unpaid', NULL, NULL);

-- One $320 line per course on each invoice
INSERT INTO invoice_line (invoice_id, course_id, description, amount)
SELECT i.id, c.course_id, c.description, 320.00
FROM invoice i
JOIN (VALUES
        ('sstudent', 'ap-chemistry',   'AP Chemistry'),
        ('sstudent', 'ap-physics-2',   'AP Physics 2'),
        ('sstudent', 'ap-precalc',     'AP Pre Calculus'),
        ('sstudent', 'ap-english-lit', 'AP English Literature'),
        ('sstudent', 'ap-cs-a',        'AP Computer Science A'),
        ('jsmith',   'ap-calc-bc',     'AP Calculus BC'),
        ('jsmith',   'ap-english-lit', 'AP English Literature'),
        ('jsmith',   'ap-cs-a',        'AP Computer Science A'),
        ('mgarcia',  'ap-chemistry',   'AP Chemistry'),
        ('mgarcia',  'ap-precalc',     'AP Pre Calculus')
     ) AS c (username, course_id, description)
  ON c.username = i.username
ORDER BY i.id, c.course_id;
