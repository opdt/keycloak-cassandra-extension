ALTER TABLE user_sessions ADD version bigint;

CREATE TABLE IF NOT EXISTS user_sessions_lwt (
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
     notes map<text, text>,
     version bigint
);