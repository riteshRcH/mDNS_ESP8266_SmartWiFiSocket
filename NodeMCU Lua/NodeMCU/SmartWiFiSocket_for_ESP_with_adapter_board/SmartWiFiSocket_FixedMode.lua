collectgarbage()
require "SmartWiFiSocket_CommonUtils"

local EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode

local srv=nil

local is_any_timer_running = false
local running_timer_type = nil
local running_timer_end_time = 0
local running_timer_cron_mask_config_string = nil

local sntp_snyc_retry_cnt = 0

--local scheduled_all_cron_entries = false

-- Action/State Indicator Files
--local INITIAL_SETUP_DONE = "initial_setup_done"   --not used in this file
--local IS_INTERNET_MODE_CONFIGURED = "is_internet_mode_configured"     --Used only in this file so made it local to function

-- On Boot Action/State Indicator Files
local SNTP_SYNC_DONE_ONCE_ON_BOOT = "sntp_sync_done_once_on_boot"

-- Config Persistence (Used only once in this file hence moved it into function)
--local MDNS_HOSTNAME = "mdns_hostname"

-- Schedule/Timers Entry List
local ONE_SHOT_FUTURE_TIMERS = "OSFT"
local FUTURE_SCHEDULES = "FS"
local RECURRING_SCHEDULES = "RS"
local RECURRING_TIMERS = "RT"
    
local fd = file.open("initial_setup_done", 'r')
local initial_setup_done_contents = fd:read()
fd:close()
fd = nil
local _, _, _, timezone_offset_or_zone_filename_indicator, timezone_offset_or_zone_filename = string.find(initial_setup_done_contents, '^(.)(.)(.+)$')
collectgarbage()

local toggleTriColorRed = false
local tmrBlinkTriColorRed = tmr.create()
tmrBlinkTriColorRed:register(500, tmr.ALARM_AUTO, function(timer)
        toggleTriColorRed = not toggleTriColorRed
        --gpio.write(0, toggleTriColorRed and gpio.HIGH or gpio.LOW)
        gpio_write_setup_led_pins(toggleTriColorRed, false, false)
    end)

function indicate_trying_connect_to_external_socket_host_wifi_start_timer()
    gpio_write_setup_led_pins(false, false, false)
    
    tmrBlinkTriColorRed:start()
end

--Since it is only 1 line, making the function inline
--function indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
--    gpio_write_setup_led_pins(true, false, false)
--end

--Since it is only 1 line, making the function inline
--function indicate_fixed_socket_mode()
--    gpio_write_setup_led_pins(false, true, false)
--end

--Moved it here from CommonUtils because it was used only here itself
function create_action_result_indicator_file (filename, truncate_contents)  -- max filename length 31 chars for ESP's file system
    local fd = file.open(filename, truncate_contents and "w+" or "a+")                 -- a+ mode creates file if doesnt exist also does NOT truncate file if exists
    fd:close()
    fd = nil
    
    collectgarbage()
end

