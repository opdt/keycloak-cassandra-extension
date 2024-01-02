CREATE TABLE IF NOT EXISTS events (
    id text,
    time timestamp,
    type text,
    realm_id text,
    client_id text,
    user_id text,
    session_id text,
    ip_address text,
    error text,
    details_json,
    details_json_long_value,
    PRIMARY KEY (realm_id, id)
) WITH CLUSTERING ORDER BY (id ASC);

CREATE TABLE IF NOT EXISTS admin_events (
    id text,
    time timestamp,
    realm_id text,
    operation_type text,
    resource_type text,
    auth_realm_id text,
    auth_client_id text,
    auth_user_id text,
    auth_ip_address text,
    resource_path text,
    representation text,
    error text,
    PRIMARY KEY (realm_id, id)
) WITH CLUSTERING ORDER BY (id ASC);
