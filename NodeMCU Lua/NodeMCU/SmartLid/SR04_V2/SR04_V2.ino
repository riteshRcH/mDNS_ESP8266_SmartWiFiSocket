#define triggerPIN 11
#define echoPIN 12

int returnCM;                           // Variable containg processed sounding

//****************** SETUP **********************
void setup() {
  pinMode(triggerPIN, OUTPUT);          // Set the trigPin as an Output (sr04t or v2.0)
  //pinMode(echoPIN, INPUT);            // Set the echoPin as an Input (sr04t)
  pinMode(echoPIN,INPUT_PULLUP);        // Set the echoPin as an Input with pullup (sr04t or v2.0)
  Serial.begin(9600);
  // End of setup
}

//******************* LOOP **********************
void loop() {
  int distanceCM = 0;                     
  unsigned long durationMS = 0;           
  // Do sounding here
  distanceCM = 0;
  durationMS = 0;
  // Clear the trigger pin
  digitalWrite(triggerPIN, LOW);
  delayMicroseconds(2);
  // Sets the trigger on HIGH state for 10 micro seconds
  digitalWrite(triggerPIN, HIGH);
  delayMicroseconds(10);
  digitalWrite(triggerPIN, LOW);
  // wait for the echo
  durationMS = pulseIn(echoPIN, HIGH);
  // Calculating the distance
  distanceCM = (((int) durationMS * 0.034) / 2);
  // Prints the distance on the Serial Monitor
  Serial.print("Sample: ");
  Serial.println(distanceCM);
  // Pause between soundings at least60 ms
  delay(1000);
  // End of Main Loop
}

