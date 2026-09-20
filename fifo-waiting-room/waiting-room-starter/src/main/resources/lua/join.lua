-- Idempotent FIFO join.
-- KEYS[1]=queue ZSET, KEYS[2]=seq, KEYS[3]=visitor HASH, KEYS[4]=meta HASH, KEYS[5]=events SET
-- ARGV[1]=visitorId, ARGV[2]=joinedAtEpochMs, ARGV[3]=defaultMaxQueue, ARGV[4]=defaultOpen (1/0), ARGV[5]=eventId
-- Returns: {ok, position_or_reason, status, ticketId?, joinedAt?}
-- ok=1 success, ok=0 reject (reason in [2]: FULL|CLOSED)

local rank = redis.call('ZRANK', KEYS[1], ARGV[1])
if rank then
  local joinedAt = redis.call('HGET', KEYS[3], 'joinedAt')
  return {1, rank + 1, 'WAITING', '', joinedAt or ARGV[2]}
end

local status = redis.call('HGET', KEYS[3], 'status')
if status == 'ADMITTED' then
  local ticket = redis.call('HGET', KEYS[3], 'ticketId') or ''
  local joinedAt = redis.call('HGET', KEYS[3], 'joinedAt') or ARGV[2]
  return {1, 0, 'ADMITTED', ticket, joinedAt}
end

local open = redis.call('HGET', KEYS[4], 'open')
if (not open) then
  open = ARGV[4]
end
if open == '0' then
  return {0, 'CLOSED', '', '', ''}
end

local maxQueue = redis.call('HGET', KEYS[4], 'maxQueue')
if (not maxQueue) then
  maxQueue = ARGV[3]
end
maxQueue = tonumber(maxQueue)

local card = redis.call('ZCARD', KEYS[1])
if card >= maxQueue then
  return {0, 'FULL', '', '', ''}
end

local seq = redis.call('INCR', KEYS[2])
redis.call('ZADD', KEYS[1], seq, ARGV[1])
redis.call('HSET', KEYS[3], 'status', 'WAITING', 'joinedAt', ARGV[2])
redis.call('SADD', KEYS[5], ARGV[5])
local pos = redis.call('ZRANK', KEYS[1], ARGV[1]) + 1
return {1, pos, 'WAITING', '', ARGV[2]}
