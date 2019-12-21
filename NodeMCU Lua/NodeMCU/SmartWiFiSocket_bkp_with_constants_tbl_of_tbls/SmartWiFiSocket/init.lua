--RELAY_OPIN = 3
gpio.mode(3, gpio.OUTPUT)
gpio.write(3, gpio.LOW)

dofile('SmartWiFiSocket.lua')