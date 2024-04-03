ALTER TABLE attributes_to_user_sessions WITH gc_grace_seconds = 4320;
ALTER TABLE authentication_sessions WITH gc_grace_seconds = 4320;
ALTER TABLE login_failures WITH gc_grace_seconds = 4320;
ALTER TABLE root_authentication_sessions WITH gc_grace_seconds = 4320;
ALTER TABLE single_use_objects WITH gc_grace_seconds = 4320;
ALTER TABLE user_sessions WITH gc_grace_seconds = 4320;
ALTER TABLE user_sessions_to_attributes WITH gc_grace_seconds = 4320;
