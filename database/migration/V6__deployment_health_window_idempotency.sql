ALTER TABLE tpip_deployment_health_evaluation
    ADD CONSTRAINT uk_health_evaluation_window
        UNIQUE (deployment_id, window_start, window_end);
