-- Pop up to N visitors from the head of the FIFO queue.
-- KEYS[1]=queue ZSET
-- ARGV[1]=n
-- Returns: list of visitorIds (may be empty)

local n = tonumber(ARGV[1])
if (not n) or n < 1 then
  return {}
end

local members = redis.call('ZRANGE', KEYS[1], 0, n - 1)
if #members == 0 then
  return {}
end

redis.call('ZREM', KEYS[1], unpack(members))
return members
