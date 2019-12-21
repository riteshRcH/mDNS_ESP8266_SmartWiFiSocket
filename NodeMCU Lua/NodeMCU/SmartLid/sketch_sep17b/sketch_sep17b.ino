volatile int StartMeasurement = 6;
volatile int EchoPin = 7;
volatile unsigned long startPulse = 0;
volatile unsigned long endPulse = 0;

int attemptDistanceMeasurementOnce(void);
int attemptDistanceUntilSuccess(void);
void ultrasonicISR(void);
double AverageDistance ;

void setup() {
pinMode(StartMeasurement, OUTPUT);
pinMode(EchoPin, INPUT);
Serial.begin(9600);
}

void loop() {
int duration = 0;
float distanceInches = 0.0;

// get a distance measurement (might have to retry a few times -- hardware has been inconsistent)
duration = attemptDistanceUntilSuccess();

Serial.print("Duration in microseconds: ");
Serial.println(duration); 

// empirical conversion, your sensor may be different !
distanceInches = duration / 127.000;

AverageDistance = (0.95 * AverageDistance) + (0.05 * distanceInches);  // 20 shot rolling average

Serial.print("Distance in inches: ");
Serial.println(distanceInches); 
Serial.println(" "); 

Serial.print("Average in inches: ");
Serial.println(AverageDistance); 
Serial.println(" "); 

// make a new measurement about 4 times per second
delay(250);
}

int attemptDistanceUntilSuccess()
{
int duration;
int attempts = 1;

while(attempts < 10) {
    Serial.println("attempting");
    duration = attemptDistanceMeasurementOnce();
    if(duration > 0 && duration < 60000) {
        break;
    }
    // wait a short time before attempting again -- the primary failure mode is very slow - 187 ms echo pulse
    delay(200);
}

return duration;
}
int attemptDistanceMeasurementOnce(void)
{
int duration;

endPulse = startPulse = 0;
attachInterrupt(EchoPin, ultrasonicISR, CHANGE);

// pulse the sensor to make it get a distance reading
digitalWrite(StartMeasurement, HIGH);
delay(5);
digitalWrite(StartMeasurement, LOW);

// wait while we get both up and down edges of pulse (and interrupts)
// the interrupt service routine sets our start and end "times" in microseconds
delay(20);
duration = endPulse - startPulse;

detachInterrupt(EchoPin);
return duration;
}

void ultrasonicISR(void)
{
if(digitalRead(EchoPin) == HIGH)
startPulse = micros();
else
endPulse = micros();
}
