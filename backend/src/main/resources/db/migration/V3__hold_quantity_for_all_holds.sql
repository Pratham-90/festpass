-- Every hold records how many tickets it is for, seated holds included.
-- Payment books the hold's seats with a conditional update and checks the updated row count
-- against this number, and the per-user limit can simply add up quantities.
-- tier_id stays general-admission only.

ALTER TABLE holds DROP CONSTRAINT holds_check;
ALTER TABLE holds ALTER COLUMN quantity SET NOT NULL;
