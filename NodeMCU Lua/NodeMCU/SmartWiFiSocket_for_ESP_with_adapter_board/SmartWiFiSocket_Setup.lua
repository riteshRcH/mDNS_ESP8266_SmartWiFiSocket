require "SmartWiFiSocket_CommonUtils"

--init_wifi_mode(wifi.SOFTAP)
    wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
    while wifi.setmode(wifi.SOFTAP) ~= wifi.SOFTAP do
    end

local DEFAULT_AP_AUTH_MODE = wifi.WPA_WPA2_PSK
local DEFAULT_AP_CHANNEL = 6
local DEFAULT_AP_IS_SSID_HIDDEN = false
local DEFAULT_AP_MAX_CONN = 4
local DEFAULT_AP_BEACON_INTERVAL = 100
local DEFAULT_AP_SAVE_CFG_TO_FLASH = true

local external_socket_host_wifi_ssid = nil
local external_socket_host_wifi_pwd = nil
local new_socket_name = nil
local timezone_details = nil

-- Action/State Indicator Files
local INITIAL_SETUP_DONE = "initial_setup_done"

-- Config Persistence (Used only once in this file, hence made it local to function)
--local MDNS_HOSTNAME = "mdns_hostname"

local toggleTriColorBlue = false
local tmrBlinkTriColorBlue = tmr.create()
tmrBlinkTriColorBlue:register(500, tmr.ALARM_AUTO, function(timer)
        toggleTriColorBlue = not toggleTriColorBlue
        --gpio.write(TRI_COLOR_SETUP_LED_BLUE_OPIN, toggleTriColorBlue and gpio.HIGH or gpio.LOW)
        --gpio.write(2, toggleTriColorBlue and gpio.HIGH or gpio.LOW)
        gpio_write_setup_led_pins(false, false, toggleTriColorBlue)
    end)

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

function urldecode(str)
    str = string.gsub(str, "+", " ")
    str = string.gsub(str, "%%(%x%x)", function(h) return string.char(tonumber(h, 16)) end)
    str = string.gsub(str, "\r\n", "\n")
    return str
end

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

function set_ap_config (new_ssid, new_pwd, new_auth_mode, new_channel, new_is_ssid_hidden, new_max_conn, new_beacon_interval, save_config_to_flash)
    while not wifi.ap.config({
                                ssid = new_ssid,
                                pwd = new_pwd,
                                auth = new_auth_mode,
                                channel = new_channel,
                                hidden = new_is_ssid_hidden,
                                max = new_max_conn,
                                beacon = new_beacon_interval,
                                save = save_config_to_flash
                            })      -- true/false => Success/Failure
                        do
    end
end

function start_setup_web_server()
--    To test: http://192.168.10.1/setup_fixed_socket?new_socket_name=ac&external_socket_host_wifi_ssid=SuperBeam&external_socket_host_wifi_pwd=12591259
--    To test: http://192.168.10.1/setup_portable_socket?new_socket_wifi_ap_ssid=SuperBeam&new_socket_wifi_ap_pwd=12591259

--    local SETUP_WEB_SERVER_PORT = 80
    
    srv=nil
    collectgarbage()
    srv=net.createServer(net.TCP)   --make http server
    
    if srv then
        srv:listen(80, function(conn)
                conn:on("receive", function(client_conn, http_request)
                        
                        local _, _, method, path, vars = string.find(http_request, "([A-Z]+) (.+)?(.+) HTTP")
                        if (method == nil)
                        then
                            _, _, method, path = string.find(http_request, "([A-Z]+) (.+) HTTP")
                        end
                        
                        if method == 'GET' and path == '/setup_fixed_socket'
                        then
                            _, _, new_socket_name = string.find(vars, 'new_socket_name=([^&]+)')
                            _, _, external_socket_host_wifi_ssid = string.find(vars, 'external_socket_host_wifi_ssid=([^&]+)')
                            _, _, external_socket_host_wifi_pwd = string.find(vars, 'external_socket_host_wifi_pwd=([^&]+)')
                            _, _, timezone_details = string.find(vars, 'timezone_details=([^&]+)')
                            
                            new_socket_name = urldecode(new_socket_name)
                            external_socket_host_wifi_ssid = urldecode(external_socket_host_wifi_ssid)
                            external_socket_host_wifi_pwd = urldecode(external_socket_host_wifi_pwd)
                            timezone_details = urldecode(timezone_details)
                        
                            client_conn:send("HTTP/1.1 200 OK\n")
                            client_conn:send("Content-Type: text/plain\n\n")
                            if new_socket_name ~= nil and external_socket_host_wifi_ssid ~= nil and external_socket_host_wifi_pwd ~= nil and timezone_details ~= nil
                            then
                                client_conn:send("received")
                                
                                srv:close()
                                srv = nil
                                
                                tmr.create():alarm(4000, tmr.ALARM_SINGLE, try_connecting_to_external_socket_host_wifi)
                                
                                indicate_trying_connect_to_external_socket_host_wifi_start_timer()
                            else
                                client_conn:send("error")
                            end
                        elseif method == 'GET' and path == '/setup_portable_socket' then
                            _, _, new_socket_wifi_ap_ssid = string.find(vars, 'new_socket_wifi_ap_ssid=([^&]+)')
                            _, _, new_socket_wifi_ap_pwd = string.find(vars, 'new_socket_wifi_ap_pwd=([^&]+)')
                            
                            new_socket_wifi_ap_ssid = urldecode(new_socket_wifi_ap_ssid)
                            new_socket_wifi_ap_pwd = urldecode(new_socket_wifi_ap_pwd)
                        
                            client_conn:send("HTTP/1.1 200 OK\n")
                            client_conn:send("Content-Type: text/plain\n\n")
                            if new_socket_wifi_ap_ssid ~= nil and new_socket_wifi_ap_pwd ~= nil
                            then
                                client_conn:send("received")
                                
                                srv:close()
                                srv = nil
                                
                                tmr.create():alarm(2200, tmr.ALARM_SINGLE, function(t)
                                        try_setting_up_portable_socket(new_socket_wifi_ap_ssid, new_socket_wifi_ap_pwd)
                                    end)
                                
                                gpio_write_setup_led_pins(false, false, false)
                                
                                tmrBlinkTriColorBlue:start()
                            else
                                client_conn:send("error")
                            end
                        elseif method == 'GET' and path == '/get_scanned_nearby_wifi_networks' then
                            local fd = file.open('scanned_nearby_wifi_networks', 'r')
                            client_conn:send("HTTP/1.1 200 OK\n")
                            client_conn:send("Content-Type: text/plain\n\n")
                            if fd then
                                client_conn:send(fd:read())
                                fd:close()
                            else
                                client_conn:send('error')
                            end
                            fd = nil
                        end
                    end)
                conn:on("sent", function(client_conn)
                        client_conn:close()
                        collectgarbage()
                    end)
            end)
    end
