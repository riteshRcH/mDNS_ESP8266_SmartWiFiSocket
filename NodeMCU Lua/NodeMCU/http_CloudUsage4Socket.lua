--taken from here https://github.com/stuartpb/tvtropes-lua/blob/master/urlencode.lua
function url_encode(str)
    --1st gsub Ensure all newlines are in CRLF form
    
    --2nd gsub Percent-encode all non-unreserved characters
    --2nd gsub as per RFC 3986, Section 2.3
    --2nd gsub (except for space, which gets plus-encoded)

    --3rd gsub Convert spaces to plus signs
    return str:gsub("\r?\n", "\r\n"):gsub("([^%w%-%.%_%~ ])", function (c) return string.format ("%%%02X", string.byte(c)) end):gsub(" ", "+")
end

http.get("http://pastebin.com/raw/EYQQsda5", nil, function(http_status_code, http_response)
    if http_status_code == 200 then
        local _, _, cipher_text_len, cipher_text = http_response:find('([0-9]+)\r\n(.*)')
        local domain_name_and_rest_api_version = crypto.decrypt("AES-CBC", "Pw4DoMAiNnAmE92$", encoder.fromBase64(cipher_text), "IVFoRAESCBCYo92&"):match("(.-)%z*$"):sub(1, cipher_text_len)
        if string.len(domain_name_and_rest_api_version) == tonumber(cipher_text_len) then
            socket_name = 'Fan'
            external_wifi_ssid = 'TripMate-94AA'
            domain_name_and_rest_api_version = domain_name_and_rest_api_version:match("^%s*(.-)%s*$")
            get_url = domain_name_and_rest_api_version .. '/sockets_per_user_status?email=' .. encoder.toHex('ritesht93@gmail.com') .. '&socket_name=' .. socket_name:gsub(' ', '+') .. '&external_wifi_ssid=' .. encoder.toHex(external_wifi_ssid) .. '&source=socket'
            http.get(get_url, nil, function(code, data)
                if code == 200 and data then
                    local _, _, cipher_data_len, iv, cipher_data = data:find('([0-9]+)\r\n(................)(.*)')
                    if not iv:find('[^0-9dfhjlnprtvxzEGIKNOQSUWY]') then
                        local _, _, stop_running_timer, desired_state = crypto.decrypt("AES-CBC", "PwSeRVeRdAta#92*", encoder.fromHex(cipher_data), iv):match("(.-)%z*$"):sub(1, cipher_data_len):match("^%s*(.-)%s*$"):find('^([0-9])([0-9])$')
                        print(stop_running_timer, desired_state)
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
                    http.put(domain_name_and_rest_api_version .. '/sockets_per_user', nil, iv2 ..encoder.toHex(crypto.encrypt("AES-CBC", "PwS0cKEtDaTA!92$", 'ritesht93@gmail.com~' .. socket_name .. '~' .. external_wifi_ssid .. '~0~' .. current_rtctime .. '~019800~socket', iv2)), function(http_put_status_code, http_put_response)
                        --if not (http_put_status_code < 0) then
                        --    print(http_put_response)
                        --end
                    end)
                end
            end)
        end
     end
end)