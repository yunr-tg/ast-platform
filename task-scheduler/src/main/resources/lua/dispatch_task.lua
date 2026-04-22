-- KEYS[1]: active_keys_list (dispatch:active:keys)
-- KEYS[2]: active_keys_set (dispatch:active:keys:set)
-- ARGV[1]: ready_prefix (e.g., "dispatch:ready:")
-- ARGV[2]: processing_prefix (e.g., "dispatch:processing:")
-- ARGV[3]: current_time (score for processing ZSet)

local active_key_list = KEYS[1]
local active_key_set = KEYS[2]
local ready_prefix = ARGV[1]
local processing_prefix = ARGV[2]
local current_time = ARGV[3]

local size = redis.call('LLEN', active_key_list)
if size == 0 then
    return nil
end

for i = 1, size do
    -- Rotate active keys
    local active_key = redis.call('RPOPLPUSH', active_key_list, active_key_list)
    if not active_key then
        break
    end

    local ready_queue = ready_prefix .. active_key
    local processing_queue = processing_prefix .. active_key

    -- Pop from ready list
    local task = redis.call('RPOP', ready_queue)
    
    if task then
        -- Add to processing ZSet with current time as score
        redis.call('ZADD', processing_queue, current_time, task)
        -- Track this as an active processing queue
        redis.call('SADD', 'dispatch:processing:active:keys', active_key)
        return {active_key, task}
    else
        -- Queue is empty
        redis.call('LREM', active_key_list, 0, active_key)
        redis.call('SREM', active_key_set, active_key)
    end
end

return nil
