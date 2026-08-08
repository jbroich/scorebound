create table display_assignments (
    account_id uuid not null,
    scoreboard_id uuid not null,
    position integer not null,
    assigned_at timestamp with time zone not null,
    assigned_by uuid not null,
    primary key (account_id, scoreboard_id),
    constraint fk_display_assignments_account
        foreign key (account_id) references accounts (id) on delete cascade,
    constraint fk_display_assignments_scoreboard
        foreign key (scoreboard_id) references scoreboards (id) on delete cascade,
    constraint fk_display_assignments_actor
        foreign key (assigned_by) references accounts (id),
    constraint uq_display_assignments_position unique (account_id, position)
);

create table display_configurations (
    account_id uuid primary key,
    mode varchar(16) not null,
    fixed_scoreboard_id uuid,
    rotation_seconds integer not null,
    sound_enabled boolean not null,
    modified_at timestamp with time zone not null,
    constraint fk_display_configurations_account
        foreign key (account_id) references accounts (id) on delete cascade,
    constraint fk_display_configurations_scoreboard
        foreign key (fixed_scoreboard_id) references scoreboards (id) on delete set null,
    constraint ck_display_configurations_mode check (mode in ('FIXED', 'ROTATION')),
    constraint ck_display_configurations_rotation check (rotation_seconds between 10 and 300)
);
