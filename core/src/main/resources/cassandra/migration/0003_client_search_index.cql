CREATE TABLE IF NOT EXISTS client_search_index (
     realm_id text,
     name text,
     value text,
     client_id text,
     PRIMARY KEY ((realm_id, name, value), client_id)
) WITH CLUSTERING ORDER BY (client_id ASC);