collectgarbage()
require "SmartWiFiSocket_CommonUtils"

--made it local
--local EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode

local srv=nil
--local calling_identify_and_schedule_todays_schedule_timer_entries_after_1st_sntp_sync = true

local is_any_timer_running = false
local running_timer_type = nil
local running_timer_end_time = 0
local running_timer_cron_mask_config_string = nil
local running_timer_obj_ref = nil
local running_timer_obj_type = nil  -- false = tmr, true = cron.entry

local sntp_snyc_retry_cnt = 0

--local scheduled_all_cron_entries = false

-- Action/State Indicator Files
--local INITIAL_SETUP_DONE = "initial_setup_done"   --not used in this file
--local IS_INTERNET_MODE_CONFIGURED = "is_internet_mode_configured"     --Used only in this file so made it local to function

-- On Boot Indicators
local sntp_sync_done_once_on_boot = false
local cron_scheduled_after_sntp = false

-- Config Persistence (Used only once in this file hence moved it into function)
--local MDNS_HOSTNAME = "mdns_hostname"

-- Schedule/Timers Entry List
--made it local
--local ONE_SHOT_FUTURE_TIMERS = "OSFT"
--local FUTURE_SCHEDULES = "FS"
--local RECURRING_SCHEDULES = "RS"
--local RECURRING_TIMERS = "RT"
    
local fd = file.open('initial_setup_done', 'r')
local _, _, _, timezone_offset_or_zone_filename_indicator, timezone_offset_or_zone_filename = fd:read():find('^(.)(.)(.+)$')
fd:close()

local internet_mode_configured_by = nil
local mdns_hostname = nil
local external_wifi_ssid = nil

fd = file.open('version', 'r')
socketSoftwareVersion = fd:read()
fd:close()

