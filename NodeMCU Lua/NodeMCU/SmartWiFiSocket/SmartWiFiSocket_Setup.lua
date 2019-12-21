require "/FLASH/SmartWiFiSocket_CommonUtils"

local DEFAULT_AP_AUTH_MODE = wifi.WPA_WPA2_PSK
local DEFAULT_AP_CHANNEL = 6
local DEFAULT_AP_BEACON_INTERVAL = 100
local DEFAULT_AP_SAVE_CFG_TO_FLASH = true

local external_socket_host_wifi_ssid = nil
local external_socket_host_wifi_pwd = nil
local new_socket_name = nil
local timezone_details = nil
local remember_power_cuts = nil

-- Action/State Indicator Files, made it local
--local INITIAL_SETUP_DONE = "initial_setup_done"

-- Config Persistence (Used only once in this file, hence made it local to function)
--local MDNS_HOSTNAME = "mdns_hostname"

local toggleBiColorSetupRG = false
local toggleBiColorWhichColorToToggle = nil-- 0 red + green = Portable Socket (SSID not hidden), 1 red = Fixed Socket
local tmrBlinkBiColorShowDoingSetup = tmr.create()
-- no need to stop this timer as we will be doing node.restart anyways
tmrBlinkBiColorShowDoingSetup:register(500, tmr.ALARM_AUTO, function(timer)
        toggleBiColorSetupRG = not toggleBiColorSetupRG
        if toggleBiColorWhichColorToToggle == '0' then
            gpio_write_setup_led_pins(toggleBiColorSetupRG, toggleBiColorSetupRG)
        elseif toggleBiColorWhichColorToToggle == '1' then
            gpio_write_setup_led_pins(toggleBiColorSetupRG, false)
        end
    end)

--decongested = unused channel
--function get_first_decongested_channel (tbl)
--    local nearby_channels = {}
--    local first_decongested_channel = nil
--    for bssid, value in pairs(tbl) do
--        nearby_channels[tonumber(string.match(value, "^.*,\s*([0-9]+)\s*$"))] = true
--    end
--    for ch=1,11 do
--        if ch > 2 and nearby_channels[ch] == nil then
--          first_decongested_channel = ch
--          break
--        end
--    end
    
--    if first_decongested_channel == nil then
--        first_decongested_channel = 11
--    end
    
--    init_wifi_config(first_decongested_channel)
--end

local try_connecting_to_external_socket_host_wifi = function()
    --init_wifi_mode(wifi.STATION)
        wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
        while wifi.setmode(wifi.STATION) ~= wifi.STATION do
        end

    wifi.eventmon.register(wifi.eventmon.STA_GOT_IP, function(tbl)
            
--            local EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode => hardcoding this variable value to save flash program space
            mdns.register(new_socket_name, { service="http", port = 9911 })
            
            local fd = file.open("initial_setup_done", "w+")
            fd:write("1" .. remember_power_cuts .. timezone_details)
            -- 1[01]<timezone_details>('0NA='=>no sntp sync, '1TZID='=>parse localtime, '2TZID=offset'=>no localtime parsing, use given offset directly) for Fixed Socket/0 for Portable Socket
            fd:close()
            
            fd = file.open("mdns_hostname", "w+")
            fd:write(new_socket_name)
            fd:close()
            
            node.restart()
        end)
    
--    hardcoding these variables to save flash program space
--    local DEFAULT_STA_AUTO_CONNECT = false
--    local DEFAULT_STA_SAVE_CFG_TO_FLASH = true

    while not wifi.sta.config({ ssid = external_socket_host_wifi_ssid,
                                pwd = external_socket_host_wifi_pwd,
                                auto = false,
                                save = true }) do
        end
    wifi.sta.connect()
end

local try_setting_up_portable_socket = function(new_socket_wifi_ap_ssid, new_socket_wifi_ap_pwd, set_ap_as_hidden)
    while not wifi.ap.config({
                                ssid = new_socket_wifi_ap_ssid,
                                pwd = new_socket_wifi_ap_pwd,
                                auth = DEFAULT_AP_AUTH_MODE,
                                channel = DEFAULT_AP_CHANNEL,
                                hidden = set_ap_as_hidden,
                                max = 4,                                -- allow maximum (4) persons to connect to portable socket
                                beacon = DEFAULT_AP_BEACON_INTERVAL,
                                save = DEFAULT_AP_SAVE_CFG_TO_FLASH
                            })      -- true/false => Success/Failure
                        do
    end
    
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
    
    local fd = file.open("initial_setup_done", "w+")
    fd:write("0" .. remember_power_cuts)                   -- 1 for Fixed Socket/0 for Portable Socket
    fd:close()
    
    node.restart()
end

