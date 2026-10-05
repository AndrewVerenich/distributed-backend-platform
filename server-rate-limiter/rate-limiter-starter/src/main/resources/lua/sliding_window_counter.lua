-- Weighted sliding window. O(1) memory: previous window count blended into the current one.
-- KEYS[1] = base key
-- ARGV[1] = limit, ARGV[2] = windowMs
-- Returns {allowed, limit, remaining, retryAfterMs, resetAfterMs}

local limit = tonumber(ARGV[1])
local windowMs = tonumber(ARGV[2])
local t = redis.call('TIME')
local nowMs = tonumber(t[1]) * 1000 + math.floor(tonumber(t[2]) / 1000)
local windowId = math.floor(nowMs / windowMs)
local elapsed = nowMs % windowMs
local prevKey = KEYS[1] .. ':' .. (windowId - 1)
local currKey = KEYS[1] .. ':' .. windowId

local prev = tonumber(redis.call('GET', prevKey) or '0')
local curr = tonumber(redis.call('GET', currKey) or '0')
local weight = (windowMs - elapsed) / windowMs
local estimate = prev * weight + curr
local resetAfter = windowMs - elapsed
if resetAfter < 0 then
  resetAfter = 0
end

if estimate < limit then
  local n = redis.call('INCR', currKey)
  if n == 1 then
    redis.call('PEXPIRE', currKey, windowMs * 2)
  end
  local remaining = math.floor(limit - (estimate + 1))
  if remaining < 0 then
    remaining = 0
  end
  return {1, limit, remaining, 0, resetAfter}
end

local retryAfter = resetAfter
if prev > 0 and curr < limit then
  local threshold = windowMs - ((limit - curr) * windowMs / prev)
  local wait = math.ceil(threshold - elapsed)
  if wait < 1 then
    wait = 1
  end
  if wait > resetAfter then
    wait = resetAfter
  end
  retryAfter = wait
end

return {0, limit, 0, retryAfter, resetAfter}
