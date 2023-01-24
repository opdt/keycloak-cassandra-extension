CREATE TABLE IF NOT EXISTS attributes_to_user_sessions (
    attribute_name text,
    attribute_value text,
    user_session_id text,
    PRIMARY KEY (attribute_name, attribute_value, user_session_id)
) WITH CLUSTERING ORDER BY (attribute_value ASC, user_session_id ASC);

CREATE TABLE IF NOT EXISTS authentication_sessions (
    parent_session_id text,
    tab_id text,
    action text,
    client_id text,
    protocol text,
    redirect_uri text,
    timestamp bigint,
    user_id text,
    auth_notes map<text, text>,
    client_notes map<text, text>,
    client_scopes set<text>,
    execution_status map<text, text>,
    required_actions set<text>,
    user_notes map<text, text>,
    PRIMARY KEY (parent_session_id, tab_id)
) WITH CLUSTERING ORDER BY (tab_id ASC);

CREATE TABLE IF NOT EXISTS client_initial_accesses (
    realm_id text,
    id text,
    count int,
    expiration bigint,
    remaining_count int,
    timestamp bigint,
    PRIMARY KEY (realm_id, id)
) WITH CLUSTERING ORDER BY (id ASC);

CREATE TABLE IF NOT EXISTS client_scopes (
    realm_id text PRIMARY KEY,
    client_scopes frozen<set<text>>
);

CREATE TABLE IF NOT EXISTS clients (
    realm_id text,
    id text,
    attributes map<text, frozen<set<text>>>,
    PRIMARY KEY (realm_id, id)
) WITH CLUSTERING ORDER BY (id ASC);

CREATE TABLE IF NOT EXISTS federated_identities (
    user_id text,
    identity_provider text,
    broker_user_id text,
    broker_user_name text,
    created_timestamp timestamp,
    identity_token text,
    realm_id text,
    PRIMARY KEY (user_id, identity_provider)
) WITH CLUSTERING ORDER BY (identity_provider ASC);

CREATE TABLE IF NOT EXISTS federated_identity_to_user_mapping (
    broker_user_id text,
    identity_provider text,
    user_id text,
    PRIMARY KEY ((broker_user_id, identity_provider))
);

CREATE TABLE IF NOT EXISTS login_failures (
    user_id text,
    id text,
    failed_login_not_before bigint,
    last_failure bigint,
    last_ip_failure text,
    num_failures int,
    realm_id text,
    PRIMARY KEY (user_id, id)
) WITH CLUSTERING ORDER BY (id ASC);

CREATE TABLE IF NOT EXISTS name_to_client_scope (
    realm_id text,
    name text,
    id text,
    PRIMARY KEY (realm_id, name)
) WITH CLUSTERING ORDER BY (name ASC);

CREATE TABLE IF NOT EXISTS name_to_realm (
    name text PRIMARY KEY,
    id text
);

CREATE TABLE IF NOT EXISTS realms (
    id text PRIMARY KEY,
    name text,
    attributes map<text, frozen<set<text>>>
);

CREATE TABLE IF NOT EXISTS realms_to_users (
    realm_id text,
    service_account boolean,
    user_id text,
    PRIMARY KEY (realm_id, service_account, user_id)
) WITH CLUSTERING ORDER BY (service_account ASC, user_id ASC);

CREATE TABLE IF NOT EXISTS roles (
    realm_id text PRIMARY KEY,
    realm_roles frozen<set<text>>,
    client_roles map<text, frozen<set<text>>>
);

CREATE TABLE IF NOT EXISTS root_authentication_sessions (
    id text PRIMARY KEY,
    expiration bigint,
    realm_id text,
    timestamp bigint
);

CREATE TABLE IF NOT EXISTS single_use_objects (
    key text PRIMARY KEY,
    notes map<text, text>
);

CREATE TABLE IF NOT EXISTS user_search_index (
    realm_id text,
    name text,
    value text,
    user_id text,
    PRIMARY KEY ((realm_id, name, value), user_id)
) WITH CLUSTERING ORDER BY (user_id ASC);

CREATE TABLE IF NOT EXISTS user_sessions (
    id text PRIMARY KEY,
    auth_method text,
    broker_session_id text,
    broker_user_id text,
    expiration bigint,
    ip_address text,
    last_session_refresh bigint,
    login_username text,
    offline boolean,
    persistence_state text,
    realm_id text,
    remember_me boolean,
    state text,
    timestamp bigint,
    user_id text,
    client_sessions map<text, text>,
    notes map<text, text>
);

CREATE TABLE IF NOT EXISTS user_sessions_to_attributes (
    user_session_id text,
    attribute_name text,
    attribute_values list<text>,
    PRIMARY KEY (user_session_id, attribute_name)
) WITH CLUSTERING ORDER BY (attribute_name ASC);

CREATE TABLE IF NOT EXISTS users (
    realm_id text,
    id text,
    created_timestamp timestamp,
    email text,
    email_verified boolean,
    enabled boolean,
    federation_link text,
    first_name text,
    last_name text,
    service_account boolean,
    service_account_client_link text,
    username text,
    username_case_insensitive text,
    attributes map<text, frozen<list<text>>>,
    client_roles map<text, frozen<set<text>>>,
    credentials set<text>,
    realm_roles set<text>,
    required_actions set<text>,
    PRIMARY KEY ((realm_id, id))
);

CREATE TABLE IF NOT EXISTS user_consents (
    realm_id text,
    user_id text,
    client_id text,
    created_timestamp timestamp,
    last_updated_timestamp timestamp,
    granted_client_scopes_id set<text>,
    PRIMARY KEY (realm_id, user_id, client_id)
) WITH CLUSTERING ORDER BY (user_id ASC, client_id ASC);
