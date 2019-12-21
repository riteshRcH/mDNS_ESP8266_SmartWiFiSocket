wifi.setmode(wifi.STATION)
wifi.sta.config('SuperBeam', '12591259')
wifi.sta.connect()
gpio.mode(2, gpio.OUTPUT)
gpio.write(2, gpio.LOW)
srv=net.createServer(net.TCP)
srv:listen(9911,function(conn)
  conn:on("receive",function(client_conn, payload)
    print(payload)

    local _, _, method, path, vars = string.find(payload, "([A-Z]+) (.+)?(.+) HTTP")
    if (method == nil)
    then
        _, _, method, path = string.find(payload, "([A-Z]+) (.+) HTTP")
    end

    client_conn:send("HTTP/1.1 200 OK\r\n")
    client_conn:send("Content-Type: text/html\n\n")
    if method == 'GET' then
        if path == '/gpio/2' then
            client_conn:send("<h1> " .. gpio.read(2) .. " </h1>")
        elseif path == '/gpio/2/1' then
            gpio.write(2, gpio.HIGH)
            client_conn:send("<h1> " .. gpio.read(2) .. " </h1>")
        elseif path == '/gpio/2/0' then
            gpio.write(2, gpio.LOW)
            client_conn:send("<h1> " .. gpio.read(2) .. " </h1>")    
        end
    end
  end)
  conn:on("sent",function(client_conn) client_conn:close() end)
end)
