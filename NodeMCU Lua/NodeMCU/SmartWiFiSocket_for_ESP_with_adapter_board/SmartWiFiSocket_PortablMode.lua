require "SmartWiFiSocket_CommonUtils"

--Not required as setup already done and the only prerequisite for Potrable Mode is WiFi in AP mode which will anyways change, no need to show
--indicate_trying_to_setup_portable_socket_start_timer()
--    local toggleTriColorBlue = false
--    local tmrBlinkTriColorBlue = tmr.create()
--    tmrBlinkTriColorBlue:register(500, tmr.ALARM_AUTO, function(timer)
--            toggleTriColorBlue = not toggleTriColorBlue
--            --gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, toggleTriColorBlue and gpio.HIGH or gpio.LOW)
--            gpio.write(2, toggleTriColorBlue and gpio.HIGH or gpio.LOW)
--        end)
--    gpio.write(0, gpio.LOW)
--    gpio.write(1, gpio.LOW)
--    gpio.write(2, gpio.LOW)
    
--    tmrBlinkTriColorBlue:start()

--indicate_trying_to_setup_portable_socket_stop_timer()
--    tmrBlinkTriColorBlue:stop()
    
--    gpio.write(0, gpio.LOW)
--    gpio.write(1, gpio.LOW)
--    gpio.write(2, gpio.LOW)
    
local toggleTimerRunning = false
local toggleRelayPin = false
local tmrToggleRelayPin = tmr.create()
local current_toggle_count = 0
local total_toggle_count = 0
local toggle_timer_duration_secs = 0
tmrToggleRelayPin:register(500, tmr.ALARM_AUTO, function(timer)
        toggleRelayPin = not toggleRelayPin
        if toggleRelayPin then
            set_gpio_relay_pin(false, true)
        else
            reset_gpio_relay_pin(false, true)
        end
        if total_toggle_count > 0 then
            current_toggle_count = current_toggle_count + 1
            if current_toggle_count >= total_toggle_count then
                tmrToggleRelayPin:stop()
                toggleTimerRunning = false
            end
        end
    end)

local srv=nil

function start_portable_mode_web_server()
--    To test: http://192.168.10.1:9911
--    Endpoint 1  to set gpio relay pin: /gpio/relay/1      (should work irrespective of sntp done or not)
--    Endpoint 2  to reset gpio relay pin: /gpio/relay/0    (should work irrespective of sntp done or not)
--    Endpoint 3  to get gpio relay pin status: /gpio/relay_status (should work irrespective of sntp done or not)

--    local EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode => hardcoding this variable value to save flash program space
    
    srv = nil
    
    collectgarbage()
    
    srv=net.createServer(net.TCP)   --make http server
    
    if srv then
        srv:listen(9911, function(conn)
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
                                client_conn:send(toggleTimerRunning and ((get_gpio_relay_pin_status() == 1 and '4' or '5') .. '~' .. current_toggle_count .. '~' .. total_toggle_count .. '~' .. toggle_timer_duration_secs) or get_gpio_relay_pin_status())
                            elseif path == '/gpio/relay/1' then
                                client_conn:send(set_gpio_relay_pin(false) and "success" or "error")
                            elseif path == '/gpio/relay/0' then
                                client_conn:send(reset_gpio_relay_pin(false) and "success" or "error")
                            elseif path == '/gpio/relay/toggle' then
                                local _, _, timer_duration_secs = string.find(vars, 'timer_duration_secs=([^&]+)')
                                local _, _, toggle_timer_initial_state = string.find(vars, 'toggle_timer_initial_state=([^&]+)')
                                _, _, total_toggle_count = string.find(vars, 'total_toggle_count=([^&]+)')
                                
                                total_toggle_count = tonumber(total_toggle_count)
                                timer_duration_secs = tonumber(timer_duration_secs)
                                toggle_timer_duration_secs = timer_duration_secs
                                toggle_timer_initial_state = (toggle_timer_initial_state == '1')
                                
                                tmrToggleRelayPin:interval(timer_duration_secs * 1000)
                                toggleRelayPin = toggle_timer_initial_state
                                
                                if toggleRelayPin then
                                    --using is_for_timer_start_event for toggle_timer i.e same color indication as in FixedMode (ON=Blue) so that user has to remember less colors
                                    set_gpio_relay_pin(true)
                                else
                                    --using is_for_timer_start_event for toggle_timer i.e same color indication as in FixedMode (OFF=Red) so that user has to remember less colors
                                    reset_gpio_relay_pin(true)
                                end
                                
                                tmrToggleRelayPin:start()
                                
                                toggleTimerRunning = true
                                current_toggle_count = 0
                                
                                client_conn:send("success")
                            elseif path == '/gpio/relay/stop_toggle' then
                                tmrToggleRelayPin:stop()
                                toggleTimerRunning = false
                                
                                client_conn:send("success")
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

--Not required as only the only external event which will occur will be factory reset which will anyways do node.restart
--function stop_portable_mode_web_server()
--    if srv ~= nil then
--        srv:close()
--        srv=nil
--        collectgarbage()
--    end
--end

--set_ap_ip_config()
    while not wifi.ap.setip({
            ip = "192.168.10.1",
            netmask = "255.255.255.0",
            gateway = "192.168.10.1"
        }) do
    end
    
--set_ap_dhcp_config_and_start_dhcp()
    wifi.ap.dhcp.config ({ start = "192.168.10.2" })
    while not wifi.ap.dhcp.start() do
    end

start_portable_mode_web_server()