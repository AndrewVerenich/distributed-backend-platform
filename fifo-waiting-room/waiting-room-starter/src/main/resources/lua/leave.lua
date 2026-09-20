-- Leave queue.
-- KEYS[1]=queue ZSET, KEYS[2]=visitor HASH
-- ARGV[1]=visitorId
-- Returns: 1 if removed from zset, 0 otherwise

local removed = redis.call('ZREM', KEYS[1], ARGV[1])
redis.call('HSET', KEYS[2], 'status', 'LEFT')
return removed
