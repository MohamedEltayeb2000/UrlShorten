-- Change record on every approval (risk class, change ticket, justification, proposed commands and migrations).
alter table approval add column risk_class varchar(8);
alter table approval add column change_ticket varchar(64);
alter table approval add column risk_justification varchar(1000);
alter table approval add column proposed_commands varchar(2000);
alter table approval add column proposed_migrations varchar(2000);
