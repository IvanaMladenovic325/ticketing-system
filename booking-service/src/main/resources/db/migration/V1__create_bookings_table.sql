CREATE TABLE bookings
(
    id                BIGSERIAL PRIMARY KEY,
    user_id           BIGINT                   NOT NULL,
    event_id          BIGINT                   NOT NULL,
    number_of_tickets INTEGER                  NOT NULL,
    total_price       NUMERIC(10, 2)           NOT NULL,
    status            VARCHAR(20)              NOT NULL,
    created_at        TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_bookings_number_of_tickets CHECK (number_of_tickets > 0),
    CONSTRAINT ck_bookings_total_price CHECK (total_price >= 0),
    CONSTRAINT ck_bookings_status CHECK (status IN ('PENDING_PAYMENT', 'CONFIRMED', 'CANCELLED', 'PAYMENT_FAILED'))
);

CREATE INDEX ix_bookings_user_id ON bookings (user_id);
CREATE INDEX ix_bookings_event_id ON bookings (event_id);
