BEGIN;

DELETE FROM delivery.partner_payout_requests
WHERE id = 'f4c2f36c-2d11-4c94-843e-26389ea65ea6';

UPDATE delivery.partner_accruals
SET accrual_period_month = to_char(created_at AT TIME ZONE 'UTC', 'YYYY-MM'),
    payout_cycle_month = to_char((created_at AT TIME ZONE 'UTC' + interval '1 month'), 'YYYY-MM'),
    available_from = (date_trunc('month', created_at AT TIME ZONE 'UTC' + interval '1 month') AT TIME ZONE 'UTC')
WHERE partner_account_id = '8e5afea7-4a15-4c92-b8ff-714e067fb8b8';

UPDATE delivery.partner_accounts pa
SET balance = COALESCE((
        SELECT SUM(CASE WHEN a.status = 'ACCRUED' THEN a.amount ELSE 0 END)
               - SUM(CASE WHEN a.status = 'REVERSED' THEN a.amount ELSE 0 END)
        FROM delivery.partner_accruals a
        WHERE a.partner_account_id = pa.id
    ), 0),
    pending_payout = COALESCE((
        SELECT SUM(p.amount)
        FROM delivery.partner_payout_requests p
        WHERE p.partner_account_id = pa.id
          AND p.status IN ('PENDING', 'SCHEDULED')
    ), 0),
    paid_out = COALESCE((
        SELECT SUM(p.amount)
        FROM delivery.partner_payout_requests p
        WHERE p.partner_account_id = pa.id
          AND p.status = 'PAID'
          AND p.payout_method = 'BANK_TRANSFER'
    ), 0),
    transferred_to_main_balance = COALESCE((
        SELECT SUM(p.amount)
        FROM delivery.partner_payout_requests p
        WHERE p.partner_account_id = pa.id
          AND p.status = 'PAID'
          AND p.payout_method = 'TRANSFER_TO_MAIN_BALANCE'
    ), 0),
    available_for_payout = 0,
    updated_at = now()
WHERE pa.id = '8e5afea7-4a15-4c92-b8ff-714e067fb8b8';

COMMIT;
