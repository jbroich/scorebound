create table teams (
    id uuid primary key,
    name varchar(100) not null,
    normalized_name varchar(100) not null,
    active_name_key varchar(100) unique,
    short_name varchar(20) not null,
    normalized_short_name varchar(20) not null,
    active_short_name_key varchar(20) unique,
    color varchar(7) not null,
    active boolean not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    modified_at timestamp with time zone not null,
    modified_by uuid
);

create table team_images (
    team_id uuid primary key,
    content_type varchar(32) not null,
    image_data bytea not null,
    updated_at timestamp with time zone not null,
    updated_by uuid,
    constraint fk_team_images_team
        foreign key (team_id) references teams (id) on delete cascade
);

create table members (
    id uuid primary key,
    display_name varchar(100) not null,
    first_name varchar(100),
    last_name varchar(100),
    active boolean not null,
    version bigint not null,
    created_at timestamp with time zone not null,
    created_by uuid,
    modified_at timestamp with time zone not null,
    modified_by uuid
);

create table memberships (
    id uuid primary key,
    member_id uuid not null,
    team_id uuid not null,
    valid_from timestamp with time zone not null,
    valid_until timestamp with time zone,
    open_membership_key uuid unique,
    created_by uuid,
    constraint fk_memberships_member
        foreign key (member_id) references members (id),
    constraint fk_memberships_team
        foreign key (team_id) references teams (id),
    constraint ck_memberships_dates
        check (valid_until is null or valid_until > valid_from),
    constraint ck_memberships_open_key
        check ((valid_until is null and open_membership_key = member_id)
            or (valid_until is not null and open_membership_key is null))
);

create index ix_memberships_member_history
    on memberships (member_id, valid_from desc);

alter table accounts
    add constraint fk_accounts_member
        foreign key (member_id) references members (id);

create unique index uq_accounts_member
    on accounts (member_id);
