CREATE DATABASE IF NOT EXISTS testdata_agent
  CHARACTER SET utf8mb4
  COLLATE utf8mb4_0900_ai_ci;

USE testdata_agent;

CREATE TABLE IF NOT EXISTS project (
  id VARCHAR(36) NOT NULL,
  name VARCHAR(120) NOT NULL,
  description VARCHAR(1000) NULL,
  status VARCHAR(24) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_project_status (status),
  INDEX idx_project_created_at (created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS api_definition (
  id VARCHAR(36) NOT NULL,
  project_id VARCHAR(36) NOT NULL,
  name VARCHAR(160) NOT NULL,
  path VARCHAR(500) NOT NULL,
  method VARCHAR(16) NOT NULL,
  summary VARCHAR(500) NULL,
  base_url VARCHAR(500) NULL,
  parameters_json TEXT NULL,
  request_schema TEXT NULL,
  response_schema TEXT NULL,
  auth_config TEXT NULL,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_api_definition_project
    FOREIGN KEY (project_id) REFERENCES project (id),
  UNIQUE KEY uk_api_project_method_path (project_id, method, path),
  INDEX idx_api_definition_project (project_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_case (
  id VARCHAR(36) NOT NULL,
  project_id VARCHAR(36) NOT NULL,
  api_definition_id VARCHAR(36) NOT NULL,
  name VARCHAR(180) NOT NULL,
  scenario_type VARCHAR(24) NOT NULL,
  request_data TEXT NOT NULL,
  assertions TEXT NOT NULL,
  priority VARCHAR(8) NOT NULL,
  source VARCHAR(24) NOT NULL,
  review_status VARCHAR(24) NOT NULL,
  review_record_id VARCHAR(36) NULL,
  version INT NOT NULL DEFAULT 1,
  enabled BOOLEAN NOT NULL DEFAULT TRUE,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_test_case_project
    FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_test_case_api
    FOREIGN KEY (api_definition_id) REFERENCES api_definition (id),
  INDEX idx_test_case_project (project_id),
  INDEX idx_test_case_api (api_definition_id),
  INDEX idx_test_case_review_status (review_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_run (
  id VARCHAR(36) NOT NULL,
  project_id VARCHAR(36) NOT NULL,
  run_name VARCHAR(180) NOT NULL,
  trigger_type VARCHAR(24) NOT NULL,
  status VARCHAR(24) NOT NULL,
  total_count INT NOT NULL,
  processed_count INT NOT NULL DEFAULT 0,
  pass_count INT NOT NULL DEFAULT 0,
  fail_count INT NOT NULL DEFAULT 0,
  error_count INT NOT NULL DEFAULT 0,
  skipped_count INT NOT NULL DEFAULT 0,
  started_at DATETIME(6) NOT NULL,
  finished_at DATETIME(6) NULL,
  parent_run_id VARCHAR(36) NULL,
  request_key VARCHAR(80) NULL,
  request_fingerprint VARCHAR(64) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_test_run_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_test_run_parent FOREIGN KEY (parent_run_id) REFERENCES test_run (id),
  UNIQUE KEY uk_test_run_request_key (request_key),
  INDEX idx_test_run_project_started (project_id, started_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_result (
  id VARCHAR(36) NOT NULL,
  run_id VARCHAR(36) NOT NULL,
  test_case_id VARCHAR(36) NOT NULL,
  case_name VARCHAR(180) NOT NULL,
  case_snapshot TEXT NOT NULL,
  request_summary TEXT NULL,
  response_summary TEXT NULL,
  status_code INT NULL,
  duration_ms BIGINT NULL,
  result_status VARCHAR(24) NOT NULL,
  error_message TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_test_result_run FOREIGN KEY (run_id) REFERENCES test_run (id),
  CONSTRAINT fk_test_result_case FOREIGN KEY (test_case_id) REFERENCES test_case (id),
  INDEX idx_test_result_run (run_id),
  INDEX idx_test_result_case_status (test_case_id, result_status)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS assertion_result (
  id VARCHAR(36) NOT NULL,
  test_result_id VARCHAR(36) NOT NULL,
  assertion_type VARCHAR(40) NOT NULL,
  expected_value TEXT NULL,
  actual_value TEXT NULL,
  passed BOOLEAN NOT NULL,
  message VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_assertion_result_test_result
    FOREIGN KEY (test_result_id) REFERENCES test_result (id),
  INDEX idx_assertion_result_test_result (test_result_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_execution_approval (
  id VARCHAR(36) NOT NULL,
  agent_task_id VARCHAR(36) NOT NULL,
  project_id VARCHAR(36) NOT NULL,
  case_ids_json TEXT NOT NULL,
  case_versions_json TEXT NULL,
  review_ids_json TEXT NULL,
  run_name VARCHAR(180) NOT NULL,
  request_key VARCHAR(80) NOT NULL,
  approved_by VARCHAR(80) NOT NULL,
  status VARCHAR(24) NOT NULL,
  run_id VARCHAR(36) NULL,
  created_at DATETIME(6) NOT NULL,
  expires_at DATETIME(6) NOT NULL,
  used_at DATETIME(6) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_agent_approval_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_agent_approval_run FOREIGN KEY (run_id) REFERENCES test_run (id),
  UNIQUE KEY uk_agent_approval_request_key (request_key),
  INDEX idx_agent_approval_project_created (project_id, created_at),
  INDEX idx_agent_approval_task (agent_task_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS test_case_review (
  id VARCHAR(36) NOT NULL,
  test_case_id VARCHAR(36) NOT NULL,
  case_version INT NOT NULL,
  reviewer VARCHAR(80) NOT NULL,
  reviewed_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  INDEX idx_case_review_version (test_case_id, case_version)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS agent_tool_audit (
  id VARCHAR(36) NOT NULL,
  agent_task_id VARCHAR(36) NOT NULL,
  tool_name VARCHAR(80) NOT NULL,
  project_id VARCHAR(36) NULL,
  run_id VARCHAR(36) NULL,
  approval_id VARCHAR(36) NULL,
  input_summary TEXT NOT NULL,
  started_at DATETIME(6) NOT NULL,
  finished_at DATETIME(6) NULL,
  duration_ms BIGINT NULL,
  status VARCHAR(24) NOT NULL,
  result_summary TEXT NULL,
  error_type VARCHAR(120) NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_agent_audit_project FOREIGN KEY (project_id) REFERENCES project (id),
  CONSTRAINT fk_agent_audit_run FOREIGN KEY (run_id) REFERENCES test_run (id),
  CONSTRAINT fk_agent_audit_approval FOREIGN KEY (approval_id) REFERENCES agent_execution_approval (id),
  INDEX idx_agent_audit_project_started (project_id, started_at),
  INDEX idx_agent_audit_task (agent_task_id),
  INDEX idx_agent_audit_approval (approval_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_quality_task (
  id VARCHAR(36) NOT NULL,
  project_id VARCHAR(36) NOT NULL,
  file_name VARCHAR(255) NOT NULL,
  stored_file_name VARCHAR(100) NOT NULL,
  file_type VARCHAR(12) NOT NULL,
  file_size BIGINT NOT NULL,
  sheet_name VARCHAR(160) NULL,
  row_count INT NOT NULL,
  column_count INT NOT NULL,
  empty_cell_count BIGINT NOT NULL,
  status VARCHAR(24) NOT NULL,
  headers_json TEXT NOT NULL,
  preview_json TEXT NOT NULL,
  summary_json TEXT NULL,
  created_at DATETIME(6) NOT NULL,
  updated_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_data_quality_task_project FOREIGN KEY (project_id) REFERENCES project (id),
  INDEX idx_data_quality_task_project_created (project_id, created_at)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

CREATE TABLE IF NOT EXISTS data_quality_issue (
  id VARCHAR(36) NOT NULL,
  task_id VARCHAR(36) NOT NULL,
  row_number INT NOT NULL,
  column_name VARCHAR(160) NOT NULL,
  rule_code VARCHAR(32) NOT NULL,
  original_value TEXT NULL,
  severity VARCHAR(16) NOT NULL,
  description VARCHAR(1000) NOT NULL,
  created_at DATETIME(6) NOT NULL,
  PRIMARY KEY (id),
  CONSTRAINT fk_data_quality_issue_task FOREIGN KEY (task_id) REFERENCES data_quality_task (id),
  INDEX idx_data_quality_issue_task_row (task_id, row_number),
  INDEX idx_data_quality_issue_rule (task_id, rule_code)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