--used encoder.fromHex() instead to save space on below function
--local url_decode = function(s)
--    return s:gsub("+", " "):gsub("%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end):gsub("\r\n", "\n")
--end

--init_wifi_ap()
    --    local old_format = 0 --SSID : Authmode, RSSI, BSSID, Channel
    --    local new_format = 1 --BSSID : SSID, RSSI, auth mode, Channel
    --    wifi.sta.getap({ ssid = nil, bssid = nil, channel = 0, show_hidden = 1}, new_format, get_first_decongested_channel)

    local DEFAULT_AP_SETUP_SSID = 'Setup Socket'
    --local DEFAULT_AP_SETUP_PWD = 'S/\/\@rT\/\/iFiS0cKeT9911:)'
    local DEFAULT_AP_SETUP_PWD = 'Sm@rTWiFiUniFyS0cKeT:)'
                
    while not wifi.ap.config({
                                ssid = DEFAULT_AP_SETUP_SSID,
                                pwd = DEFAULT_AP_SETUP_PWD,
                                auth = DEFAULT_AP_AUTH_MODE,
                                channel = DEFAULT_AP_CHANNEL,
                                hidden = false,
                                max = 1,                                -- allow only 1 person to do setup
                                beacon = DEFAULT_AP_BEACON_INTERVAL,
                                save = DEFAULT_AP_SAVE_CFG_TO_FLASH
                            })      -- true/false => Success/Failure
                        do
    end
    
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

--    To test: http://192.168.10.1/setup_fixed_socket?new_socket_name=ac&external_socket_host_wifi_ssid=SuperBeam&external_socket_host_wifi_pwd=12591259
--    To test: http://192.168.10.1/setup_portable_socket?new_socket_wifi_ap_ssid=SuperBeam&new_socket_wifi_ap_pwd=12591259

--    local SETUP_WEB_SERVER_PORT = 80
    
collectgarbage()
local srv = net.createServer(net.TCP)   --make http server

if srv then
    srv:listen(80, function(conn)
            conn:on("receive", function(client_conn, http_request)
                    --print(http_request)
                    
                    local method, path, vars = http_request:match("([A-Z]+) (.+)?(.+) HTTP")
                    if (method == nil)
                    then
                        method, path = http_request:match("([A-Z]+) (.+) HTTP")
                    end
                    
                    if method == 'GET' then
                        client_conn:send("HTTP/1.1 200 OK\nContent-Type: text/plain\n\n")
                        if path == '/setup_fixed_socket' then
                            new_socket_name = encoder.fromHex(vars:match('new_socket_name=([^&]+)'))
                            external_socket_host_wifi_ssid = encoder.fromHex(vars:match('external_socket_host_wifi_ssid=([^&]+)'))
                            external_socket_host_wifi_pwd = encoder.fromHex(vars:match('external_socket_host_wifi_pwd=([^&]+)'))
                            timezone_details = encoder.fromHex(vars:match('timezone_details=([^&]+)'))
                            remember_power_cuts = vars:match('remember_power_cuts=([^&]+)')
                        
                            if new_socket_name and external_socket_host_wifi_ssid and external_socket_host_wifi_pwd and timezone_details and remember_power_cuts then
                                client_conn:send("received")
                                
                                tmr.create():alarm(4000, tmr.ALARM_SINGLE, function()
                                        srv:close()
                                        srv = nil
                                        
                                        try_connecting_to_external_socket_host_wifi()
                                    end)
                                
                                --indicate_trying_connect_to_external_socket_host_wifi_start_timer()
                                    gpio_write_setup_led_pins(false, false)
                                    toggleBiColorWhichColorToToggle = '1'
                                    tmrBlinkBiColorShowDoingSetup:start()
                            else
                                client_conn:send("error")
                            end
                        elseif path == '/setup_portable_socket' then
                            new_socket_wifi_ap_ssid = encoder.fromHex(vars:match('new_socket_wifi_ap_ssid=([^&]+)'))
                            new_socket_wifi_ap_pwd = encoder.fromHex(vars:match('new_socket_wifi_ap_pwd=([^&]+)'))
                            set_ap_as_hidden = vars:match('set_ap_as_hidden=([^&]+)')
                            remember_power_cuts = vars:match('remember_power_cuts=([^&]+)')
                        
                            if new_socket_wifi_ap_ssid and new_socket_wifi_ap_pwd and set_ap_as_hidden and remember_power_cuts then
                                client_conn:send("received")
                                
                                tmr.create():alarm(2200, tmr.ALARM_SINGLE, function(t)
                                        srv:close()
                                        srv = nil
                                        
                                        try_setting_up_portable_socket(new_socket_wifi_ap_ssid, new_socket_wifi_ap_pwd, set_ap_as_hidden == '1')
                                    end)
                                
                                gpio_write_setup_led_pins(false, false)
                                toggleBiColorWhichColorToToggle = '0'
                                tmrBlinkBiColorShowDoingSetup:start()
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
                        elseif path == '/get_socket_software_version' then
                            local version = node.info()
                            client_conn:send(version)
                        end
                    elseif method == 'POST' and path == '/store_file_onto_flash' then
                        local store_as_filename = vars:match('store_as_filename=([^&]+)')
                        --print(store_as_filename, not file.exists(store_as_filename), store_as_filename=="localtime", store_as_filename=="tz32.lua")
                
                        local write_success_indicator = false
                        if store_as_filename == 'localtime' then
                            _, http_message_body_start = http_request:find('\r\n\r\n')
                            --print(http_request:sub(http_message_body_start+1))
                            local fd=file.open(store_as_filename, 'a+')
                            write_success_indicator = fd:write(encoder.fromHex(http_request:sub(http_message_body_start+1)))
                            fd:close()
                        end
                        --print(write_success_indicator)
                        client_conn:send("HTTP/1.1 200 OK\nContent-Type: text/plain\n\n" .. (write_success_indicator and "success" or "error"))
                    end
                end)
            conn:on("sent", function(client_conn)
                    client_conn:close()
                    collectgarbage()
                end)
        end)
end