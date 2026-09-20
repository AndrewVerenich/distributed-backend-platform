-- Mark visitor admitted and bind opaque ticket id.
-- KEYS[1]=visitor HASH
-- ARGV[1]=ticketId
-- Returns: joinedAt epoch ms (or '')

local joinedAt = redis.call('HGET', KEYS[1], 'joinedAt') or ''
redis.call('HSET', KEYS[1], 'status', 'ADMITTED', 'ticketId', ARGV[1])
return joinedAt
