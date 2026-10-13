CREATE TABLE events
(
    id              BIGSERIAL PRIMARY KEY,
    name            VARCHAR(200)   NOT NULL,
    description     VARCHAR(2000),
    venue           VARCHAR(200)   NOT NULL,
    date_time       TIMESTAMP      NOT NULL,
    total_seats     INTEGER        NOT NULL,
    available_seats INTEGER        NOT NULL,
    ticket_price    NUMERIC(10, 2) NOT NULL,
    CONSTRAINT ck_events_total_seats CHECK (total_seats > 0),
    CONSTRAINT ck_events_available_seats CHECK (available_seats BETWEEN 0 AND total_seats),
    CONSTRAINT ck_events_ticket_price CHECK (ticket_price >= 0)
);
