CREATE TABLE IF NOT EXISTS clients (
    id SERIAL PRIMARY KEY,
    username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(100),
    is_confirmed BOOLEAN DEFAULT FALSE,
    confirmation_token VARCHAR(255),
    password_reset_token VARCHAR(255),
    password_reset_expires TIMESTAMP WITH TIME ZONE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS rooms (
    id SERIAL PRIMARY KEY,
    number VARCHAR(20) NOT NULL UNIQUE,
    capacity INT NOT NULL,
    bed_count INT NOT NULL DEFAULT 1,
    price NUMERIC(10,2) NOT NULL,
    is_reserved BOOLEAN DEFAULT FALSE,
    has_fridge BOOLEAN DEFAULT FALSE,
    has_kitchenette BOOLEAN DEFAULT FALSE,
    has_balcony BOOLEAN DEFAULT FALSE,
    has_tv BOOLEAN DEFAULT FALSE,
    has_table BOOLEAN DEFAULT FALSE
    );

CREATE TABLE IF NOT EXISTS room_images (
    id SERIAL PRIMARY KEY,
    room_id INT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    file_name VARCHAR(255) NOT NULL,
    image_data BYTEA NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS reservations (
                                            id SERIAL PRIMARY KEY,
                                            room_id INT NOT NULL REFERENCES rooms(id) ON DELETE CASCADE,
    client_id INT REFERENCES clients(id) ON DELETE SET NULL,
    guest_name VARCHAR(100) NOT NULL,
    status VARCHAR(20) NOT NULL,
    check_in DATE,
    check_out DATE,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );

CREATE TABLE IF NOT EXISTS admins (
                                      id SERIAL PRIMARY KEY,
                                      username VARCHAR(50) NOT NULL UNIQUE,
    password VARCHAR(255) NOT NULL,
    created_at TIMESTAMP WITH TIME ZONE DEFAULT NOW()
    );

INSERT INTO admins (username, password)
VALUES ('admin', 'admin123')
    ON CONFLICT (username) DO NOTHING;