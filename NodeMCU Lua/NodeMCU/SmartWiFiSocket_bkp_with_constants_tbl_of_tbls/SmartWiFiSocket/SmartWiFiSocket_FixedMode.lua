require "SmartWiFiSocket_CommonUtils"

init_wifi_mode(wifi.STATION)
wifi.sta.disconnect()

remove_action_result_indicator_files_on_boot()

local CONSTANTS = require "SmartWiFiSocket_Constants"

srv=nil

function start_fixed_mode_web_server()
--    To test: http://<resolved IP address from mdns hostname>:9911
--    Endpoint 1  to set gpio relay pin: /gpio/relay/1      (should work irrespective of sntp done or not)
--    Endpoint 2  to reset gpio relay pin: /gpio/relay/0    (should work irrespective of sntp done or not)
--    Endpoint 3  to get gpio relay pin status: /gpio/relay_status (should work irrespective of sntp done or not)
    
    srv=nil
    collectgarbage()
    
    srv=net.createServer(net.TCP)   --make http server
    
    if srv then
        srv:listen(CONSTANTS.NETWORK_SERVICE_SETTINGS.EXECUTION_MODE_WEB_SERVER_PORT, function(conn)
                conn:on("receive", function(client_conn, payload)
                        --print(payload)
                
                        local _, _, method, path, vars = string.find(payload, "([A-Z]+) (.+)?(.+) HTTP")
                        if (method == nil)
                        then
                            _, _, method, path = string.find(payload, "([A-Z]+) (.+) HTTP")
                        end

                        client_conn:send("HTTP/1.1 200 OK\n")
                        client_conn:send("Content-Type: text/plain\n\n")
                        if method == 'GET' then
                            if path == '/gpio/relay_status' then
                                client_conn:send(get_gpio_relay_pin_status())
                            elseif path == '/gpio/relay/1' then
                                if set_gpio_relay_pin() then
                                    client_conn:send("success")
                                else
                                    client_conn:send("error")
                                end
                            elseif path == '/gpio/relay/0' then
                                if reset_gpio_relay_pin() then
                                    client_conn:send("success")
                                else
                                    client_conn:send("error")
                                end
                            else
                                client_conn:send("error")
                            end
                        end
                    end)
                conn:on("sent", function(client_conn)
                        client_conn:close()
                        collectgarbage()
                    end)
            end)
    end
end

function stop_fixed_mode_web_server()
    if srv ~= nil then
        srv:close()
        srv=nil
        collectgarbage()
    end
end

wifi.eventmon.register(wifi.eventmon.STA_GOT_IP, function(tbl)
        
        indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
        indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
        
        fd = file.open(CONSTANTS.FILENAMES.MDNS_HOSTNAME, 'r')
        mdns.register(fd:read(), { service="http", port = CONSTANTS.NETWORK_SERVICE_SETTINGS.EXECUTION_MODE_WEB_SERVER_PORT })
        fd:close()
        fd = nil
        
        start_fixed_mode_web_server()
        
        if file.exists(CONSTANTS.FILENAMES.SNTP_SYNC_DONE_ONCE_ON_BOOT) then
            indicate_fixed_socket_mode()
        else
            sntp_sync_time()
        end
    end)

wifi.eventmon.register(wifi.eventmon.STA_DISCONNECTED, function(tbl)
        stop_fixed_mode_web_server()
        indicate_trying_connect_to_external_socket_host_wifi_start_timer()
        wifi.sta.connect()
    end)

function sntp_sync_time()
--    sntp.sync({ "1.in.pool.ntp.org", "0.asia.pool.ntp.org", "2.asia.pool.ntp.org", "ntp1.iitd.ac.in", "ntp2.iitd.ac.in" }, function(sec, usec, server, info)
--            rtctime.set(sec + 19800)
--        end,
--        sntp_sync_time, 1)
    -- 19800 secs = 5.5 hours IST
    indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
    sntp.sync(nil, function(sec)
            rtctime.set(sec + 19800)
            
            if not file.exists(CONSTANTS.FILENAMES.SNTP_SYNC_DONE_ONCE_ON_BOOT) then
                create_action_result_indicator_file(CONSTANTS.FILENAMES.SNTP_SYNC_DONE_ONCE_ON_BOOT)
            end
            
            indicate_fixed_socket_mode()
        end, sntp_sync_time, 1)
end

indicate_trying_connect_to_external_socket_host_wifi_start_timer()
wifi.sta.connect()

function post_state_to_internet ()
    
end

function get_action_from_internet ()
    
end