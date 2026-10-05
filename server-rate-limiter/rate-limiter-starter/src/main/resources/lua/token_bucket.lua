-- Token bucket. tokens refill continuously, capped by burst.
-- KEYS[1] = hash {tokens, ts}
-- ARGV[1] = capacity, ARGV[2] = refill tokens per millisecond, ARGV[3] = key ttl ms
-- Returns {allowed, limit, remaining, retryAfterMs, resetAfterMs}
-- limit in the reply is the burst capacity (the visible quota).

local capacity = tonumber(ARGV[1])
local refillPerMs = tonumber(ARGV[2])
local idleTtl = tonumber(ARGV[3])
local t = redis.call('TIME')
local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)

local data = redis.call('HMGET', KEYS[1], 'tokens', 'ts')
local tokens = tonumber(data[1])
local ts = tonumber(data[2])
if tokens == nil or ts == nil then
  tokens = capacity
  ts = nowMs
end

local elapsed = nowMs - ts
if elapsed < 0 then
  elapsed = 0
end
if refillPerMs > 0 then
  tokens = math.min(capacity, tokens + elapsed * refillPerMs)
else
  tokens = math.min(capacity, tokens)
end

local allowed = 0
local retryAfter = 0
if tokens >= 1 then
  tokens = tokens - 1
  allowed = 1
else
  local deficit = 1 - tokens
  if refillPerMs > 0 then
    retryAfter = math.ceil(deficit / refillPerMs)
  else
    retryAfter = idleTtl
  end
  if retryAfter < 0 then
    retryAfter = 0
  end
end

redis.call('HSET', KEYS[1], 'tokens', string.format('%.6f', tokens), 'ts', tostring(nowMs))
redis.call('PEXPIRE', KEYS[1], idleTtl)

local remaining = math.floor(tokens)
if remaining < 0 then
  remaining = 0
end

local resetAfter = 0
if refillPerMs > 0 then
  local missing = capacity - tokens
  if missing < 0 then
    missing = 0
  end
  resetAfter = math.ceil(missing / refillPerMs)
end

return {allowed, capacity, remaining, retryAfter, resetAfter}
