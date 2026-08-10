ALTER TABLE tpip_binding_version
    ADD COLUMN access_channel_id BIGINT UNSIGNED NULL AFTER endpoint_id,
    ADD KEY idx_binding_ver_channel (access_channel_id),
    ADD CONSTRAINT fk_binding_ver_channel FOREIGN KEY (access_channel_id) REFERENCES tpip_access_channel (id);
