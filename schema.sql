-- datenbank schema fuer mrp
-- ausfuehren vor dem start der app

CREATE TABLE IF NOT EXISTS users (
    id SERIAL PRIMARY KEY,
    username VARCHAR(100) UNIQUE NOT NULL,
    password VARCHAR(255) NOT NULL,
    email VARCHAR(255),
    favorite_genre VARCHAR(100)
);

CREATE TABLE IF NOT EXISTS media (
    id SERIAL PRIMARY KEY,
    title VARCHAR(255) NOT NULL,
    description TEXT,
    media_type VARCHAR(50),
    release_year INT,
    genres TEXT,
    age_restriction INT,
    creator_id INT REFERENCES users(id)
);

CREATE TABLE IF NOT EXISTS ratings (
    id SERIAL PRIMARY KEY,
    media_id INT REFERENCES media(id) ON DELETE CASCADE,
    user_id INT REFERENCES users(id),
    stars INT NOT NULL CHECK(stars BETWEEN 1 AND 5),
    comment TEXT,
    comment_confirmed BOOLEAN DEFAULT FALSE,
    created_at TIMESTAMP DEFAULT NOW(),
    UNIQUE(media_id, user_id)
);

CREATE TABLE IF NOT EXISTS rating_likes (
    rating_id INT REFERENCES ratings(id) ON DELETE CASCADE,
    user_id INT REFERENCES users(id),
    PRIMARY KEY(rating_id, user_id)
);

CREATE TABLE IF NOT EXISTS favorites (
    user_id INT REFERENCES users(id),
    media_id INT REFERENCES media(id) ON DELETE CASCADE,
    PRIMARY KEY(user_id, media_id)
);
