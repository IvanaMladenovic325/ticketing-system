CREATE TABLE seat_reservations
(
    booking_id        BIGINT PRIMARY KEY,
    event_id          BIGINT                   NOT NULL REFERENCES events (id) ON DELETE CASCADE,
    number_of_tickets INTEGER                  NOT NULL,
    status            VARCHAR(20)              NOT NULL,
    updated_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_seat_reservations_number_of_tickets CHECK (number_of_tickets > 0),
    CONSTRAINT ck_seat_reservations_status CHECK (status IN ('RESERVED', 'RELEASED'))
);

CREATE INDEX ix_seat_reservations_event_id ON seat_reservations (event_id);