end

function init_wifi_ap()
--    local old_format = 0 --SSID : Authmode, RSSI, BSSID, Channel
--    local new_format = 1 --BSSID : SSID, RSSI, auth mode, Channel
--    wifi.sta.getap({ ssid = nil, bssid = nil, channel = 0, show_hidden = 1}, new_format, get_first_decongested_channel)

    local DEFAULT_AP_SETUP_SSID = 'Setup Smart WiFi Socket'
    --local DEFAULT_AP_SETUP_PWD = 'S/\/\@rT\/\/iFiS0cKeT9911:)'
    local DEFAULT_AP_SETUP_PWD = 'Sm@rTWiFiS0cKeT:)'

    --indicate_initial_setup_not_done()
        gpio_write_setup_led_pins(false, false, false)
    
    set_ap_config ( DEFAULT_AP_SETUP_SSID,
                    DEFAULT_AP_SETUP_PWD,
                    DEFAULT_AP_AUTH_MODE,
                    DEFAULT_AP_CHANNEL,
                    DEFAULT_AP_IS_SSID_HIDDEN,
                    DEFAULT_AP_MAX_CONN,
                    DEFAULT_AP_BEACON_INTERVAL,
                    DEFAULT_AP_SAVE_CFG_TO_FLASH )
    
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
    
    start_setup_web_server()
end

function try_connecting_to_external_socket_host_wifi()
    --init_wifi_mode(wifi.STATION)
        wifi.setphymode(wifi.PHYMODE_N) -- IEEE 802.11n physical mode
        while wifi.setmode(wifi.STATION) ~= wifi.STATION do
        end

    wifi.eventmon.register(wifi.eventmon.STA_GOT_IP, function(tbl)
            
--            local EXECUTION_MODE_WEB_SERVER_PORT = 9911   -- for both Fixed and Portable mode => hardcoding this variable value to save flash program space
            mdns.register(new_socket_name, { service="http", port = 9911 })
            
            local fd = file.open(INITIAL_SETUP_DONE, "w+")
            fd:write("1" .. timezone_details)                       -- 1<timezone_details> for Fixed Socket/0 for Portable Socket
            fd:close()
            fd = nil
            
            fd = file.open("mdns_hostname", "w+")
            fd:write(new_socket_name)
            fd:close()
            fd = nil
            
            --indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
                tmrBlinkTriColorRed:stop()
    
                gpio_write_setup_led_pins(false, false, false)
            --indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
                gpio_write_setup_led_pins(true, false, false)
            
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

function try_setting_up_portable_socket(new_socket_wifi_ap_ssid, new_socket_wifi_ap_pwd)
    set_ap_config ( new_socket_wifi_ap_ssid,
                    new_socket_wifi_ap_pwd,
                    DEFAULT_AP_AUTH_MODE,
                    DEFAULT_AP_CHANNEL,
                    DEFAULT_AP_IS_SSID_HIDDEN,
                    DEFAULT_AP_MAX_CONN,
                    DEFAULT_AP_BEACON_INTERVAL,
                    DEFAULT_AP_SAVE_CFG_TO_FLASH )
    
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
    
    local fd = file.open(INITIAL_SETUP_DONE, "w+")
    fd:write("0")                                                   -- 1 for Fixed Socket/0 for Portable Socket
    fd:close()
    fd = nil
    
    node.restart()
end

init_wifi_ap()