create table scorer_assignments (
    account_id uuid not null,
    scoreboard_id uuid not null,
    assigned_at timestamp with time zone not null,
    assigned_by uuid not null,
    primary key (account_id, scoreboard_id),
    constraint fk_scorer_assignments_account
        foreign key (account_id) references accounts (id) on delete cascade,
    constraint fk_scorer_assignments_scoreboard
        foreign key (scoreboard_id) references scoreboards (id) on delete cascade,
    constraint fk_scorer_assignments_actor
        foreign key (assigned_by) references accounts (id)
);

alter table period_participants alter column current_score set data type bigint;

create table score_transactions (
    id uuid primary key,
    period_id uuid not null,
    team_id uuid not null,
    member_id uuid,
    kind varchar(8) not null,
    amount integer not null,
    resulting_score bigint not null,
    reason varchar(500) not null,
    created_at timestamp with time zone not null,
    created_by uuid not null,
    idempotency_key varchar(128),
    constraint uq_score_transactions_idempotency unique (created_by, idempotency_key),
    constraint fk_score_transactions_period
        foreign key (period_id) references competition_periods (id),
    constraint fk_score_transactions_team
        foreign key (team_id) references teams (id),
    constraint fk_score_transactions_member
        foreign key (member_id) references members (id),
    constraint fk_score_transactions_actor
        foreign key (created_by) references accounts (id),
    constraint ck_score_transactions_kind check (kind in ('CREDIT', 'DEBIT')),
    constraint ck_score_transactions_amount check (amount between 1 and 1000),
    constraint ck_score_transactions_resulting_score check (resulting_score >= 0),
    constraint ck_score_transactions_reason check (length(trim(reason)) > 0),
    constraint ck_score_transactions_idempotency
        check (idempotency_key is null or length(trim(idempotency_key)) > 0)
);

create index ix_score_transactions_period_created
    on score_transactions (period_id, created_at desc, id desc);

create table score_cancellations (
    transaction_id uuid primary key,
    reason varchar(500) not null,
    created_at timestamp with time zone not null,
    created_by uuid not null,
    constraint fk_score_cancellations_transaction
        foreign key (transaction_id) references score_transactions (id),
    constraint fk_score_cancellations_actor
        foreign key (created_by) references accounts (id),
    constraint ck_score_cancellations_reason check (length(trim(reason)) > 0)
);