local tmrSyncStateWithCloudForInternetMode = tmr.create()
tmrSyncStateWithCloudForInternetMode:register(900000, tmr.ALARM_AUTO, function(timer)
        http.get("http://pastebin.com/raw/EYQQsda5", nil, function(http_status_code, http_response)
                if http_status_code == 200 then
                    local _, _, cipher_text_len, cipher_text = http_response:find('([0-9]+)\r\n(.*)')
                    local domain_name_and_rest_api_version = crypto.decrypt("AES-CBC", "Pw4DoMAiNnAmE92$", encoder.fromBase64(cipher_text), "IVFoRAESCBCYo92&"):match("(.-)%z*$"):sub(1, cipher_text_len)
                    if string.len(domain_name_and_rest_api_version) == tonumber(cipher_text_len) then
                        domain_name_and_rest_api_version = domain_name_and_rest_api_version:match("^%s*(.-)%s*$")
                        get_url = domain_name_and_rest_api_version .. '/sockets_per_user_status?email=' .. encoder.toHex(internet_mode_configured_by) .. '&socket_name=' .. mdns_hostname:gsub(' ', '+') .. '&external_wifi_ssid=' .. encoder.toHex(external_wifi_ssid) .. '&source=socket'
                        http.get(get_url, nil, function(code, data)
                                if code == 200 and data then
                                    local _, _, cipher_data_len, iv, cipher_data = data:find('([0-9]+)\r\n(................)(.*)')
                                    if not iv:find('[^0-9dfhjlnprtvxzEGIKNOQSUWY]') then
                                        local _, _, stop_running_timer, desired_state = crypto.decrypt("AES-CBC", "PwSeRVeRdAta#92*", encoder.fromHex(cipher_data), iv):match("(.-)%z*$"):sub(1, cipher_data_len):match("^%s*(.-)%s*$"):find('^([0-9])([0-9])$')
                                        if stop_running_timer == '1' and is_any_timer_running and running_timer_obj_ref then
                                            if running_timer_obj_type then
                                                running_timer_obj_ref:unschedule()
                                            else
                                                running_timer_obj_ref:stop()
                                            end
                                            is_any_timer_running = false
                                        end
                                        if desired_state then
                                            (desired_state == '1' and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
                                        end
                                    end

                                    local iv2 = ''
                                    for i=1, 16, 1 do
                                        iv2 = iv2 .. string.char(node.random(65, 90))
                                    end
                                    current_rtctime = rtctime.get()
                                    http.put(domain_name_and_rest_api_version .. '/sockets_per_user', nil, iv2 ..encoder.toHex(crypto.encrypt("AES-CBC", "PwS0cKEtDaTA!92$", internet_mode_configured_by .. '~' .. mdns_hostname .. '~' .. external_wifi_ssid .. '~' .. (is_any_timer_running and (get_gpio_relay_pin_status() == 1 and "2" or "3") or get_gpio_relay_pin_status()) .. '~' .. current_rtctime .. '~' .. timezone_offset_or_zone_filename_indicator .. timezone_offset_or_zone_filename .. '~socket' .. (is_any_timer_running and ('~' .. running_timer_type .. "~" .. running_timer_cron_mask_config_string .. "~" .. (running_timer_end_time-current_rtctime)) or "") .. "~" .. socketSoftwareVersion, iv2)), function(http_put_status_code, http_put_response)
                                            --if not (http_put_status_code < 0) then print(http_put_response) end
                                        end)
                                end
                            end)
                    end
                end
                collectgarbage()
            end)
    end)
fd = file.open("internet_mode_configured", 'r')
if fd then
    internet_mode_configured_by = fd:read()
    if internet_mode_configured_by then
        tmrSyncStateWithCloudForInternetMode:start()
    end
    fd:close()
end
collectgarbage()

local toggleTriColorRed = false
local tmrBlinkTriColorRed = tmr.create()
tmrBlinkTriColorRed:register(500, tmr.ALARM_AUTO, function(timer)
        toggleTriColorRed = not toggleTriColorRed
        --gpio.write(0, toggleTriColorRed and gpio.HIGH or gpio.LOW)
        gpio_write_setup_led_pins(toggleTriColorRed, false, false)
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

local url_decode = function(s)
    return s:gsub("+", " "):gsub("%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end):gsub("\r\n", "\n")
end

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
            running_timer_cron_mask_config_string = cron_mask_config_string
            current_rtctime = rtctime.get()
                                                
            if current_rtctime > 0 then
                running_timer_end_time = current_rtctime+timer_duration_secs
                -- after_state un-necessary as it will be opposite of before_state
                cron_schedule_OSCT_OSFT_RT_end_time(timer_duration_secs, before_state_in_use)
            end
        end
    end
    cron.schedule(cron_mask, schedule_callback)
end

local remove_all_date_files = function(except_date)
    for past_daily_schedule_timer_filename, _ in pairs(file.list())
    do
        if past_daily_schedule_timer_filename:find('^_') and past_daily_schedule_timer_filename ~= except_date then
            file.remove(past_daily_schedule_timer_filename)
        end
    end
end

local identify_and_schedule_todays_schedule_timer_entries = function()
    cron.reset()
    
    current_rtctime = rtctime.get()
    --FOR CRON => 0=SUN, 1=MON, 2=TUE, 3=WED, 4=THU, 5=FRI, 6=SAT
    --FROM RTC => 1=SUN, 2=MON, 3=TUE, 4=WED, 5=THU, 6=FRI, 7=SAT
    today=tostring(rtctime.epoch2cal(current_rtctime)['wday']-1)
    tbl = rtctime.epoch2cal(current_rtctime)  --dont put local tbl here, tbl gets nullified (dont know why)
    local todays_schedule_timers = '_' .. tbl['day'] .. tbl['mon'] .. tbl['year']
    
    remove_all_date_files(todays_schedule_timers)
    local was_todays_schedule_timers_present = file.exists(todays_schedule_timers) and file.list()[todays_schedule_timers] > 0
    
    -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
    fd = file.open(todays_schedule_timers, was_todays_schedule_timers_present and "r" or "w+")
    local todays_schedule_timers_contents = ''
    if was_todays_schedule_timers_present then
        todays_schedule_timers_contents = fd:read()
        -- when the file is empty, todays_schedule_timers_contents would be nil then making it empty string for below string.find calls
        if not todays_schedule_timers_contents then
            todays_schedule_timers_contents = ''
        end
    end
    fd:close()

    -- Scheduling all One Shot Future Timers
    fd = file.open("OSFT", 'r')
    while true do
        cron_mask_config_string = fd:readline()
        -- cron_mask_config_string:find('^1') checks if it is enabled
        if cron_mask_config_string and cron_mask_config_string:find('^1') then
            cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") --match() used to trim
            --after_state un-necessary as it will be opposite of before_state
            local _, _, cron_mask, in_year, before_state, timer_duration_secs, _ = cron_mask_config_string:find('.([^=]+)=([^=]+)=(.)=([^=]+)=(.)')
            local _, _, min, hour, day, month = cron_mask:find('([^_]+)_([^_]+)_([^_]+)_([^_]+)_*')
            
            if tonumber(tbl['year']) == tonumber(in_year) and tonumber(tbl['mon']) == tonumber(month) and tonumber(tbl['day']) == tonumber(day) and ((tonumber(tbl['hour']) == tonumber(hour) and tonumber(tbl['min']) < tonumber(min)) or tonumber(tbl['hour']) < tonumber(hour)) then
                if not was_todays_schedule_timers_present then
                   append_line_to_file("1OSFT=" .. cron_mask_config_string, todays_schedule_timers, true)
                end
                if not was_todays_schedule_timers_present or todays_schedule_timers_contents:find("1OSFT=" .. cron_mask_config_string) then
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
        -- cron_mask_config_string:find('^1') checks if it is enabled
        if cron_mask_config_string and cron_mask_config_string:find('^1') then
            cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") --match() used to trim
            local _, _, cron_mask, in_year, desired_state = cron_mask_config_string:find('.([^=]+)=([^=]+)=(.)')
            local _, _, min, hour, day, month = cron_mask:find('([^_]+)_([^_]+)_([^_]+)_([^_]+)_*')
            
            if tonumber(tbl['year']) == tonumber(in_year) and tonumber(tbl['mon']) == tonumber(month) and tonumber(tbl['day']) == tonumber(day) and ((tonumber(tbl['hour']) == tonumber(hour) and tonumber(tbl['min']) < tonumber(min)) or tonumber(tbl['hour']) < tonumber(hour)) then
                if not was_todays_schedule_timers_present then
                    append_line_to_file("1FS=" .. cron_mask_config_string, todays_schedule_timers, true)
                end
                if not was_todays_schedule_timers_present or todays_schedule_timers_contents:find("1FS=" .. cron_mask_config_string) then
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
        -- cron_mask_config_string:find('^1') checks if it is enabled
        if cron_mask_config_string and cron_mask_config_string:find('^1') then
            cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") -- match() used to trim
            local _, _, cron_mask, recurrence_start, recurrence_end, desired_state = cron_mask_config_string:find('.([^=]+)=([^=]+)=([^=]+)=(.)')
            _, _, cron_mask_recurring_days = cron_mask:find('[^_]+_[^_]+_[^_]+_[^_]+_([^_]+)')
            
            recurrence_start = tonumber(recurrence_start)
            recurrence_end = tonumber(recurrence_end)
            
            -- Today's day (0,1,2 etc) must be present in the cron mask
            if ((recurrence_start == 0 or recurrence_end == 0) or (current_rtctime > recurrence_start and current_rtctime < recurrence_end)) and (cron_mask_recurring_days == '*' or cron_mask_recurring_days:find(today)) then
                if not was_todays_schedule_timers_present then
                    append_line_to_file("1RS=" .. cron_mask_config_string, todays_schedule_timers, true)
                end
                if not was_todays_schedule_timers_present or todays_schedule_timers_contents:find("1RS=" .. cron_mask_config_string) then
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
        -- cron_mask_config_string:find('^1') checks if it is enabled
        if cron_mask_config_string and cron_mask_config_string:find('^1') then
            cron_mask_config_string = cron_mask_config_string:match("^%s*(.-)%s*$") --match() used to trim
            --after_state un-necessary as it will be opposite of before_state
            local _, _, cron_mask, recurrence_start, recurrence_end, before_state, timer_duration_secs, _ = cron_mask_config_string:find('.([^=]+)=([^=]+)=([^=]+)=(.)=([^=]+)=(.)')
            _, _, cron_mask_recurring_days = cron_mask:find('[^_]+_[^_]+_[^_]+_[^_]+_([^_]+)')
            
            recurrence_start = tonumber(recurrence_start)
            recurrence_end = tonumber(recurrence_end)
            
            -- Today's day (0,1,2 etc) must be present in the cron mask
            if ((recurrence_start == 0 or recurrence_end == 0) or (current_rtctime > recurrence_start and current_rtctime < recurrence_end)) and (cron_mask_recurring_days == '*' or cron_mask_recurring_days:find(today)) then
                if not was_todays_schedule_timers_present then
                   append_line_to_file("1RT=" .. cron_mask_config_string, todays_schedule_timers, true)
                end
                if not was_todays_schedule_timers_present or todays_schedule_timers_contents:find("1RT=" .. cron_mask_config_string) then
                    cron_schedule_timer(cron_mask:gsub('_', ' '), before_state, tonumber(timer_duration_secs), "rt", cron_mask_config_string)
                end
            end
        else
            break
        end
    end
    fd:close()
    
    cron.schedule('02 0 * * *', function(e)
            if get_gpio_relay_pin_status() == 1 then
                -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
                file.open('restore_relay_on', "w+"):close()
            end
            
            -- previous todays_schedule_timer for past dates
            remove_all_date_files(nil)
            node.restart()
        end)   -- restart Socket everyday at 12:02 am
    
    cron_scheduled_after_sntp = true
    
    --indicate_fixed_socket_mode()
    gpio_write_setup_led_pins(false, true, false)
    
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
                
                        local _, _, method, path, vars = payload:find("([A-Z]+) (.+)?(.+) HTTP")
                        if (method == nil)
                        then
                            _, _, method, path = payload:find("([A-Z]+) (.+) HTTP")
                        end

                        client_conn:send("HTTP/1.1 200 OK\nContent-Type: text/plain\n\n")
                        if method == 'GET' then
                            if path == '/gpio/relay_status' then
                                current_rtctime = rtctime.get()
                                client_conn:send((is_any_timer_running and (get_gpio_relay_pin_status() == 1 and "2" or "3") or get_gpio_relay_pin_status()) .. ((internet_mode_configured_by == nil and '0' or ('1' .. internet_mode_configured_by)) .. "~" .. external_wifi_ssid .. '~' .. ((current_rtctime == nil or current_rtctime == 0) and "NA" or (timezone_offset_or_zone_filename_indicator .. timezone_offset_or_zone_filename))) .. (is_any_timer_running and ('~' .. running_timer_type .. ";" .. running_timer_cron_mask_config_string .. ";" .. (running_timer_end_time-current_rtctime)) or "") .. "~" .. socketSoftwareVersion)
                            elseif path == '/gpio/relay/1' then
                                client_conn:send(is_any_timer_running and "error" or (set_gpio_relay_pin(false) and "success" or "error"))
                            elseif path == '/gpio/relay/0' then
                                client_conn:send(is_any_timer_running and "error" or (reset_gpio_relay_pin(false) and "success" or "error"))
                            elseif path == '/set_new_osct' then
                                if sntp_sync_done_once_on_boot and sntp_snyc_retry_cnt <= 16 then
                                    if is_any_timer_running then
                                        client_conn:send("error_another_timer_running")
                                    else
                                        current_rtctime = rtctime.get()
                                        
                                        if current_rtctime > 0 then
                                            local _, _, cron_mask_config_string = vars:find('cron_mask_config_string=([^&]+)')
                                            cron_mask_config_string = url_decode(cron_mask_config_string)
                                            --after_state un-necessary as it will be opposite of before_state
                                            local _, _, before_state, timer_duration_secs, _ = cron_mask_config_string:find('(.)=([^=]+)=(.)')
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
                                        else
                                            client_conn:send("error_no_rtc")
                                        end
                                    end
                                else
                                    client_conn:send("error_no_rtc")
                                end
                            elseif path == '/get_schedule_timers' then
                                local _, _, schedule_timer_filename = vars:find('schedule_timer_filename=([^&]+)')
                                local fh = file.open(schedule_timer_filename, 'r')
                                if fh and (schedule_timer_filename == 'OSFT' or schedule_timer_filename == 'FS' or schedule_timer_filename == 'RS' or schedule_timer_filename == 'RT' or schedule_timer_filename:find('^_')) then
                                    local file_contents = fh:read()
                                    client_conn:send(file_contents == nil and "empty" or file_contents)
                                    fh:close()
                                else
                                    client_conn:send("error")
                                end
                            elseif path == '/save_update_enabled_remove_schedule_timer_or_update_run_skip_for_today' then  -- for today's date
                                local _, _, call_type = vars:find('call_type=([^&]+)')
                                if sntp_sync_done_once_on_boot and sntp_snyc_retry_cnt <= 16 then
                                    if is_any_timer_running and call_type ~= 'removing_past_entries' then
                                        client_conn:send("error_another_timer_running")
                                    else
                                        if get_gpio_relay_pin_status() == 0 or call_type == 'removing_past_entries' then
                                            local _, _, schedule_timer_filename = vars:find('schedule_timer_filename=([^&]+)')
                                            local _, _, cron_mask_config_string = vars:find('cron_mask_config_string=([^&]+)')
                                            local _, _, truncate_before_update = vars:find('truncate_before_update=([^&]+)')
                                            local _, _, last_run_skip_update_or_todays_entry_saved_updated_removed = vars:find('last_run_skip_update_or_todays_entry_saved_updated_removed=([^&]+)')
                                            local _, _, todays_schedule_timers = vars:find('todays_schedule_timers=([^&]+)')
                                                        
                                            -- w+ truncates but a+ doesnt truncate if exists; both w+ and a+ creates file if not exists (create_action_result_indicator_file)
                                            if truncate_before_update == '1' then
                                                file.open(schedule_timer_filename,  "w+"):close()
                                            end
                                            
                                            if cron_mask_config_string then
                                                cron_mask_config_string = url_decode(cron_mask_config_string)
                                            end
                                            client_conn:send((cron_mask_config_string == nil or append_line_to_file(cron_mask_config_string, schedule_timer_filename, true)) and "success" or "error")
                                            
                                            if last_run_skip_update_or_todays_entry_saved_updated_removed == '1' then
                                                if call_type == 'remove_saved_schedule_timer' or call_type == 'save_new_schedule_timer' or call_type == 'update_enabled_schedule_timers' then
                                                    file.remove(todays_schedule_timers)
                                                end
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
                                local _, _, state_after_stopping_running_timer = vars:find('state_after_stopping_running_timer=([^&]+)')
                                if is_any_timer_running and running_timer_obj_ref then
                                    if running_timer_obj_type then
                                        running_timer_obj_ref:unschedule()
                                    else
                                        running_timer_obj_ref:stop()
                                    end;
                                    
                                    (state_after_stopping_running_timer == '1' and set_gpio_relay_pin or reset_gpio_relay_pin)(false);
                                    is_any_timer_running = false
                                    client_conn:send("success")
                                else
                                    client_conn:send("no_running_timer")
                                end
                            elseif path == '/internet_mode_config' then
                                local _, _, local_internet_mode_configured_by = vars:find('internet_mode_configured_by=([^&]+)')
                                local_internet_mode_configured_by = url_decode(local_internet_mode_configured_by)
                                if append_line_to_file(local_internet_mode_configured_by, "internet_mode_configured", false) then
                                    internet_mode_configured_by = local_internet_mode_configured_by
                                    tmrSyncStateWithCloudForInternetMode:start()
                                    
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

-- called only once hence made it inline
--function stop_fixed_mode_web_server()
--    if srv then
--        srv:close()
--        srv=nil
--        collectgarbage()
--    end
--end

wifi.eventmon.register(wifi.eventmon.STA_GOT_IP, function(tbl_ip_params)
        
        --indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
            tmrBlinkTriColorRed:stop()
        --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
            gpio_write_setup_led_pins(true, false, false)
            
        external_wifi_ssid = wifi.sta.getconfig(true)['ssid']
        
        local fh = file.open("mdns_hostname", 'r')
        mdns_hostname = fh:read()
        fh:close()
        
        mdns.register(mdns_hostname, { service="http", port = 9911 })
        
        start_fixed_mode_web_server()
        
        if sntp_sync_done_once_on_boot then
            if sntp_snyc_retry_cnt <= 16 then
                --indicate_fixed_socket_mode()
                    gpio_write_setup_led_pins(false, true, false)
            else
                --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
                    gpio_write_setup_led_pins(true, false, false)
                cron.reset()
                cron_scheduled_after_sntp = false
                sntp_sync_done_once_on_boot = false
            end
        else
            --    sntp.sync({ "1.in.pool.ntp.org", "0.asia.pool.ntp.org", "2.asia.pool.ntp.org", "ntp1.iitd.ac.in", "ntp2.iitd.ac.in" }, function(sec, usec, server, info)
            --            rtctime.set(sec + 19800)
            --        end,
            --        sntp_sync_time, 1)
            -- 19800 secs = 5.5 hours IST
            sntp.sync(nil, function(sec)
                    if timezone_offset_or_zone_filename_indicator == '0' then
                        rtctime.set(sec + tonumber(timezone_offset_or_zone_filename))     -- indicator is 0 i.e the value is an offset hence convert to number
                    end
                       
                    sntp_sync_done_once_on_boot = true
                        
--                    if sntp_snyc_retry_cnt <= 16 then
--                        --indicate_fixed_socket_mode()
--                        gpio_write_setup_led_pins(false, true, false)
--                    end
                        
                    if cron_scheduled_after_sntp then
                        if sntp_snyc_retry_cnt <= 16 then
                            --indicate_fixed_socket_mode()
                            gpio_write_setup_led_pins(false, true, false)
                        elseif get_gpio_relay_pin_status() == 0 and not is_any_timer_running and sntp_snyc_retry_cnt > 16 then
                            --node.restart()
                            tmr.create():alarm(5000, tmr.ALARM_SINGLE, identify_and_schedule_todays_schedule_timer_entries)
                        end
                    else
                        --calling_identify_and_schedule_todays_schedule_timer_entries_after_1st_sntp_sync = true
                        tmr.create():alarm(5000, tmr.ALARM_SINGLE, identify_and_schedule_todays_schedule_timer_entries)
                    end
                        
                    sntp_snyc_retry_cnt = 0
                end, function()
                    sntp_snyc_retry_cnt = sntp_snyc_retry_cnt + 1
                    -- SNTP SYNC was done once on boot and after that if couldn't sntp sync till 16 times (4 hours tried syncing every 15 mins) then show red led on
                    if sntp_sync_done_once_on_boot and sntp_snyc_retry_cnt <= 16 then
                        --indicate_fixed_socket_mode()
                        gpio_write_setup_led_pins(true, true, false)
                    else
                        --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
                        gpio_write_setup_led_pins(true, false, false)
                        cron.reset()
                        cron_scheduled_after_sntp = false
                        sntp_sync_done_once_on_boot = false
                    end
                end, 1)
        end
    end)

wifi.eventmon.register(wifi.eventmon.STA_DISCONNECTED, function(tbl_disconnected_ap_details_and_reason)
        --stop_fixed_mode_web_server()
            if srv then
                srv:close()
                srv=nil
                collectgarbage()
            end
        mdns.close()
        
        --indicate_trying_connect_to_external_socket_host_wifi_start_timer()
            gpio_write_setup_led_pins(false, false, false)
            tmrBlinkTriColorRed:start()
            
        wifi.sta.connect()
    end)

--indicate_trying_connect_to_external_socket_host_wifi_start_timer()
    gpio_write_setup_led_pins(false, false, false)
    tmrBlinkTriColorRed:start()
wifi.sta.connect()