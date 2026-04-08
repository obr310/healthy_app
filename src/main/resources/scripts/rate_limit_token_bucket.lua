-- Token Bucket Rate Limiter
-- KEYS[1] = rate limit key
-- ARGV[1] = capacity (max tokens)
-- ARGV[2] = refill rate (tokens per second)
-- ARGV[3] = now timestamp (milliseconds)
-- ARGV[4] = requested tokens (default 1)
-- Returns: 1 = allowed, 0 = rejected

local key = KEYS[1]
local capacity = tonumber(ARGV[1])
local refillRate = tonumber(ARGV[2])
local now = tonumber(ARGV[3])
local requested = tonumber(ARGV[4])

local bucket = redis.call('HMGET', key, 'tokens', 'lastRefill')
local tokens = tonumber(bucket[1])
local lastRefill = tonumber(bucket[2])

-- Initialize bucket if it doesn't exist
if tokens == nil then
    tokens = capacity
    lastRefill = now
end

-- Calculate tokens to add based on elapsed time
local elapsed = (now - lastRefill) / 1000.0
local tokensToAdd = elapsed * refillRate
tokens = math.min(capacity, tokens + tokensToAdd)
lastRefill = now

-- Check if enough tokens available
if tokens >= requested then
    tokens = tokens - requested
    redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill)
    redis.call('EXPIRE', key, math.ceil(capacity / refillRate) + 1)
    return 1
else
    redis.call('HMSET', key, 'tokens', tokens, 'lastRefill', lastRefill)
    redis.call('EXPIRE', key, math.ceil(capacity / refillRate) + 1)
    return 0
end
