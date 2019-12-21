NodeMCU firmware Pin|ESP8266 GPIO Pin-Component Pin
Vcc side(relay indicator LED and relay)
0|16-R
5|14-G
6|12-B
7|13-relay
GND side(setup LED and touch button)
1|5-R
2|4-G
3|0-B
8|15-Touch Btn Input

gpio.mode(0, gpio.OUTPUT);gpio.mode(5, gpio.OUTPUT);gpio.mode(6, gpio.OUTPUT);gpio.mode(7, gpio.OUTPUT)
gpio.mode(1, gpio.OUTPUT);gpio.mode(2, gpio.OUTPUT);gpio.mode(3, gpio.OUTPUT);
gpio.mode(8, gpio.INT); gpio.trig(8, "up", function(level, when) print('heyy') end)