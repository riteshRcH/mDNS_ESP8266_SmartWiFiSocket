local CONSTANTS = require "SmartWiFiSocket_Constants"

require "SmartWiFiSocket_CommonUtils"

init_wifi_mode(wifi.SOFTAP)

local external_socket_host_wifi_ssid = nil
local external_socket_host_wifi_pwd = nil
local new_socket_name = nil

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
    
    srv=nil
    collectgarbage()
    srv=net.createServer(net.TCP)   --make http server
    
    if srv then
        srv:listen(CONSTANTS.NETWORK_SERVICE_SETTINGS.SETUP_WEB_SERVER_PORT, function(conn)
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
                            
                            new_socket_name = urldecode(new_socket_name)
                            external_socket_host_wifi_ssid = urldecode(external_socket_host_wifi_ssid)
                            external_socket_host_wifi_pwd = urldecode(external_socket_host_wifi_pwd)
                        
                            client_conn:send("HTTP/1.1 200 OK\n")
                            client_conn:send("Content-Type: text/plain\n\n")
                            if new_socket_name ~= nil and external_socket_host_wifi_ssid ~= nil and external_socket_host_wifi_pwd ~= nil
                            then
                                client_conn:send("received")
                                
                                srv:close()
                                srv = nil
                                
                                tmr.create():alarm(4000, tmr.ALARM_SINGLE, try_connecting_to_external_socket_host_wifi)
                                
                                indicate_trying_connect_to_external_socket_host_wifi_start_timer()
                            else
                                client_conn:send("error")
                            end
                            collectgarbage()
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
                                
                                indicate_trying_to_setup_portable_socket_start_timer()
                            else
                                client_conn:send("error")
                            end
                            collectgarbage()
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

    indicate_initial_setup_not_done()
    
    set_ap_config ( CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_SETUP_SSID,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_SETUP_PWD,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_AUTH_MODE,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_CHANNEL,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_IS_SSID_HIDDEN,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_MAX_CONN,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_BEACON_INTERVAL,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_SAVE_CFG_TO_FLASH )
    
    set_ap_ip_config()
    
    set_ap_dhcp_config_and_start_dhcp()
    
    start_setup_web_server()
end

function try_connecting_to_external_socket_host_wifi()
    init_wifi_mode(wifi.STATION)

    wifi.eventmon.register(wifi.eventmon.STA_GOT_IP, function(tbl)
            
            mdns.register(new_socket_name, { service="http", port=CONSTANTS.NETWORK_SERVICE_SETTINGS.EXECUTION_MODE_WEB_SERVER_PORT })
            
            fd = file.open(CONSTANTS.FILENAMES.INITIAL_SETUP_DONE, "w+")
            fd:write("1")                                                   -- 1 for Fixed Socket/0 for Portable Socket
            fd:close()
            fd = nil
            
            local fd = file.open(CONSTANTS.FILENAMES.MDNS_HOSTNAME, "w+")
            fd:write(new_socket_name)
            fd:close()
            
            unregister_all_wifi_eventmon_callbacks()
            
            indicate_trying_connect_to_external_socket_host_wifi_stop_timer()
            indicate_connected_to_external_socket_host_wifi_but_sntp_sync_not_done()
            
            node.restart()
        end)

    while not wifi.sta.config({ ssid = external_socket_host_wifi_ssid,
                                pwd = external_socket_host_wifi_pwd,
                                auto = CONSTANTS.WIFI_STA_DEFAULT_SETTINGS.DEFAULT_STA_AUTO_CONNECT,
                                save = CONSTANTS.WIFI_STA_DEFAULT_SETTINGS.DEFAULT_STA_SAVE_CFG_TO_FLASH }) do
        end
    wifi.sta.connect()
end

function try_setting_up_portable_socket(new_socket_wifi_ap_ssid, new_socket_wifi_ap_pwd)
    set_ap_config ( new_socket_wifi_ap_ssid,
                    new_socket_wifi_ap_pwd,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_AUTH_MODE,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_CHANNEL,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_IS_SSID_HIDDEN,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_MAX_CONN,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_BEACON_INTERVAL,
                    CONSTANTS.WIFI_AP_DEFAULT_SETTINGS.DEFAULT_AP_SAVE_CFG_TO_FLASH )
    
    set_ap_ip_config()
    
    set_ap_dhcp_config_and_start_dhcp()
    
    fd = file.open(CONSTANTS.FILENAMES.INITIAL_SETUP_DONE, "w+")
    fd:write("0")                                                   -- 1 for Fixed Socket/0 for Portable Socket
    fd:close()
    fd = nil
    
    node.restart()
end

init_wifi_ap()