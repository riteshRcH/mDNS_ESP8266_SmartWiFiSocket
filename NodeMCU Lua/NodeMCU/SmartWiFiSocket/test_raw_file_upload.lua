srv=net.createServer(net.TCP)   --make http server
srv:listen(80, function(conn)
            conn:on("receive", function(client_conn, http_request)
                local _, _, method, path, vars = string.find(http_request, "([A-Z]+) (.+)?(.+) HTTP")
                if (method == nil)
                then
                    _, _, method, path = string.find(http_request, "([A-Z]+) (.+) HTTP")
                end
                
                if method == 'POST' and path == '/store_file_onto_flash' then
                        local _, _, store_as_filename = string.find(vars, 'store_as_filename=([^&]+)')
                
                        _, http_message_body_start = http_request:find('\r\n\r\n')
                        fd=file.open(store_as_filename, 'w+')
                        fd:write(http_request:sub(http_message_body_start+1))
                        fd:close()

                        client_conn:send("HTTP/1.1 200 OK\n")
                        client_conn:send("Content-Type: text/plain\n\n")
                        client_conn:send("success")
                end
            end)
            conn:on("sent", function(client_conn)
                    client_conn:close()
                    collectgarbage()
                end)
            end)