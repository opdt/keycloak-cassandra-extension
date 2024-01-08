CREATE TABLE IF NOT EXISTS events (
    id text,
    time bigint,
    type text,
    realm_id text,
    client_id text,
    user_id text,
    session_id text,
    ip_address text,
    error text,
    details_json text,
    details_json_long_value text,
    PRIMARY KEY ((realm_id), time, id)
) WITH CLUSTERING ORDER BY (time DESC);

CREATE TABLE IF NOT EXISTS admin_events (
    id text,
    time bigint,
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
    PRIMARY KEY ((realm_id), time, id)
) WITH CLUSTERING ORDER BY (time DESC);
