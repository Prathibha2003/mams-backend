-- MAMS schema (MySQL 8.0.16+). Safe to run on every startup.

CREATE TABLE IF NOT EXISTS bases (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL UNIQUE,
    location  VARCHAR(150)
);

CREATE TABLE IF NOT EXISTS equipment_types (
    id        BIGINT AUTO_INCREMENT PRIMARY KEY,
    name      VARCHAR(100) NOT NULL UNIQUE,
    category  VARCHAR(20) NOT NULL,
    CONSTRAINT chk_equipment_category
        CHECK (category IN ('VEHICLE', 'WEAPON', 'AMMUNITION', 'OTHER'))
);

CREATE TABLE IF NOT EXISTS users (
    id             BIGINT AUTO_INCREMENT PRIMARY KEY,
    username       VARCHAR(50)  NOT NULL UNIQUE,
    password_hash  VARCHAR(100) NOT NULL,
    full_name      VARCHAR(100) NOT NULL,
    role           VARCHAR(20)  NOT NULL,
    base_id        BIGINT NULL,
    created_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_users_base FOREIGN KEY (base_id) REFERENCES bases(id),
    CONSTRAINT chk_users_role
        CHECK (role IN ('ADMIN', 'BASE_COMMANDER', 'LOGISTICS_OFFICER')),
    CONSTRAINT chk_users_base_required
        CHECK (role = 'ADMIN' OR base_id IS NOT NULL)
);

-- Starting stock per base and equipment type (used for opening balances)
CREATE TABLE IF NOT EXISTS initial_balances (
    base_id            BIGINT NOT NULL,
    equipment_type_id  BIGINT NOT NULL,
    quantity           INT NOT NULL DEFAULT 0,
    PRIMARY KEY (base_id, equipment_type_id),
    CONSTRAINT fk_ib_base FOREIGN KEY (base_id) REFERENCES bases(id),
    CONSTRAINT fk_ib_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id),
    CONSTRAINT chk_ib_qty CHECK (quantity >= 0)
);

CREATE TABLE IF NOT EXISTS purchases (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_id            BIGINT NOT NULL,
    equipment_type_id  BIGINT NOT NULL,
    quantity           INT NOT NULL,
    unit_cost          DECIMAL(12,2),
    supplier           VARCHAR(150),
    purchase_date      DATE NOT NULL,
    created_by         BIGINT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_purchases_base FOREIGN KEY (base_id) REFERENCES bases(id),
    CONSTRAINT fk_purchases_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id),
    CONSTRAINT fk_purchases_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_purchases_qty CHECK (quantity > 0),
    INDEX idx_purchases_filter (base_id, equipment_type_id, purchase_date)
);

CREATE TABLE IF NOT EXISTS transfers (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    from_base_id       BIGINT NOT NULL,
    to_base_id         BIGINT NOT NULL,
    equipment_type_id  BIGINT NOT NULL,
    quantity           INT NOT NULL,
    notes              VARCHAR(255),
    transferred_at     TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    created_by         BIGINT NOT NULL,
    CONSTRAINT fk_transfers_from FOREIGN KEY (from_base_id) REFERENCES bases(id),
    CONSTRAINT fk_transfers_to FOREIGN KEY (to_base_id) REFERENCES bases(id),
    CONSTRAINT fk_transfers_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id),
    CONSTRAINT fk_transfers_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_transfers_qty CHECK (quantity > 0),
    CONSTRAINT chk_transfers_bases CHECK (from_base_id <> to_base_id),
    INDEX idx_transfers_from (from_base_id, equipment_type_id, transferred_at),
    INDEX idx_transfers_to (to_base_id, equipment_type_id, transferred_at)
);

CREATE TABLE IF NOT EXISTS assignments (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_id            BIGINT NOT NULL,
    equipment_type_id  BIGINT NOT NULL,
    quantity           INT NOT NULL,
    assigned_to        VARCHAR(100) NOT NULL,
    assigned_date      DATE NOT NULL,
    notes              VARCHAR(255),
    created_by         BIGINT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_assign_base FOREIGN KEY (base_id) REFERENCES bases(id),
    CONSTRAINT fk_assign_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id),
    CONSTRAINT fk_assign_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_assign_qty CHECK (quantity > 0),
    INDEX idx_assign_filter (base_id, equipment_type_id, assigned_date)
);

CREATE TABLE IF NOT EXISTS expenditures (
    id                 BIGINT AUTO_INCREMENT PRIMARY KEY,
    base_id            BIGINT NOT NULL,
    equipment_type_id  BIGINT NOT NULL,
    quantity           INT NOT NULL,
    reason             VARCHAR(255),
    expended_date      DATE NOT NULL,
    created_by         BIGINT NOT NULL,
    created_at         TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    CONSTRAINT fk_exp_base FOREIGN KEY (base_id) REFERENCES bases(id),
    CONSTRAINT fk_exp_type FOREIGN KEY (equipment_type_id) REFERENCES equipment_types(id),
    CONSTRAINT fk_exp_user FOREIGN KEY (created_by) REFERENCES users(id),
    CONSTRAINT chk_exp_qty CHECK (quantity > 0),
    INDEX idx_exp_filter (base_id, equipment_type_id, expended_date)
);

-- Audit trail for every API transaction (no foreign keys, so logs are never blocked)
CREATE TABLE IF NOT EXISTS audit_logs (
    id            BIGINT AUTO_INCREMENT PRIMARY KEY,
    user_id       BIGINT NULL,
    username      VARCHAR(50),
    action        VARCHAR(50) NOT NULL,
    entity_type   VARCHAR(50),
    entity_id     BIGINT NULL,
    http_method   VARCHAR(10),
    endpoint      VARCHAR(255),
    details       TEXT,
    status_code   INT,
    ip_address    VARCHAR(45),
    created_at    TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
    INDEX idx_audit_user (user_id),
    INDEX idx_audit_time (created_at)
);

-- Starter data (INSERT IGNORE skips rows that already exist)
INSERT IGNORE INTO bases (name, location) VALUES
    ('Alpha Base', 'North Sector'),
    ('Bravo Base', 'East Sector'),
    ('Charlie Base', 'South Sector');

INSERT IGNORE INTO equipment_types (name, category) VALUES
    ('Armoured Vehicle', 'VEHICLE'),
    ('Transport Truck', 'VEHICLE'),
    ('Assault Rifle', 'WEAPON'),
    ('Sniper Rifle', 'WEAPON'),
    ('5.56mm Ammunition', 'AMMUNITION'),
    ('7.62mm Ammunition', 'AMMUNITION');