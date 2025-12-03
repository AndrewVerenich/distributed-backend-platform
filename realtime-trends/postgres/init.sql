CREATE TABLE user_preferences (
    id BIGSERIAL PRIMARY KEY,
    user_id BIGINT NOT NULL,
    category_id BIGINT NOT NULL
);
CREATE INDEX idx_user_preferences_user_id
    ON user_preferences(user_id);

INSERT INTO user_preferences (user_id, category_id) VALUES (1, 1);
INSERT INTO user_preferences (user_id, category_id) VALUES (1, 2);
INSERT INTO user_preferences (user_id, category_id) VALUES (1, 3);
INSERT INTO user_preferences (user_id, category_id) VALUES (2, 4);
INSERT INTO user_preferences (user_id, category_id) VALUES (2, 5);
INSERT INTO user_preferences (user_id, category_id) VALUES (3, 6);
INSERT INTO user_preferences (user_id, category_id) VALUES (3, 7);
INSERT INTO user_preferences (user_id, category_id) VALUES (3, 8);
