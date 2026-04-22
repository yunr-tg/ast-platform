local taskId = redis.call('RPOP', KEYS[1])
if not taskId then
  return nil
end
redis.call('ZADD', KEYS[2], ARGV[1], taskId)
return taskId
