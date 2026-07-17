local todayKey = KEYS[1]
local tomorrowKey = KEYS[2]
local weightRatio = ARGV[1]
local expireAtEpochSecond = ARGV[2]

local CARRIED_OVER = 1
local TARGET_ALREADY_EXISTS = -1
local SOURCE_MISSING = -2

if redis.call("EXISTS", tomorrowKey) == 1 then
    return TARGET_ALREADY_EXISTS
end

if redis.call("EXISTS", todayKey) == 0 then
    return SOURCE_MISSING
end

redis.call("ZUNIONSTORE", tomorrowKey, 1, todayKey, "WEIGHTS", weightRatio)
redis.call("EXPIREAT", tomorrowKey, expireAtEpochSecond)

return CARRIED_OVER
