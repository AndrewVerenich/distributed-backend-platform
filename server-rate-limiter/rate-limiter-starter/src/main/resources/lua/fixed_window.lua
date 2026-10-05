-- Fixed window counter. Atomic across app instances.
-- KEYS[1] = base key (window id is appended)
-- ARGV[1] = limit, ARGV[2] = windowMs
-- Returns {allowed, limit, remaining, retryAfterMs, resetAfterMs}

local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local t = redis.call('TIME')
local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local windowId = math.floor(nowMs / windowMs)
local key = KEYS[1] .. ':' .. windowId

local count = redis.call('INCR', key)
local ttl = redis.call('PTTL', key)
if ttl < 0 then
  ttl = windowMs - (nowMs % windowMs)
  if ttl < 1 then
    ttl = windowMs
  end
  redis.call('PEXPIRE', key, ttl)
end

local remaining = limit - count
if remaining < 0 then
  remaining = 0
end

local allowed = 0
local retryAfter = 0
if count <= limit then
  allowed = 1
else
  retryAfter = ttl
end

return {allowed, limit, remaining, retryAfter, ttl}
