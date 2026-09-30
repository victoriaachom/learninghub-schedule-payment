-- Dev-only seed data (ignored under the prod profile).
-- Dates are relative to today so the statuses (Paid / Overdue / Upcoming /
-- Scheduled) always look right in a demo, whatever day you run it.
-- Users: sstudent (Sam, student), jsmith (John, student), mgarcia (Maria, student, has an overdue bill).

INSERT INTO billing_profile (username, name, email, address) VALUES
  ('sstudent', 'Sam Student',  'sam.student@learninghub.edu', '1200 E University Blvd, Tucson, AZ 85721'),
  ('jsmith',   'John Smith',   'john.smith@gmail.com',         '1234 Oak Ave, Sunnyvale, CA 94086'),
  ('mgarcia',  'Maria Garcia', 'maria.garcia@gmail.com',       '88 Palm St, Phoenix, AZ 85004');

INSERT INTO payment_method (username, brand, last4, expires, is_default) VALUES
  ('sstudent', 'Visa',       '4242', '08/2028', true),
  ('sstudent', 'Mastercard', '5678', '03/2027', false),
  ('jsmith',   'Visa',       '1111', '11/2027', true),
  ('mgarcia',  'Amex',       '0005', '06/2029', true);

-- sstudent: Q1 paid, Q2 upcoming, Q3 scheduled
INSERT INTO invoice (username, description, amount, due_date, status, paid_on, method_id) VALUES
  ('sstudent', 'Q1 · Fall 2026 Tuition',   1600.00, CURRENT_DATE - 40, 'Paid',   CURRENT_DATE - 42,
      (SELECT id FROM payment_method WHERE username = 'sstudent' AND last4 = '4242')),
  ('sstudent', 'Q2 · Winter 2026 Tuition', 1600.00, CURRENT_DATE + 26, 'Unpaid', NULL, NULL),
  ('sstudent', 'Q3 · Winter 2027 Tuition', 1600.00, CURRENT_DATE + 110, 'Unpaid', NULL, NULL);

-- jsmith: Q1 paid, Q2 upcoming
INSERT INTO invoice (username, description, amount, due_date, status, paid_on, method_id) VALUES
  ('jsmith', 'Q1 · Fall 2026 Tuition',   1280.00, CURRENT_DATE - 40, 'Paid',   CURRENT_DATE - 45,
      (SELECT id FROM payment_method WHERE username = 'jsmith' AND last4 = '1111')),
  ('jsmith', 'Q2 · Winter 2026 Tuition', 1280.00, CURRENT_DATE + 26, 'Unpaid', NULL, NULL);

-- mgarcia: Q1 overdue, Q2 upcoming
INSERT INTO invoice (username, description, amount, due_date, status, paid_on, method_id) VALUES
  ('mgarcia', 'Q1 · Fall 2026 Tuition',   1600.00, CURRENT_DATE - 10, 'Unpaid', NULL, NULL),
  ('mgarcia', 'Q2 · Winter 2026 Tuition', 1600.00, CURRENT_DATE + 26, 'Unpaid', NULL, NULL);
