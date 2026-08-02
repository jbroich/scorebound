create table accounts (
    id uuid primary key,
    username varchar(64) not null,
    normalized_username varchar(64) not null unique,
    password_hash varchar(255) not null,
    enabled boolean not null,
    must_change_password boolean not null,
    member_id uuid,
    preferred_locale varchar(2),
    created_at timestamp with time zone not null,
    created_by uuid,
    modified_at timestamp with time zone not null,
    modified_by uuid,
    constraint ck_accounts_preferred_locale
        check (preferred_locale is null or preferred_locale in ('en', 'de'))
);

create table account_roles (
    account_id uuid not null,
    role varchar(16) not null,
    primary key (account_id, role),
    constraint fk_account_roles_account
        foreign key (account_id) references accounts (id) on delete cascade,
    constraint ck_account_roles_role
        check (role in ('ADMIN', 'SCORER', 'MEMBER', 'DISPLAY'))
);
