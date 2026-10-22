-- An expense needn't be split: with nobody in split_between it's just noted as paid by paid_by. When split, shares
-- (øre per person, in split_between's order) can be uneven; null = evenly.
alter table expense drop constraint expense_split_between_check;
alter table expense add column shares bigint[];
