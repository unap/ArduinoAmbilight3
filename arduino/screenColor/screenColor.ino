/*
 * Reads rgb colours from serial and sets leds to those colours using pseudo PWM.
 *
 */
#include "FastLED.h"

// Datapin
#define PIN 6

// Number of leds
#define NUM_LEDS 92

// Array of leds
CRGB leds[NUM_LEDS];

// For checking time
unsigned long checkTime = millis();

int i, j, r, g, b;

// gamma correction lookup table for led brightness
unsigned int gamma[] =
{
  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,
  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  0,  1,  1,  1,  1,
  1,  1,  1,  1,  1,  1,  1,  1,  1,  2,  2,  2,  2,  2,  2,  2,
  2,  3,  3,  3,  3,  3,  3,  3,  4,  4,  4,  4,  4,  5,  5,  5,
  5,  6,  6,  6,  6,  7,  7,  7,  7,  8,  8,  8,  9,  9,  9, 10,
  10, 10, 11, 11, 11, 12, 12, 13, 13, 13, 14, 14, 15, 15, 16, 16,
  17, 17, 18, 18, 19, 19, 20, 20, 21, 21, 22, 22, 23, 24, 24, 25,
  25, 26, 27, 27, 28, 29, 29, 30, 31, 32, 32, 33, 34, 35, 35, 36,
  37, 38, 39, 39, 40, 41, 42, 43, 44, 45, 46, 47, 48, 49, 50, 50,
  51, 52, 54, 55, 56, 57, 58, 59, 60, 61, 62, 63, 64, 66, 67, 68,
  69, 70, 72, 73, 74, 75, 77, 78, 79, 81, 82, 83, 85, 86, 87, 89,
  90, 92, 93, 95, 96, 98, 99,101,102,104,105,107,109,110,112,114,
  115,117,119,120,122,124,126,127,129,131,133,135,137,138,140,142,
  144,146,148,150,152,154,156,158,160,162,164,167,169,171,173,175,
  177,180,182,184,186,189,191,193,196,198,200,203,205,208,210,213,
  215,218,220,223,225,228,231,233,236,239,241,244,247,249,252,255
};

void setup()
{
  FastLED.addLeds<NEOPIXEL, PIN>(leds, NUM_LEDS);
  Serial.begin(115200);
  // rainbow "cylon" on startup
  for (i = 0; i < NUM_LEDS; i++)
  {
    leds[i].setHue(i*(255/NUM_LEDS));
    FastLED.show();
    leds[i] = CRGB::Black;
    delay(5);
  }
  for (i = NUM_LEDS-1; i >= 0; i--)
  {
    leds[i].setHue(i*(255/NUM_LEDS));
    FastLED.show();
    leds[i] = CRGB::Black;
    delay(5);
  }
  FastLED.show();
}

void loop() 
{
  while (!Serial.available())
  {
    // Shut off lights if over 2 seconds since last data received
    if (millis() - checkTime > 2000)
    {
      for (i = 0; i < NUM_LEDS; i++)
      {
        leds[i] = CRGB::Black;
      }
      leds[0] = 0x2b0000;
      FastLED.show();
    }
  }
  // wait for the checkbyte
  if(0xff == Serial.read())
  {
    // read and set led colours 
    for (i = 0; i < NUM_LEDS; i++)
    {
      checkTime = millis();
      
      while (!Serial.available());
      leds[i].r = gamma[Serial.read()];
      
      // tune down green and blue in proportion to brightness to make color warmer
      while (!Serial.available());
      g = gamma[Serial.read()];
      leds[i].g = g;
      //leds[i].g = (int)((float)g*(-0.1/255*g+1));
      
      while (!Serial.available());
      b = gamma[Serial.read()];
      leds[i].b = b;
      //leds[i].b = (int)((float)b*(-0.15/255*b+1));
    }
    FastLED.show();
  }
}
