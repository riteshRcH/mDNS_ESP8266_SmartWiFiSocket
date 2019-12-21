--init_gpio_relay_pin()
--RELAY_OPIN = 8
gpio.mode(7, gpio.OUTPUT)
gpio.write(7, file.exists('restore_relay_on') and gpio.HIGH or gpio.LOW)

wifi.sta.disconnect()

dofile("SmartWiFiSocket.lc")