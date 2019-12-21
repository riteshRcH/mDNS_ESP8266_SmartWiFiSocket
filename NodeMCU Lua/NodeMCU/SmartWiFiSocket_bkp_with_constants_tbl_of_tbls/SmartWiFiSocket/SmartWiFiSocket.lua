wifi.sta.disconnect()

require "SmartWiFiSocket_CommonUtils"

init_gpio_relay_pin()
unregister_all_wifi_eventmon_callbacks()
init_tri_color_led_pins()

local CONSTANTS = require "SmartWiFiSocket_Constants"

if file.exists(CONSTANTS.FILENAMES.INITIAL_SETUP_DONE) then
    
    local fd = file.open(CONSTANTS.FILENAMES.INITIAL_SETUP_DONE, "r")
    -- 1 for Fixed Socket/0 for Portable Socket
    local initial_setup_done_contents = fd:read()
    fd:close()
    fd = nil
    
    if initial_setup_done_contents == "1" then
        dofile('SmartWiFiSocket_FixedMode.lua')
    elseif initial_setup_done_contents == "0" then
        dofile('SmartWiFiSocket_PortablMode.lua')
    else
        dofile('SmartWiFiSocket_Setup.lua')
    end
else
    dofile('SmartWiFiSocket_Setup.lua')
end

init_factory_reset_redo_initial_setup_interrupt()