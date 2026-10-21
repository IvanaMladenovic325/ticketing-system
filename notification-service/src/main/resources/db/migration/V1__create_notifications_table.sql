CREATE TABLE notifications
(
    id              BIGSERIAL PRIMARY KEY,
    booking_id      BIGINT                   NOT NULL,
    recipient_email VARCHAR(255)             NOT NULL,
    type            VARCHAR(30)              NOT NULL,
    sent_at         TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_notifications_type CHECK (type IN ('BOOKING_CONFIRMED', 'BOOKING_CANCELLED')),
    -- za jednu rezervaciju šalje se najviše jedan mejl po tipu (zaštita od duplih poruka)
    CONSTRAINT uq_notifications_booking_type UNIQUE (booking_id, type)
);
