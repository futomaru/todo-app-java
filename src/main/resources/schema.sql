CREATE TABLE IF NOT EXISTS todos (
    id BIGINT AUTO_INCREMENT PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description VARCHAR(1000),
    completed BOOLEAN NOT NULL DEFAULT FALSE,
    -- タイムスタンプ設定は service 層で行うため、ここではデフォルト値をセットしない
    created_at TIMESTAMP NOT NULL, 
    updated_at TIMESTAMP NOT NULL
);