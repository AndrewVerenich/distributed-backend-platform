-- Sliding window log. One ZSET member per allowed request, score = redis time.
-- KEYS[1] = zset key
-- ARGV[1] = limit, ARGV[2] = windowMs, ARGV[3] = unique member
-- Returns {allowed, limit, remaining, retryAfterMs, resetAfterMs}

local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local member = ARGV[3]
local t = redis.call('TIME')
local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local windowStart = nowMs - windowMs

redis.call('ZREMRANGEBYSCORE', KEYS[1], '-inf', windowStart)
local count = redis.call('ZCARD', KEYS[1])
local oldest = redis.call('ZRANGE', KEYS[1], 0, 0, 'WITHSCORES')

local resetAfter = windowMs
if oldest[2] ~= nil then
  resetAfter = tonumber(oldest[2]) + windowMs - nowMs
  if resetAfter < 0 then
    resetAfter = 0
  end
end

if count < limit then
  redis.call('ZADD', KEYS[1], nowMs, member)
  redis.call('PEXPIRE', KEYS[1], windowMs)
  local remaining = limit - count - 1
  if remaining < 0 then
    remaining = 0
  end
  return {1, limit, remaining, 0, resetAfter}
end

redis.call('PEXPIRE', KEYS[1], windowMs)
return {0, limit, 0, resetAfter, resetAfter}
