CREATE TABLE payments
(
    id         BIGSERIAL PRIMARY KEY,
    booking_id BIGINT                   NOT NULL,
    amount     NUMERIC(10, 2)           NOT NULL,
    status     VARCHAR(20)              NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE NOT NULL,
    CONSTRAINT ck_payments_amount CHECK (amount > 0),
    CONSTRAINT ck_payments_status CHECK (status IN ('SUCCESS', 'FAILED'))
);

CREATE INDEX ix_payments_booking_id ON payments (booking_id);

-- Jedna rezervacija može imati više neuspešnih pokušaja, ali najviše jedno uspešno plaćanje.
CREATE UNIQUE INDEX ux_payments_booking_id_success ON payments (booking_id) WHERE status = 'SUCCESS';
