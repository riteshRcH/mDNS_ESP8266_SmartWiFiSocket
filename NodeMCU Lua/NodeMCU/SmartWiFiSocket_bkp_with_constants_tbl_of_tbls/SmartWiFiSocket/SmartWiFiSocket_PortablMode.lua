require "SmartWiFiSocket_CommonUtils"

indicate_trying_to_setup_portable_socket_start_timer()

init_wifi_portable_mode()

indicate_trying_to_setup_portable_socket_stop_timer()

inidicate_portable_socket_mode()

local CONSTANTS = require "SmartWiFiSocket_Constants"

function start_portable_mode_web_server()
--    To test: http://192.168.10.1:9911
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

--Not required as only the only external event which will occur will be factory reset which will anyways do node.reboot
--function stop_portable_mode_web_server()
--    if srv ~= nil then
--        srv:close()
--        srv=nil
--        collectgarbage()
--    end
--end

set_ap_ip_config()
    
set_ap_dhcp_config_and_start_dhcp()

start_portable_mode_web_server()