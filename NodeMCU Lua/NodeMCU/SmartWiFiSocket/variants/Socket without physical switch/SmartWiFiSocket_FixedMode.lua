collectgarbage()
require "/FLASH/SmartWiFiSocket_CommonUtils"

local srv=nil

local running_timer_type = nil
local running_timer_end_time = 0
local running_timer_cron_mask_config_string = nil

local cnter = 0
local cron_scheduled_after_sntp = false
    
local fd = file.open('initial_setup_done', 'r')
local LANSNTPServerIP = nil
local timezone_handling_indicator, tzid, tzoffset = fd:readline():match('^..(.)(.+)=(.*)$')
if timezone_handling_indicator == '1' then
    require '/FLASH/tz32_srcDiet'
end
fd:close()

local internet_mode_configured_by = nil
local internet_mode_domain_name = nil
local external_wifi_ssid = nil

fd = file.open("internet_mode_configured", 'r')
if fd then
    internet_mode_configured_by = fd:readline()
    internet_mode_domain_name = fd:readline()
    fd:close()
end

fd = file.open("mdns_hostname", 'r')
local mdns_hostname = fd:readline()
fd:close()

local socketSoftwareVersion = node.info()

local stop_running_timer_and_set_desired_state = function(desired_state)
    if is_any_timer_running and running_timer_obj_ref then
        if running_timer_obj_type then
            running_timer_obj_ref:unschedule()
        else
            running_timer_obj_ref:stop()
        end
        is_any_timer_running = false
    end;
    (desired_state == '1' and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
    
    return "success"
end

local sync_state_with_cloud_for_internet_mode = function(timer)
    http.get(internet_mode_domain_name .. '/socket/api/v' .. socketSoftwareVersion .. '.0/sockets_per_user_status?email=' .. encoder.toHex(internet_mode_configured_by) .. '&socket_name=' .. mdns_hostname:gsub(' ', '+') .. '&external_wifi_ssid=' .. encoder.toHex(external_wifi_ssid), nil, function(code, data)
            if code == 200 and data then
                local desired_state = data:match('^([0-9])$')
                if desired_state then
                   stop_running_timer_and_set_desired_state(desired_state)
                end
                
                local iv2 = ''
                for i=1, 16, 1 do
                    iv2 = iv2 .. string.char(node.random(65, 90))
                end
                current_rtctime = rtctime.get()
                http.put(internet_mode_domain_name .. '/socket/api/v' .. socketSoftwareVersion .. '.0/sockets_per_user', 'Content-Type: text/plain\r\n', iv2 ..encoder.toHex(crypto.encrypt("AES-CBC", "PwS0cKEtDaTA!92$", internet_mode_configured_by .. '~' .. mdns_hostname .. '~' .. external_wifi_ssid .. '~' .. (is_any_timer_running and (gpio.read(RELAY_OPIN) == 1 and "2" or "3") or gpio.read(RELAY_OPIN)) .. '~' .. (timezone_handling_indicator .. tzid .. '=' .. tzoffset) .. '~' .. current_rtctime .. (is_any_timer_running and ('~' .. running_timer_type .. "~" .. running_timer_cron_mask_config_string .. "~" .. (running_timer_end_time-current_rtctime)) or ""), iv2)), function(http_put_status_code, http_put_response)
                        --if not (http_put_status_code < 0) then print(http_put_response) end
                        cnter = http_put_response == "success" and 0 or 7
                    end)
            end
        end)
end
collectgarbage()

local toggleBiColorSetupRed = false
local tmrBlinkBiColorRed = tmr.create()
tmrBlinkBiColorRed:register(500, tmr.ALARM_AUTO, function(timer)
        toggleBiColorSetupRed = not toggleBiColorSetupRed
        gpio_write_setup_led_pins(toggleBiColorSetupRed, false)
    end)

local cron_fn_set_gpio_relay_pin = function(e) set_gpio_relay_pin(false) end
local cron_fn_reset_gpio_relay_pin = function(e) reset_gpio_relay_pin(false) end

-- not required as we are doing Hex encoding for parameters in Internet Mode
--taken from here https://github.com/stuartpb/tvtropes-lua/blob/master/urlencode.lua
--function url_encode(str)
--    --1st gsub Ensure all newlines are in CRLF form
    
--    --2nd gsub Percent-encode all non-unreserved characters
--    --2nd gsub as per RFC 3986, Section 2.3
--    --2nd gsub (except for space, which gets plus-encoded)

--    --3rd gsub Convert spaces to plus signs
--    return str:gsub("\r?\n", "\r\n"):gsub("([^%w%-%.%_%~ ])", function (c) return string.format ("%%%02X", string.byte(c)) end):gsub(" ", "+")
--end

--used encoder.fromHex() instead to save space on below function
--local url_decode = function(s)
--    return s:gsub("+", " "):gsub("%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end):gsub("\r\n", "\n")
--end

local append_line_to_file = function(line, filename, require_newline_char_after_line)
    
    local write_success_indicator = false
    local fh = file.open(filename, "a+")
    if fh then
        write_success_indicator =  fh:write(line .. (require_newline_char_after_line and "\n" or ''))
        fh:close()
    end
    
    return write_success_indicator
end

local cron_schedule_OSCT_OSFT_RT_end_time = function(timer_duration_secs, before_state)
    
    local running_timer_expire_function = before_state == '1' and function(e)
                                                    is_any_timer_running = false
                                                    reset_gpio_relay_pin(false)
                                                end or function(e)
                                                    is_any_timer_running = false
                                                    set_gpio_relay_pin(false)
                                                end
    
    -- tmr has max interval limit of 6870 seconds, hence if interval > 6870 then we use cron else we use tmr itself
    running_timer_obj_type = timer_duration_secs > 6870     -- false = tmr, true = cron.entry
    if running_timer_obj_type then
        rtc_tbl = rtctime.epoch2cal(running_timer_end_time)
        running_timer_obj_ref = cron.schedule(rtc_tbl['min'] .. ' ' .. rtc_tbl['hour'] .. ' ' .. rtc_tbl['day'] .. ' ' .. rtc_tbl['mon'] .. ' *', running_timer_expire_function)
        rtc_tbl = nil
    else
        running_timer_obj_ref = tmr.create()
        running_timer_obj_ref:alarm(timer_duration_secs*1000, tmr.ALARM_SINGLE, running_timer_expire_function)
    end
    
    running_timer_expire_function = nil     -- reference to function already registered by callbacks hence we can gc this variable, anyways its local so will exists only till callback executes
end

local cron_schedule_timer = function(cron_mask, before_state, timer_duration_secs, osft_or_rt, cron_mask_config_string) -- param osft_or_rt is OSFT or RT string value
    do
        before_state_in_use = before_state
        timer_duration_secs_in_use = timer_duration_secs
        osft_or_rt_in_use = osft_or_rt
        cron_mask_config_string_in_use = cron_mask_config_string
        
        function schedule_callback(e)
            (before_state_in_use == '1' and set_gpio_relay_pin or reset_gpio_relay_pin)(true);
    
            is_any_timer_running = true
            running_timer_type = osft_or_rt_in_use
            running_timer_cron_mask_config_string = cron_mask_config_string_in_use
            current_rtctime = rtctime.get()
                                                
            if current_rtctime > 0 then
                running_timer_end_time = current_rtctime+timer_duration_secs_in_use
                -- after_state un-necessary as it will be opposite of before_state
                cron_schedule_OSCT_OSFT_RT_end_time(timer_duration_secs_in_use, before_state_in_use)
            end
        end
    end
    cron.schedule(cron_mask, schedule_callback)
end

local remove_all_date_files = function(except_date)
    for past_daily_schedule_timer_filename, _ in pairs(file.list())
    do
        if past_daily_schedule_timer_filename:match('^_') and past_daily_schedule_timer_filename ~= except_date then
            file.remove(past_daily_schedule_timer_filename)
        end
    end
end

local identify_and_schedule_todays_schedule_timer_entries = function()
    cron.reset()
    
    if file.list()["OSFT"] > 0 or file.list()["FS"] > 0 or file.list()["RS"] > 0 or file.list()["RT"] > 0 then
        current_rtctime = rtctime.get()
        --FOR CRON => 0=SUN, 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT
        --FROM RTC => 1=SUN, 2=MON, 3=TUE, 4=WED, 5=THU, 6=FRI, 7=SAT
        today=tostring(rtctime.epoch2cal(current_rtctime)['wday']-1)
        tbl = rtctime.epoch2cal(current_rtctime)  --dont put local tbl here, tbl gets nullified (dont know why)
        local todays_schedule_timers = '_' .. tbl['day'] .. tbl['mon'] .. tbl['year']
        
        remove_all_date_files(todays_schedule_timers)
        local was_todays_schedule_timers_present = file.exists(todays_schedule_timers) and file.list()[todays_schedule_timers] > 0
        
        if not was_todays_schedule_timers_present then
            -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
            fd = file.open(todays_schedule_timers, "w+")
            if fd then
                fd:close()
            end
        end

        -- Scheduling all One Shot Future Timers
        fd = file.open("OSFT", 'r')
        while true do
            cron_mask_config_string = fd:readline()
            -- cron_mask_config_string:match('^1') checks if it is enabled
            if cron_mask_config_string and cron_mask_config_string:match('^1') then
                cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") --match() used to trim
                --after_state un-necessary as it will be opposite of before_state
                local cron_mask, in_year, before_state, timer_duration_secs = cron_mask_config_string:match('.([^=]+)=([^=]+)=(.)=([^=]+)=.')
                local min, hour, day, month = cron_mask:match('([^_]+)_([^_]+)_([^_]+)_([^_]+)_*')
                
                if tonumber(tbl['year']) == tonumber(in_year) and tonumber(tbl['mon']) == tonumber(month) and tonumber(tbl['day']) == tonumber(day) and ((tonumber(tbl['hour']) == tonumber(hour) and tonumber(tbl['min']) < tonumber(min)) or tonumber(tbl['hour']) < tonumber(hour)) then
                    if not was_todays_schedule_timers_present then
                       append_line_to_file("1OSFT=" .. cron_mask_config_string, todays_schedule_timers, true)
                    end
                    if not was_todays_schedule_timers_present or check_line_present_in_file("1OSFT=" .. cron_mask_config_string, todays_schedule_timers) then
                        cron_schedule_timer(cron_mask:gsub('_', ' '), before_state, tonumber(timer_duration_secs), "osft", cron_mask_config_string)
                    end
                end
            else
                break
            end
        end
        fd:close()
        --collectgarbage()
        
        -- Scheduling all Future Schedules
        fd = file.open("FS", 'r')
        while true do
            cron_mask_config_string = fd:readline()
            -- cron_mask_config_string:match('^1') checks if it is enabled
            if cron_mask_config_string and cron_mask_config_string:match('^1') then
                cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") --match() used to trim
                local cron_mask, in_year, desired_state = cron_mask_config_string:match('.([^=]+)=([^=]+)=(.)')
                local min, hour, day, month = cron_mask:match('([^_]+)_([^_]+)_([^_]+)_([^_]+)_*')
                
                if tonumber(tbl['year']) == tonumber(in_year) and tonumber(tbl['mon']) == tonumber(month) and tonumber(tbl['day']) == tonumber(day) and ((tonumber(tbl['hour']) == tonumber(hour) and tonumber(tbl['min']) < tonumber(min)) or tonumber(tbl['hour']) < tonumber(hour)) then
                    if not was_todays_schedule_timers_present then
                        append_line_to_file("1FS=" .. cron_mask_config_string, todays_schedule_timers, true)
                    end
                    if not was_todays_schedule_timers_present or check_line_present_in_file("1FS=" .. cron_mask_config_string, todays_schedule_timers) then
                        cron.schedule(cron_mask:gsub('_', ' '), desired_state == '1' and cron_fn_set_gpio_relay_pin or cron_fn_reset_gpio_relay_pin)
                    end
                end
            else
                break
            end
        end
        fd:close()
        --collectgarbage()
        
        -- Scheduling all Recurring Schedules
        fd = file.open("RS", 'r')
        while true do
            cron_mask_config_string = fd:readline()
            -- cron_mask_config_string:match('^1') checks if it is enabled
            if cron_mask_config_string and cron_mask_config_string:match('^1') then
                cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") -- match() used to trim
                local cron_mask, recurrence_start, recurrence_end, desired_state = cron_mask_config_string:match('.([^=]+)=([^=]+)=([^=]+)=(.)')
                cron_mask_recurring_days = cron_mask:match('[^_]+_[^_]+_[^_]+_[^_]+_([^_]+)')
                
                recurrence_start = tonumber(recurrence_start)
                recurrence_end = tonumber(recurrence_end)
                
                -- Today's day (0,1,2 etc) must be present in the cron mask
                if ((recurrence_start == 0 or recurrence_end == 0) or (current_rtctime > recurrence_start and current_rtctime < recurrence_end)) and (cron_mask_recurring_days == '*' or cron_mask_recurring_days:match(today)) then
                    if not was_todays_schedule_timers_present then
                        append_line_to_file("1RS=" .. cron_mask_config_string, todays_schedule_timers, true)
                    end
                    if not was_todays_schedule_timers_present or check_line_present_in_file("1RS=" .. cron_mask_config_string, todays_schedule_timers) then
                        cron.schedule(cron_mask:gsub('_', ' '), desired_state == '1' and cron_fn_set_gpio_relay_pin or cron_fn_reset_gpio_relay_pin)
                    end
                end
            else
                break
            end
        end
        fd:close()
        --collectgarbage()
        
        -- Scheduling all Recurring Timers
        fd = file.open("RT", 'r')
        while true do
            cron_mask_config_string = fd:readline()
            -- cron_mask_config_string:match('^1') checks if it is enabled
            if cron_mask_config_string and cron_mask_config_string:match('^1') then
                cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") --match() used to trim
                --after_state un-necessary as it will be opposite of before_state
                local cron_mask, recurrence_start, recurrence_end, before_state, timer_duration_secs = cron_mask_config_string:match('.([^=]+)=([^=]+)=([^=]+)=(.)=([^=]+)=.')
                cron_mask_recurring_days = cron_mask:match('[^_]+_[^_]+_[^_]+_[^_]+_([^_]+)')
                
                recurrence_start = tonumber(recurrence_start)
                recurrence_end = tonumber(recurrence_end)
                
                -- Today's day (0,1,2 etc) must be present in the cron mask
                if ((recurrence_start == 0 or recurrence_end == 0) or (current_rtctime > recurrence_start and current_rtctime < recurrence_end)) and (cron_mask_recurring_days == '*' or cron_mask_recurring_days:match(today)) then
                    if not was_todays_schedule_timers_present then
                       append_line_to_file("1RT=" .. cron_mask_config_string, todays_schedule_timers, true)
                    end
                    if not was_todays_schedule_timers_present or check_line_present_in_file("1RT=" .. cron_mask_config_string, todays_schedule_timers) then
                        cron_schedule_timer(cron_mask:gsub('_', ' '), before_state, tonumber(timer_duration_secs), "rt", cron_mask_config_string)
                    end
                end
            else
                break
            end
        end
        fd:close()
    end
    
    cron.schedule('02 0 * * *', function(e)            
            -- previous todays_schedule_timer for past dates
            remove_all_date_files(nil)
            
            cron_scheduled_after_sntp = false
            gpio_write_setup_led_pins(not cron_scheduled_after_sntp, cron_scheduled_after_sntp)
        end)   -- restart Socket everyday at 12:02 am
    
    cron_scheduled_after_sntp = true
    
    --indicate_fixed_socket_mode()
    gpio_write_setup_led_pins(not cron_scheduled_after_sntp, cron_scheduled_after_sntp)
    
    fd = nil
    collectgarbage()
end

local start_fixed_mode_web_server = function()
--    To test: http://<resolved IP address from mdns hostname>:9911
--    Endpoint 1  to set gpio relay pin: /gpio/relay/1      (should work irrespective of sntp done or not)
--    Endpoint 2  to reset gpio relay pin: /gpio/relay/0    (should work irrespective of sntp done or not)
--    Endpoint 3  to get gpio relay pin status: /gpio/relay_status (should work irrespective of sntp done or not)
    
    collectgarbage()
    
    srv = net.createServer(net.TCP)   --make http server
    
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
                                current_rtctime = rtctime.get()
                                client_conn:send((is_any_timer_running and (gpio.read(RELAY_OPIN) == 1 and "2" or "3") or gpio.read(RELAY_OPIN)) .. ((internet_mode_configured_by == nil and '0' or ('1' .. internet_mode_configured_by)) .. "~" .. external_wifi_ssid .. '~' .. ((current_rtctime == nil or current_rtctime == 0) and "0NA=" or (timezone_handling_indicator .. tzid .. '=' .. tzoffset))) .. (is_any_timer_running and ('~' .. running_timer_type .. ";" .. running_timer_cron_mask_config_string .. ";" .. (running_timer_end_time-current_rtctime)) or "") .. "~" .. socketSoftwareVersion)
                            elseif path == '/gpio/relay/1' then
                                client_conn:send(is_any_timer_running and "error" or (set_gpio_relay_pin(false) and "success" or "error"))
                            elseif path == '/gpio/relay/0' then
                                client_conn:send(is_any_timer_running and "error" or (reset_gpio_relay_pin(false) and "success" or "error"))
                            elseif path == '/set_new_osct' then
                                current_rtctime = rtctime.get()
                                if current_rtctime > 0 then
                                    if is_any_timer_running then
                                        client_conn:send("error_another_timer_running")
                                    else
                                        local cron_mask_config_string = encoder.fromHex(vars:match('cron_mask_config_string=([^&]+)'))
                                        --after_state un-necessary as it will be opposite of before_state
                                        local before_state, timer_duration_secs = cron_mask_config_string:match('(.)=([^=]+)=.')
                                        timer_duration_secs = tonumber(timer_duration_secs);
                                        
                                        --local before_state_setting = false    --reducing vars for mem usage
                                        (before_state == '1' and set_gpio_relay_pin or reset_gpio_relay_pin)(true);
                                        
                                        --if before_state_setting then
                                            is_any_timer_running = true
                                            running_timer_type = "osct"
                                            running_timer_cron_mask_config_string = cron_mask_config_string
                                            running_timer_end_time = current_rtctime+timer_duration_secs
                                            
                                            -- after_state un-necessary as it will be opposite of before_state
                                            cron_schedule_OSCT_OSFT_RT_end_time(timer_duration_secs, before_state)
                                        
                                            client_conn:send("success")
                                        --else
                                            --client_conn:send("error")
                                        --end
                                    end
                                else
                                    client_conn:send("error_no_rtc")
                                end
                            elseif path == '/read_file_line_by_line' then
                                -- reading it line by line because if we read all at once then too much RAM is used
                                -- no need of checking filenames as the underlying firmware wont allow opening code files anyway
                                --if fh and (schedule_timer_filename == 'OSFT' or schedule_timer_filename == 'FS' or schedule_timer_filename == 'RS' or schedule_timer_filename == 'RT' or schedule_timer_filename:match('^_[0-9]+$')) then
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
                            elseif path == '/save_update_enabled_remove_schedule_timer_or_update_run_skip_for_today' then  -- for today's date
                                local call_type = vars:match('call_type=([^&]+)')
                                current_rtctime = rtctime.get()
                                if current_rtctime > 0 then
                                    if is_any_timer_running and call_type ~= 'removing_past_entries' then
                                        client_conn:send("error_another_timer_running")
                                    else
                                        if gpio.read(RELAY_OPIN) == 0 or call_type == 'removing_past_entries' then
                                            local schedule_timer_filename = vars:match('schedule_timer_filename=([^&]+)')
                                            local cron_mask_config_string = vars:match('cron_mask_config_string=([^&]+)')
                                            local truncate_before_update = vars:match('truncate_before_update=([^&]+)')
                                            local last_run_skip_update_or_todays_entry_saved_updated_removed = vars:match('last_run_skip_update_or_todays_entry_saved_updated_removed=([^&]+)')
                                            local todays_schedule_timers = vars:match('todays_schedule_timers=([^&]+)')
                                                        
                                            -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
                                            if truncate_before_update == '1' then
                                                file.open(schedule_timer_filename,  "w+"):close()
                                            end
                                            
                                            if cron_mask_config_string then
                                                cron_mask_config_string = encoder.fromHex(cron_mask_config_string)
                                            end
                                            client_conn:send((cron_mask_config_string == nil or append_line_to_file(cron_mask_config_string, schedule_timer_filename, true)) and "success" or "error")
                                            
                                            if last_run_skip_update_or_todays_entry_saved_updated_removed == '1' then
                                                if call_type == 'remove_saved_schedule_timer' or call_type == 'save_new_schedule_timer' or call_type == 'update_enabled_schedule_timers' then
                                                    file.remove(todays_schedule_timers)
                                                end
                                                cron_scheduled_after_sntp = false
                                                gpio_write_setup_led_pins(not cron_scheduled_after_sntp, cron_scheduled_after_sntp)
                                                
                                                -- tmr.create():alarm(3200, tmr.ALARM_SINGLE, node.restart)
                                                tmr.create():alarm(5000, tmr.ALARM_SINGLE, identify_and_schedule_todays_schedule_timer_entries)
                                            end
                                        else
                                            client_conn:send("allowed_only_when_appliance_off")
                                        end
                                    end
                                else
                                    client_conn:send("error_no_rtc")
                                end
                            elseif path == '/gpio/relay/stop_running_timer' then
                                client_conn:send(stop_running_timer_and_set_desired_state(vars:match('state_after_stopping_running_timer=([^&]+)')))
                            elseif path == '/internet_mode_config' then
                                internet_mode_configured_by = encoder.fromHex(vars:match('internet_mode_configured_by=([^&]+)'))
                                internet_mode_domain_name = encoder.fromHex(vars:match('internet_mode_domain_name=([^&]+)'))
                                
                                client_conn:send(append_line_to_file(internet_mode_configured_by .. '\n' .. internet_mode_domain_name, "internet_mode_configured", false) and "success" or "error")
                            elseif path == '/remove_internet_mode_config' then
                                file.remove("internet_mode_configured")
                                internet_mode_configured_by = nil
                                internet_mode_domain_name = nil
                                client_conn:send("success")
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
                            elseif path == '/set_LANSNTPServerIP' then
                                LANSNTPServerIP = vars:match('LANSNTPServerIP=([^&]+)')
                                client_conn:send("success")
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

local internet_disconnected_handler = function()
    if LANSNTPServerIP and wifi.sta.getip() then
        LANSNTPServerIP = nil
    else
        cnter = cnter >= 41 and (cnter + 1) or 41
        if cnter >= 44 then
            cron.reset()
            cnter = 41
            
            cron_scheduled_after_sntp = false
            gpio_write_setup_led_pins(not cron_scheduled_after_sntp, cron_scheduled_after_sntp)
        end
    end
end

wifi.eventmon.register(wifi.eventmon.STA_GOT_IP, function(tbl_ip_params)
        
        --indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
            tmrBlinkBiColorRed:stop()
        --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
            gpio_write_setup_led_pins(not cron_scheduled_after_sntp, cron_scheduled_after_sntp)
            
        external_wifi_ssid = wifi.sta.getconfig(true)['ssid']
        
        mdns.register(mdns_hostname, { service="http", port = 9911 })
        
        start_fixed_mode_web_server()
        
        --    sntp.sync({ "1.in.pool.ntp.org", "0.asia.pool.ntp.org", "2.asia.pool.ntp.org" }, function(sec, usec, server, info)
        --            rtctime.set(sec + 19800)
        --        end,
        --        sntp_sync_time, 1)
        -- 19800 secs = 5.5 hours IST
        if timezone_handling_indicator == '1' or timezone_handling_indicator == '2' then
            sntp.sync(LANSNTPServerIP, function(sec)
                    
                    -- indicator is 0 i.e the value is an offset hence convert to number; if indicator is 1 then take offset from timezone file
                    rtctime.set(sec + (timezone_handling_indicator == '1' and gettzoffset(sec) or tonumber(tzoffset)))
                    
                    gpio_write_setup_led_pins(not cron_scheduled_after_sntp, cron_scheduled_after_sntp)
                        
                    if not cron_scheduled_after_sntp then
                        tmr.create():alarm(5000, tmr.ALARM_SINGLE, identify_and_schedule_todays_schedule_timer_entries)
                    end
                    
                    cnter = cnter >= 41 and 1 or (cnter + 1)
                    if internet_mode_configured_by and internet_mode_domain_name and cnter >= 7 and cnter <= 40 then
                        tmr.create():alarm(cron_scheduled_after_sntp and 10000 or 32000, tmr.ALARM_SINGLE, sync_state_with_cloud_for_internet_mode)
                    end
                end, internet_disconnected_handler, 1)
        end
    end)

wifi.eventmon.register(wifi.eventmon.STA_DISCONNECTED, function(tbl_disconnected_ap_details_and_reason)
        if srv then
            srv:close()
            srv=nil
        end
        mdns.close()
        
        internet_disconnected_handler()
        
        collectgarbage()
        
        --indicate_trying_connect_to_external_socket_host_wifi_start_timer()
            gpio_write_setup_led_pins(false, false)
            tmrBlinkBiColorRed:start()
            
        wifi.sta.connect()
    end)

--indicate_trying_connect_to_external_socket_host_wifi_start_timer()
    gpio_write_setup_led_pins(false, false)
    tmrBlinkBiColorRed:start()
wifi.sta.connect()