require "/FLASH/SmartWiFiSocket_CommonUtils"

local doubleTimerRunning = false
local withinTimer1 = false
local remaining_timer1_timer2_duration_secs = 0
local double_timer_timer1_duration_secs = 0
local double_timer_timer2_duration_secs = 0
local double_timer_timer1_state = false
local current_timer1_timer2_count = 0
local total_timer1_timer2_count = 0
local tmrDoubleTimer = tmr.create()
local calc_double_timer_interval_and_start_timer = function()
    tmrDoubleTimer:interval(remaining_timer1_timer2_duration_secs <= 6870 and (remaining_timer1_timer2_duration_secs * 1000) or 6870000)
    tmrDoubleTimer:start()
end
tmrDoubleTimer:register(500, tmr.ALARM_AUTO, function(timer)
        
        remaining_timer1_timer2_duration_secs = remaining_timer1_timer2_duration_secs - 6870
        
        -- checking for less than or equal to 0 because if remaining_timer1_timer2_duration_secs is less than 6870 then subtracting 6870 will give a -ve num
        if remaining_timer1_timer2_duration_secs <= 0 then
            withinTimer1 = not withinTimer1
            
            if withinTimer1 then
                (double_timer_timer1_state and set_gpio_relay_pin or reset_gpio_relay_pin)(true);
                
                remaining_timer1_timer2_duration_secs = double_timer_timer1_duration_secs
            else
                (double_timer_timer1_state and reset_gpio_relay_pin or set_gpio_relay_pin)(true);
                
                remaining_timer1_timer2_duration_secs = double_timer_timer2_duration_secs
            end
            
            if total_timer1_timer2_count > 0 then
                current_timer1_timer2_count = current_timer1_timer2_count + 1
                if current_timer1_timer2_count >= total_timer1_timer2_count then
                    tmrDoubleTimer:stop()
                    doubleTimerRunning = false;
                    
                    --update relay indicator LED colors
                    (gpio.read(RELAY_OPIN) == 1 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
                else
                    calc_double_timer_interval_and_start_timer()
                end
            else
                calc_double_timer_interval_and_start_timer()
            end
        else
            calc_double_timer_interval_and_start_timer()
        end
    end)

--set_ap_ip_config()
    while not wifi.ap.setip({
            ip = "192.168.9.1",
            netmask = "255.255.255.0",
            gateway = "192.168.9.1"
        }) do
    end
    
--set_ap_dhcp_config_and_start_dhcp()
    wifi.ap.dhcp.config ({ start = "192.168.9.2" })
    while not wifi.ap.dhcp.start() do
    end

local stop_double_timer_and_set_same_state = function()
    if doubleTimerRunning then
        tmrDoubleTimer:stop()
        doubleTimerRunning = false;
        
        --update relay indicator LED colors
        (gpio.read(RELAY_OPIN) == 1 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
    else
        (gpio.read(RELAY_OPIN) == 0 and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
    end
    
    return "success"
end

tmr.create():alarm(100, tmr.ALARM_AUTO, function(t)
    if got_physical_switch_press then
        stop_double_timer_and_set_same_state()
        got_physical_switch_press = false
    elseif adc.read(0) >= 650 then
        --print(adc.read(0))
        got_physical_switch_press =  true
    end
end)

--    To test: http://192.168.9.1:9911
--    Endpoint 1  to set gpio relay pin: /gpio/relay/1      (should work irrespective of sntp done or not)
--    Endpoint 2  to reset gpio relay pin: /gpio/relay/0    (should work irrespective of sntp done or not)
--    Endpoint 3  to get gpio relay pin status: /gpio/relay_status (should work irrespective of sntp done or not)

--    local EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode => hardcoding this variable value to save flash program space

collectgarbage()

local srv = net.createServer(net.TCP)   --make http server

if srv then
    srv:listen(9911, function(conn)
            conn:on("receive", function(client_conn, payload)
                    --print(payload)
            
                    local method, path, vars = payload:match("([A-Z]+) (.+)?(.+) HTTP")
                    if (method == nil)
                    then
                        method, path = payload:match("([A-Z]+) (.+) HTTP")
                    end

                    client_conn:send("HTTP/1.1 200 OK\nContent-Type: text/plain\n\n")
                    if method == 'GET' then
                        if path == '/gpio/relay_status' then
                            local socketSoftwareVersion = node.info()
                            if doubleTimerRunning then
                                if withinTimer1 then
                                    client_conn:send((gpio.read(RELAY_OPIN) == 1 and '4' or '5') .. '~' .. double_timer_timer1_duration_secs .. '~' .. double_timer_timer2_duration_secs .. '~' .. (double_timer_timer1_state and '1' or '0') .. '~' .. current_timer1_timer2_count .. '~' .. total_timer1_timer2_count .. '~' .. socketSoftwareVersion)
                                else
                                    client_conn:send((gpio.read(RELAY_OPIN) == 1 and '6' or '7') .. '~' .. double_timer_timer1_duration_secs .. '~' .. double_timer_timer2_duration_secs .. '~' .. (double_timer_timer1_state and '1' or '0') .. '~' .. current_timer1_timer2_count .. '~' .. total_timer1_timer2_count .. '~' ..  socketSoftwareVersion)
                                end
                            else
                                client_conn:send(gpio.read(RELAY_OPIN) .. '~' .. socketSoftwareVersion)
                            end
                        elseif path == '/gpio/relay/1' then
                            client_conn:send(doubleTimerRunning and "error" or (set_gpio_relay_pin(false) and "success" or "error"))
                        elseif path == '/gpio/relay/0' then
                            client_conn:send(doubleTimerRunning and "error" or (reset_gpio_relay_pin(false) and "success" or "error"))
                        elseif path == '/gpio/relay/double_timer' then
                            if doubleTimerRunning then
                                client_conn:send("error")
                            else
                                double_timer_timer1_duration_secs = vars:match('double_timer_timer1_duration_secs=([^&]+)')
                                double_timer_timer1_state = vars:match('double_timer_timer1_state=([^&]+)') == '1'
                                double_timer_timer2_duration_secs = vars:match('double_timer_timer2_duration_secs=([^&]+)')
                                total_timer1_timer2_count = vars:match('total_timer1_timer2_count=([^&]+)')
                                
                                double_timer_timer1_duration_secs = tonumber(double_timer_timer1_duration_secs)
                                double_timer_timer2_duration_secs = tonumber(double_timer_timer2_duration_secs)
                                total_timer1_timer2_count = tonumber(total_timer1_timer2_count)
                                
                                remaining_timer1_timer2_duration_secs = double_timer_timer1_duration_secs;
                                
                                (double_timer_timer1_state and set_gpio_relay_pin or reset_gpio_relay_pin)(true);
                                
                                calc_double_timer_interval_and_start_timer()
                                
                                doubleTimerRunning = true
                                withinTimer1 = true
                                current_timer1_timer2_count = 0
                                
                                client_conn:send("success")
                            end
                        elseif path == '/gpio/relay/stop_double_timer' then
                            if doubleTimerRunning then
								client_conn:send(stop_double_timer_and_set_same_state())
							else
								client_conn:send("error")
							end
                        elseif path == '/get_fsinfo' then
                            local remaining, used, total = file.fsinfo()
                            client_conn:send(remaining ..'~' .. used .. '~' .. total)
                        elseif path == '/factory_reset_redo_initial_setup' then
                            file.open('do_factory_reset', 'w+'):close()
                            if file.exists('do_factory_reset') then 
                                client_conn:send("success")
                                tmr.create():alarm(3200, tmr.ALARM_SINGLE, node.restart)
                            else
                                client_conn:send("error")
                            end
                        elseif path == '/read_file_line_by_line' then
                            -- reading it line by line because if we read all at once then too much RAM is used
                            -- no need of checking filenames as the underlying firmware wont allow opening code files anyway
                            local send_str = nil
                            if global_file_handle then
                                send_str = global_file_handle:readline() or "empty"
                            else
                                global_file_handle = file.open(vars:match('fname=([^&]+)'), 'r')
                                if global_file_handle then
                                    send_str = global_file_handle:readline() or "empty"
                                else
                                    send_str = "error"
                                end
                            end
                            client_conn:send(send_str)
                            if not send_str or send_str == 'empty' or send_str == 'error' then
                                global_file_handle = nil
                            end
                        end
                    end
                end)
            conn:on("sent", function(client_conn)
                    client_conn:close()
                    collectgarbage()
                end)
        end)
end

--Not required as only the only external event which will occur will be factory reset which will anyways do node.restart
--function stop_portable_mode_web_server()
--    if srv then
--        srv:close()
--        srv=nil
--        collectgarbage()
--    end
--end