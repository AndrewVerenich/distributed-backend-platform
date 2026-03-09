CREATE TABLE video_view_counts (
    video_id                BIGINT PRIMARY KEY,
    total_views             BIGINT      NOT NULL DEFAULT 0,
    unique_viewers_estimate BIGINT      NOT NULL DEFAULT 0,
    last_updated            TIMESTAMP   NOT NULL
);

CREATE INDEX idx_video_view_counts_last_updated ON video_view_counts (last_updated DESC);
CREATE INDEX idx_video_view_counts_video_id ON video_view_counts (video_id);
