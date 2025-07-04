ALTER TABLE authentication_sessions ADD version bigint;
ALTER TABLE root_authentication_sessions ADD version bigint;

CREATE TABLE IF NOT EXISTS authentication_sessions_lwt (
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
       version bigint,
       PRIMARY KEY (parent_session_id, tab_id)
) WITH CLUSTERING ORDER BY (tab_id ASC);

CREATE TABLE IF NOT EXISTS root_authentication_sessions_lwt (
        id text PRIMARY KEY,
        expiration bigint,
        realm_id text,
        timestamp bigint,
        version bigint
);