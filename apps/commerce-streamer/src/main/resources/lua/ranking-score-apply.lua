local rankingKey = KEYS[1]
local expireAtEpochSecond = tonumber(ARGV[1])
local handledKeyTtlSeconds = tonumber(ARGV[2])

local aggregatedScores = {}
local appliedEventCount = 0

for argumentIndex = 3, #ARGV do
    local event = cjson.decode(ARGV[argumentIndex])
    local handledKey = "ranking:handled:" .. event.eventId
    local isFirstSeen = redis.call("SET", handledKey, "1", "NX", "EX", handledKeyTtlSeconds)

    if isFirstSeen then
        appliedEventCount = appliedEventCount + 1
        for _, productScore in ipairs(event.productScores) do
            local productIdKey = productScore.productId
            aggregatedScores[productIdKey] = (aggregatedScores[productIdKey] or 0) + productScore.score
        end
    end
end

local hasAppliedScores = false
for productIdKey, score in pairs(aggregatedScores) do
    redis.call("ZINCRBY", rankingKey, score, productIdKey)
    hasAppliedScores = true
end

if hasAppliedScores and redis.call("TTL", rankingKey) < 0 then
    redis.call("EXPIREAT", rankingKey, expireAtEpochSecond)
end

return appliedEventCount
