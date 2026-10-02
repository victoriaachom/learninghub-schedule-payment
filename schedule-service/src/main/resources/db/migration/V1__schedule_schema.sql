-- schedule-service schema (scheduledb). Owned by schedule-service only.
-- Scheduling context: terms (quarters) and the timetable slot of each course.
-- course_id is the shared id of a course owned by enrollment-service (no FK across databases).

CREATE TABLE term (
    id            TEXT    PRIMARY KEY,              -- e.g. q1-2026, shared with every other service
    name          TEXT    NOT NULL,
    start_date    DATE    NOT NULL,
    end_date      DATE    NOT NULL,
    is_current    BOOLEAN NOT NULL DEFAULT FALSE,
    max_credits   INTEGER NOT NULL DEFAULT 30,
    quarter       INTEGER,
    academic_year TEXT
);

CREATE TABLE class_session (
    id          BIGSERIAL PRIMARY KEY,
    course_id   TEXT  NOT NULL UNIQUE,              -- one slot per course
    term_id     TEXT  NOT NULL REFERENCES term (id) ON DELETE CASCADE,
    days        JSONB NOT NULL,                     -- e.g. ["Mon","Wed","Fri"]
    start_time  TIME  NOT NULL,
    end_time    TIME  NOT NULL,                     -- always start_time + 1 hour
    start_date  DATE,                               -- optional; empty = the term's dates
    end_date    DATE
);

CREATE INDEX idx_class_session_term ON class_session (term_id);
