require 'hocon'
require 'json'
begin
  config = Hocon.load(ARGV[0])
  puts JSON.generate(config)
rescue => e
  STDERR.puts "ERROR: #{e.class}: #{e.message}"
  exit 1
end
