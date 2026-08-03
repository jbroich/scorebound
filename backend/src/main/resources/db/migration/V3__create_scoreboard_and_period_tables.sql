create table scoreboards (
    id uuid primary key,
    name varchar(100) not null,
    description varchar(500),
    active boolean not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    modified_at timestamp with time zone not null,
    modified_by uuid
);

create table scoreboard_teams (
    scoreboard_id uuid not null,
    team_id uuid not null,
    position integer not null,
    selected_at timestamp with time zone not null,
    selected_by uuid,
    primary key (scoreboard_id, team_id),
    constraint uq_scoreboard_teams_position unique (scoreboard_id, position),
    constraint fk_scoreboard_teams_scoreboard
        foreign key (scoreboard_id) references scoreboards (id) on delete cascade,
    constraint fk_scoreboard_teams_team
        foreign key (team_id) references teams (id),
    constraint ck_scoreboard_teams_position check (position >= 0)
);

create table competition_periods (
    id uuid primary key,
    scoreboard_id uuid not null,
    name varchar(100) not null,
    starts_at timestamp with time zone not null,
    ends_at timestamp with time zone not null,
    status varchar(16) not null,
    closed_at timestamp with time zone,
    closed_by uuid,
    reopened_at timestamp with time zone,
    reopened_by uuid,
    visual_ceiling integer not null,
    active_scoreboard_key uuid unique,
    version bigint not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    constraint fk_competition_periods_scoreboard
        foreign key (scoreboard_id) references scoreboards (id),
    constraint ck_competition_periods_dates check (ends_at > starts_at),
    constraint ck_competition_periods_status
        check (status in ('SCHEDULED', 'ACTIVE', 'CLOSED')),
    constraint ck_competition_periods_ceiling check (visual_ceiling >= 0),
    constraint ck_competition_periods_active_key
        check ((status = 'ACTIVE' and active_scoreboard_key = scoreboard_id)
            or (status <> 'ACTIVE' and active_scoreboard_key is null))
);

create index ix_competition_periods_scoreboard_start
    on competition_periods (scoreboard_id, starts_at desc);

create table period_participants (
    id uuid primary key,
    period_id uuid not null,
    team_id uuid not null,
    position integer not null,
    current_score integer not null,
    winner boolean not null,
    joined_at timestamp with time zone not null,
    joined_by uuid,
    version bigint not null,
    constraint uq_period_participants_team unique (period_id, team_id),
    constraint uq_period_participants_position unique (period_id, position),
    constraint fk_period_participants_period
        foreign key (period_id) references competition_periods (id),
    constraint fk_period_participants_team
        foreign key (team_id) references teams (id),
    constraint ck_period_participants_position check (position >= 0),
    constraint ck_period_participants_score check (current_score >= 0)
);

create index ix_period_participants_standings
    on period_participants (period_id, current_score desc);