function urldecode(str)
    str = string.gsub(str, "+", " ")
    str = string.gsub(str, "%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end)
    return string.gsub(str, "\r\n", "\n")
end

function append_line_to_file(line, filename, require_newline_char_after_line)
    
    local write_success_indicator = false
    local fd = file.open(filename, "a")
    if fd then
        write_success_indicator =  fd:write(line .. (require_newline_char_after_line and "\n" or ""))
        fd:close()
    end
    fd = nil
    
    return write_success_indicator
end

function remove_saved_schedule_timer_entry_from_respective_file(cron_mask_config_string, filename)
    
    local fd = file.open(filename, "r")
    local file_contents = fd:read()
    file_contents, occurrences_replaced = file_contents:gsub(cron_mask_config_string .. "\n", "")
    fd:close()
    
    local write_success_indicator = false
    fd = file.open(filename, "w+")
    if fd then
        write_success_indicator =  fd:write(file_contents)
        fd:close()
    end
    fd = nil
    
    return write_success_indicator and occurrences_replaced > 0
end

create_action_result_indicator_file(ONE_SHOT_FUTURE_TIMERS, true)

create_action_result_indicator_file(FUTURE_SCHEDULES, true)

create_action_result_indicator_file(RECURRING_SCHEDULES, true)

create_action_result_indicator_file(RECURRING_TIMERS, true)

function trim(s)
  return s:match("^%s*(.-)%s*$")
end

function compare_cron_mask_against_rtc_for_FS_OSFT(min, hour, day, month, in_year)
    tbl = rtctime.epoch2cal(rtctime.get())  --dont put local tbl here, tbl gets nullified (dont know why)
    return tonumber(tbl['year']) == in_year and tonumber(tbl['mon']) == month and tonumber(tbl['day']) == day and tonumber(tbl['hour']) <= hour and tonumber(tbl['min']) < min
end

function compare_recurrence_range_against_rtc_for_RS_RT(recurrence_start, recurrence_end, cron_mask_recurring_days)
    --FOR CRON => 0=SUN, 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT
    --FROM RTC => 1=SUN, 2=MON, 3=TUE, 4=WED, 5=THU, 6=FRI, 7=SAT
    today=tostring(tonumber(rtctime.epoch2cal(rtctime.get())['wday'])-1)
    if recurrence_start == 0 or recurrence_end == 0 then
        return cron_mask_recurring_days == '*' or string.find(cron_mask_recurring_days, today) ~= nil   -- Today's day (0,1,2 etc) must be present in the cron mask
    else
        current_rtctime = rtctime.get()
        if current_rtctime == nil then
            return false
        else
            current_rtctime = tonumber(current_rtctime)
            return current_rtctime > recurrence_start and current_rtctime < recurrence_end and (cron_mask_recurring_days == '*' or string.find(cron_mask_recurring_days, today) ~= nil)
        end
    end
end

function cron_schedule_timer(cron_mask, before_state, timer_duration_secs, osft_or_rt, cron_mask_config_string) -- param osft_or_rt is OSFT or RT string value
    do
        before_state_in_use = before_state
        timer_duration_secs_in_use = timer_duration_secs
        osft_or_rt_in_use = osft_or_rt
        cron_mask_config_string_in_use = cron_mask_config_string
        
        function schedule_callback(e)
            if before_state_in_use == '1' then
                set_gpio_relay_pin(true)
            else
                reset_gpio_relay_pin(true)
            end
    
            is_any_timer_running = true
            running_timer_type = osft_or_rt_in_use
            running_timer_cron_mask_config_string = cron_mask_config_string
            current_rtctime = rtctime.get()
                                                
            if current_rtctime ~= nil then
                running_timer_end_time = current_rtctime+timer_duration_secs
            
                tmr.create():alarm(timer_duration_secs*1000, tmr.ALARM_SINGLE, function(t)
                    if before_state_in_use == '1' then  -- after_state un-necessary as it will be opposite of before_state
                        reset_gpio_relay_pin(false)
                    else
                        set_gpio_relay_pin(false)
                    end
                    
                    is_any_timer_running = false
                end)
            end
        end
    end
    cron.schedule(cron_mask, schedule_callback)
end

function read_and_schedule_all_timer_schedule_entries_for_today()
    cron.reset()
    
    create_action_result_indicator_file('todays_schedule_timers', true)

    -- Scheduling all One Shot Future Timers
    fd = file.open(ONE_SHOT_FUTURE_TIMERS, 'r')
    while true do
        cron_mask_config_string = fd:readline()
        if cron_mask_config_string ~= nil then
            cron_mask_config_string = trim(cron_mask_config_string)
            --after_state un-necessary as it will be opposite of before_state
            local _, _, cron_mask, in_year, before_state, timer_duration_secs, _ = string.find(cron_mask_config_string, '([^=]+)=([^=]+)=(.)=([^=]+)=(.)')
            local _, _, min, hour, day, month = string.find(cron_mask, '([^_]+)_([^_]+)_([^_]+)_([^_]+)_*')
            if compare_cron_mask_against_rtc_for_FS_OSFT(tonumber(min), tonumber(hour), tonumber(day), tonumber(month), tonumber(in_year)) then
                cron_mask = cron_mask:gsub('_', ' ')
                if append_line_to_file("OSFT=" .. cron_mask_config_string, ONE_SHOT_FUTURE_TIMERS, true) then
                    cron_schedule_timer(cron_mask, before_state, tonumber(timer_duration_secs), "osft", cron_mask_config_string)
                end
            end
        else
            break
        end
    end
    fd:close()
    --collectgarbage()
    
    -- Scheduling all Future Schedules
    fd = file.open(FUTURE_SCHEDULES, 'r')
    while true do
        cron_mask_config_string = fd:readline()
        if cron_mask_config_string ~= nil then
            cron_mask_config_string = trim(cron_mask_config_string)
            local _, _, cron_mask, in_year, desired_state = string.find(cron_mask_config_string, '([^=]+)=([^=]+)=(.)')
            local _, _, min, hour, day, month = string.find(cron_mask, '([^_]+)_([^_]+)_([^_]+)_([^_]+)_*')
            if compare_cron_mask_against_rtc_for_FS_OSFT(tonumber(min), tonumber(hour), tonumber(day), tonumber(month), tonumber(in_year)) then
                cron_mask = cron_mask:gsub('_', ' ')
                if append_line_to_file("FS=" .. cron_mask_config_string, FUTURE_SCHEDULES, true) then
                    if desired_state == '1' then
                        cron.schedule(cron_mask, function(e) set_gpio_relay_pin(false) end)
                    else
                        cron.schedule(cron_mask, function(e) reset_gpio_relay_pin(false) end)
                    end
                end
            end
        else
            break
        end
    end
    fd:close()
    --collectgarbage()
    
    -- Scheduling all Recurring Schedules
    fd = file.open(RECURRING_SCHEDULES, 'r')
    while true do
        cron_mask_config_string = fd:readline()
        if cron_mask_config_string ~= nil then
            cron_mask_config_string = trim(cron_mask_config_string)
            local _, _, cron_mask, recurrence_start, recurrence_end, desired_state = string.find(cron_mask_config_string, '([^=]+)=([^=]+)=([^=]+)=(.)')
            _, _, cron_mask_recurring_days = string.find(cron_mask, '[^_]+_[^_]+_[^_]+_[^_]+_([^_]+)')
            if compare_recurrence_range_against_rtc_for_RS_RT(tonumber(recurrence_start), tonumber(recurrence_end), cron_mask_recurring_days) then
                cron_mask = cron_mask:gsub('_', ' ')
                if append_line_to_file("RS=" .. cron_mask_config_string, RECURRING_SCHEDULES, true) then
                    if desired_state == '1' then
                        cron.schedule(cron_mask, function(e) set_gpio_relay_pin(false) end)
                    else
                        cron.schedule(cron_mask, function(e) reset_gpio_relay_pin(false) end)
                    end
                end
            end
        else
            break
        end
    end
    fd:close()
    --collectgarbage()
    
    -- Scheduling all Recurring Timers
    fd = file.open(RECURRING_TIMERS, 'r')
    while true do
        cron_mask_config_string = fd:readline()
        if cron_mask_config_string ~= nil then
            cron_mask_config_string = trim(cron_mask_config_string)
            --after_state un-necessary as it will be opposite of before_state
            local _, _, cron_mask, recurrence_start, recurrence_end, before_state, timer_duration_secs, _ = string.find(cron_mask_config_string, '([^=]+)=([^=]+)=([^=]+)=(.)=([^=]+)=(.)')
            _, _, cron_mask_recurring_days = string.find(cron_mask, '[^_]+_[^_]+_[^_]+_[^_]+_([^_]+)')
            if compare_recurrence_range_against_rtc_for_RS_RT(tonumber(recurrence_start), tonumber(recurrence_end), cron_mask_recurring_days) then
                cron_mask = cron_mask:gsub('_', ' ')
                if append_line_to_file("RT=" .. cron_mask_config_string, RECURRING_TIMERS, true) then
                    cron_schedule_timer(cron_mask, before_state, tonumber(timer_duration_secs), "rt", cron_mask_config_string)
                end
            end
        else
            break
        end
    end
    fd:close()
    
    cron.schedule('02 0 * * *', function(e)
            if get_gpio_relay_pin_status() == 1 then
                create_action_result_indicator_file('restore_relay_on', true)
            end
            node.restart()
        end)   -- restart Socket everyday at 12:02 am
    
    --scheduled_all_cron_entries = true
    create_action_result_indicator_file('scheduled_cron_entries_on_boot', true)
    
    fd = nil
    collectgarbage()
end

function is_internet_mode_configured()
--    local IS_INTERNET_MODE_CONFIGURED = "is_internet_mode_configured"
    if file.exists("is_internet_mode_configured") then
        local fd = file.open("is_internet_mode_configured", "r")
        local is_internet_mode_configured_contents = fd:read()
        fd:close()
        fd = nil
        
        return ('1' .. is_internet_mode_configured_contents)
    else
        return '0'
    end
end

function get_timezone_details()
    if rtctime.get() == nil or rtctime.get() == 0 then
        return "NA"
    else
        --fh for file handle
        local fh = file.open('initial_setup_done', 'r')
        local initial_setup_done_contents = fh:read()
        fh:close()
        fh = nil
        
        return string.sub(initial_setup_done_contents, 2)
    end
end

function start_fixed_mode_web_server()
--    To test: http://<resolved IP address from mdns hostname>:9911
--    Endpoint 1  to set gpio relay pin: /gpio/relay/1      (should work irrespective of sntp done or not)
--    Endpoint 2  to reset gpio relay pin: /gpio/relay/0    (should work irrespective of sntp done or not)
--    Endpoint 3  to get gpio relay pin status: /gpio/relay_status (should work irrespective of sntp done or not)
    
    srv=nil
    collectgarbage()
    
    srv=net.createServer(net.TCP)   --make http server
    
    if srv then
        srv:listen(EXECUTION_MODE_WEB_SERVER_PORT, function(conn)
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
                                client_conn:send((is_any_timer_running and (get_gpio_relay_pin_status() and "2" or "3") or get_gpio_relay_pin_status()) .. (is_internet_mode_configured() .. "~" .. wifi.sta.getconfig(true)['ssid'] .. '~' .. get_timezone_details()) .. (is_any_timer_running and ('~' .. running_timer_type .. ";" .. running_timer_cron_mask_config_string .. ";" .. (running_timer_end_time-rtctime.get())) or ""))
                            elseif path == '/gpio/relay/1' then
                                client_conn:send(set_gpio_relay_pin(false) and "success" or "error")
                            elseif path == '/gpio/relay/0' then
                                client_conn:send(reset_gpio_relay_pin(false) and "success" or "error")
                            elseif path == '/set_new_osct' then
                                if file.exists(SNTP_SYNC_DONE_ONCE_ON_BOOT) and sntp_snyc_retry_cnt <= 16 then
                                    if is_any_timer_running then
                                        client_conn:send("error_another_timer_running")
                                    else
                                        local _, _, cron_mask_config_string = string.find(vars, 'cron_mask_config_string=([^&]+)')
                                        cron_mask_config_string = urldecode(cron_mask_config_string)
                                        --after_state un-necessary as it will be opposite of before_state
                                        local _, _, before_state, timer_duration_secs, _ = string.find(cron_mask_config_string, '(.)=([^=]+)=(.)')
                                        timer_duration_secs = tonumber(timer_duration_secs)
                                        
                                        --local before_state_setting = false    --reducing vars for mem usage
                                        if before_state == '1' then
                                            set_gpio_relay_pin(true)
                                        else
                                            reset_gpio_relay_pin(true)
                                        end
                                        
                                        --if before_state_setting then
                                            is_any_timer_running = true
                                            running_timer_type = "osct"
                                            running_timer_cron_mask_config_string = cron_mask_config_string
                                            current_rtctime = rtctime.get()
                                            
                                            if current_rtctime == nil then
                                                client_conn:send("error_no_rtc")
                                            else
                                                running_timer_end_time = current_rtctime+timer_duration_secs
                                            
                                                if before_state == '1' then
                                                    tmr.create():alarm(timer_duration_secs*1000, tmr.ALARM_SINGLE, function(t)
                                                            reset_gpio_relay_pin(false)
                                                            is_any_timer_running = false
                                                        end)
                                                else
                                                    tmr.create():alarm(timer_duration_secs*1000, tmr.ALARM_SINGLE, function(t)
                                                            set_gpio_relay_pin(false)
                                                            is_any_timer_running = false
                                                        end)
                                                end
                                            
                                                client_conn:send("success")
                                            end
                                        --else
                                            --client_conn:send("error")
                                        --end
                                    end
                                else
                                    client_conn:send("error_no_rtc")
                                end
                            elseif path == '/save_new_schedule_timer_entry' then   -- for OSFT, FS, RS, RT
                                if file.exists(SNTP_SYNC_DONE_ONCE_ON_BOOT) and sntp_snyc_retry_cnt <= 16 then
                                    if is_any_timer_running then
                                        client_conn:send("error_another_timer_running")
                                    else
                                        local _, _, schedule_timer_filename = string.find(vars, 'schedule_timer_filename=([^&]+)')
                                        local _, _, cron_mask_config_string = string.find(vars, 'cron_mask_config_string=([^&]+)')
                                        local _, _, is_entry_for_today = string.find(vars, 'is_entry_for_today=([^&]+)')
                                        cron_mask_config_string = urldecode(cron_mask_config_string)
                                        
                                        if append_line_to_file(cron_mask_config_string, schedule_timer_filename, true) then
                                            client_conn:send("success")
                                            if is_entry_for_today == '1' then
                                                --read_and_schedule_all_timer_schedule_entries_for_today()  -- already freed error along with reboot
                                                tmr.create():alarm(2000, tmr.ALARM_SINGLE, node.restart)
                                            end
                                        else
                                            client_conn:send("error")
                                        end
                                    end
                                else
                                    client_conn:send("error_no_rtc")
                                end
                            elseif path == '/get_schedule_timers' then
                                local _, _, schedule_timer_filename = string.find(vars, 'schedule_timer_filename=([^&]+)')
                                local fd = file.open(schedule_timer_filename, 'r')
                                local file_contents = fd:read()
                                client_conn:send(file_contents == nil and "empty" or file_contents)
                                fd:close()
                                fd = nil
                            elseif path == '/remove_saved_schedule_timer' then
                                local _, _, schedule_timer_filename = string.find(vars, 'schedule_timer_filename=([^&]+)')
                                local _, _, cron_mask_config_string = string.find(vars, 'cron_mask_config_string=([^&]+)')
                                cron_mask_config_string = urldecode(cron_mask_config_string)
                                cron_mask_config_string = cron_mask_config_string:gsub("[%%%]%^%-$().[*+?]", "%%%1")    --escape regex meta-characters (escaped using %), reference: http://gammon.com.au/scripts/doc.php?lua=string.gsub
                                
                                client_conn:send(remove_saved_schedule_timer_entry_from_respective_file(cron_mask_config_string, schedule_timer_filename) and "success" or "error")
                            elseif path == '/internet_mode_config' then
                                local _, _, internet_mode_configured_by = string.find(vars, 'internet_mode_configured_by=([^&]+)')
                                internet_mode_configured_by = urldecode(internet_mode_configured_by)
                                client_conn:send(append_line_to_file(internet_mode_configured_by, "is_internet_mode_configured", false) and "success" or "error")
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
        
        --indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
            tmrBlinkTriColorRed:stop()
    
            gpio_write_setup_led_pins(false, false, false)
        --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
            gpio_write_setup_led_pins(true, false, false)
        
        local fd = file.open("mdns_hostname", 'r')
        mdns.register(fd:read(), { service="http", port = EXECUTION_MODE_WEB_SERVER_PORT })
        fd:close()
        fd = nil
        
        start_fixed_mode_web_server()
        
        if file.exists(SNTP_SYNC_DONE_ONCE_ON_BOOT) then
            if sntp_snyc_retry_cnt <= 16 then
                --indicate_fixed_socket_mode()
                    gpio_write_setup_led_pins(false, true, false)
            else
                --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
                    gpio_write_setup_led_pins(true, false, false)
                cron.reset()
                file.remove(SNTP_SYNC_DONE_ONCE_ON_BOOT)
            end
        else
            sntp_sync_time()
        end
    end)

wifi.eventmon.register(wifi.eventmon.STA_DISCONNECTED, function(tbl)
        stop_fixed_mode_web_server()
        mdns.close()
        
        indicate_trying_connect_to_external_socket_host_wifi_start_timer()
        wifi.sta.connect()
    end)

function sntp_sync_time()
--    sntp.sync({ "1.in.pool.ntp.org", "0.asia.pool.ntp.org", "2.asia.pool.ntp.org", "ntp1.iitd.ac.in", "ntp2.iitd.ac.in" }, function(sec, usec, server, info)
--            rtctime.set(sec + 19800)
--        end,
--        sntp_sync_time, 1)
    -- 19800 secs = 5.5 hours IST
    sntp.sync(nil, function(sec)
            if timezone_offset_or_zone_filename_indicator == '0' then
                rtctime.set(sec + tonumber(timezone_offset_or_zone_filename))     -- indicator is 0 i.e the value is an offset hence convert to number
            end
            
            create_action_result_indicator_file(SNTP_SYNC_DONE_ONCE_ON_BOOT, true)
            
            if sntp_snyc_retry_cnt <= 16 then
            --indicate_fixed_socket_mode()
                gpio_write_setup_led_pins(false, true, false)
            end
            
            if file.exists('scheduled_cron_entries_on_boot') then
                if get_gpio_relay_pin_status() == 0 and not is_any_timer_running and sntp_snyc_retry_cnt > 16 then
                    node.restart()
                end
            else
                read_and_schedule_all_timer_schedule_entries_for_today()
            end
            
            sntp_snyc_retry_cnt = 0
        end, function()
                sntp_snyc_retry_cnt = sntp_snyc_retry_cnt + 1
            
                -- SNTP SYNC was done once on boot and after that if couldn't sntp sync till 16 times (4 hours tried syncing every 15 mins) then show red led on
                if file.exists(SNTP_SYNC_DONE_ONCE_ON_BOOT) and sntp_snyc_retry_cnt <= 16 then
                    --indicate_fixed_socket_mode()
                        gpio_write_setup_led_pins(false, true, false)
                else
                    --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
                        gpio_write_setup_led_pins(true, false, false)
                    cron.reset()
                    file.remove(SNTP_SYNC_DONE_ONCE_ON_BOOT)
                end
            end, 1)
end

indicate_trying_connect_to_external_socket_host_wifi_start_timer()
wifi.sta.connect()

--function post_state_to_internet ()
    
--end

--function get_action_from_internet ()
    
--end