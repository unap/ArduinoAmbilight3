import processing.core.*; 
import processing.data.*; 
import processing.event.*; 
import processing.opengl.*; 

import processing.serial.*; 
import java.awt.AWTException; 
import java.awt.Color; 
import java.awt.Graphics; 
import java.awt.HeadlessException; 
import java.awt.Rectangle; 
import java.awt.Robot; 
import java.awt.Dimension; 
import java.awt.Toolkit; 
import java.awt.event.HierarchyEvent; 
import java.awt.event.HierarchyListener; 
import java.awt.image.BufferedImage; 
import javax.imageio.ImageIO; 
import javax.swing.JFrame; 
import javax.swing.JButton; 
import java.awt.event.ActionEvent; 
import java.awt.event.ActionListener; 
import java.awt.event.ComponentAdapter; 
import java.awt.event.ComponentEvent; 

import java.util.HashMap; 
import java.util.ArrayList; 
import java.io.File; 
import java.io.BufferedReader; 
import java.io.PrintWriter; 
import java.io.InputStream; 
import java.io.OutputStream; 
import java.io.IOException; 

public class ScreenColor extends PApplet {





















Serial port;
Robot robby;
Rectangle screenRect;

// Number of leds on different sides of screen
int topLedCount = 32;
int sideLedCount = 21;
int botLedCount = 7;

// total number of leds
int ledCount = topLedCount + (2*sideLedCount) + (2*botLedCount);

byte[] serialData = new byte[ledCount*3 +1];

// sreenshot scaled size
int imgWidth = 384;
int imgHeight = 240;

// led brightness 0-1
double ledBrightness = .8f;

// vertical/horizontal size of screen for one led
int zoneWidth = imgWidth/topLedCount;
int zoneHeight = imgHeight/sideLedCount;
// opposite dimension
int zoneSize = imgHeight/6;

Boolean running;

BufferedImage image;
Graphics graphics;
DisposeHandler dh;

/*
 *   SETUP
 */
public void setup() {
  noStroke();
  // we need a dispose handler to shut off the lights on exit
  //FIXME: isn't run on exit when exported, don't know why
  dh = new DisposeHandler(this);
  port = new Serial(this, Serial.list()[0],115200); //set baud rate
  size(240, 150);
  if (frame != null) {
    frame.setResizable(true);
  }
  running = true;
  //standard Robot class error check
  try {
    robby = new Robot();
  }
  catch (AWTException e){
    //println("Robot class not supported by your system!");
    exit();
  }
  
  image = new BufferedImage(imgWidth, imgHeight, BufferedImage.TYPE_INT_RGB);
  graphics = image.createGraphics();
  
  //Default screenshot bounds to fullscreen.
  Dimension scrSize = Toolkit.getDefaultToolkit().getScreenSize();
  screenRect = new Rectangle(0, 0, scrSize.width-5, scrSize.height-5); // i can't remember why i have the -5 but i'm not changing it
  
  //Make window have a minimum height
  frame.addComponentListener(new ComponentAdapter() {
    public void componentResized(ComponentEvent e) {
      int minHeight = 50;
      if (e.getSource() == frame) {
        if (frame.getHeight() < minHeight) {
          frame.setSize(frame.getWidth(), minHeight);
        }
      }  
    }
  });
  
}

/*
 *   Calculate average color of area from image
 */
public int[] areaAvgColors(BufferedImage img, int startX, int startY, int w, int h) {
  int rgb[] = {0, 0, 0};

  // Read pixel colors
  int[] colors = img.getRGB(startX, startY, w, h, null, 0, w);

  for (int clr = 0; clr < colors.length; clr++) {
    rgb[0] += colors[clr] >> 16 & 0xff;
    rgb[1] += colors[clr] >> 8 & 0xff;
    rgb[2] += colors[clr] & 0xff;
  }

  // Calculate average
  rgb[0] = rgb[0] / (w*h);
  rgb[1] = rgb[1] / (w*h);
  rgb[2] = rgb[2] / (w*h);

  return rgb;
}

/*
 *   Get average colors of side regions of screen
 */
public int[][] screenAvgColors() {
  int red = 0, blue = 0, green = 0;
  
  // Take screenshot and scale it
  try {
    BufferedImage capture = robby.createScreenCapture(screenRect);
    graphics.drawImage(capture, 0, 0, imgWidth, imgHeight, null);
    //g.dispose();
    //ImageIO.write(image, "png", new File("scrnshot.png"));
  }
  catch (Exception e) {
    //e.printStackTrace();
  }

  // total ledCount leds
  int[][] colors = new int[ledCount][3];

  // leds on bottom right, starting from bottom middle
  for (int i = 0; i < botLedCount; i++) {
    colors[i] = areaAvgColors(image, (imgWidth-(zoneWidth*(botLedCount-i))), (imgHeight-zoneSize), zoneWidth, zoneSize);
  }

  // leds on right side, starting from bottom right corner
  for (int i = 0; i < sideLedCount; i++) {
    colors[i+botLedCount] = areaAvgColors(image, (imgWidth - zoneSize), (imgHeight - zoneHeight*(i+1)), zoneSize, zoneHeight);
  }

  // leds on top, starting from top right corner
  for (int i = 0; i < topLedCount; i++) {
    colors[i+botLedCount+sideLedCount] = areaAvgColors(image, (imgWidth - zoneWidth*(i+1)), 0, zoneWidth, zoneSize);
  }

  // leds on left side, starting from top left corner
  for (int i = 0; i < sideLedCount; i++) {
    colors[i+botLedCount+sideLedCount+topLedCount] = areaAvgColors(image, 0, zoneHeight*(i), zoneSize, zoneHeight);
  }

  // leds on bottom left, starting from bottom left
  for (int i = 0; i < botLedCount; i++) {
    colors[i+botLedCount+(2*sideLedCount)+topLedCount] = areaAvgColors(image, i*zoneWidth, (imgHeight-zoneSize), zoneWidth, zoneSize);
  }

  return colors;
}


/*
 *   DRAW 
 */
public void draw() {
  background(0, 0, 0);
    
  int[][] clrs = screenAvgColors();

  int boxSize = height/7;
  
  serialData[0] = (byte)0xff;
  int z = 1;
  
  // Add colors to array
  int[] colors = new int[ledCount];
  for(int i = 0; i < colors.length; i++)
  {
    colors[i] = color(clrs[i][0], clrs[i][1], clrs[i][2]);
    serialData[z++] = (byte) (clrs[i][0]*ledBrightness);
    serialData[z++] = (byte) (clrs[i][1]*ledBrightness);
    serialData[z++] = (byte) (clrs[i][2]*ledBrightness);
  }

  // Draw the colors in the window
  
  // leds on right side, starting from bottom right corner
  for (int i = 0; i < sideLedCount; i++) {
    fill(colors[i+botLedCount]);
    rect(width - boxSize, (height - (height/(float)sideLedCount)*(i+1)), boxSize, (height/sideLedCount));
  }

  // leds on left side, starting from top left corner
  for (int i = 0; i < sideLedCount; i++) {
    fill(colors[i+botLedCount+sideLedCount+topLedCount]);
    rect(0, (height/(float)sideLedCount)*(i), boxSize, (height/sideLedCount));
  }

  // leds on top, starting from top right corner
  for (int i = 0; i < topLedCount; i++) {
    fill(colors[i+botLedCount+sideLedCount]);
    rect((width - (width/(float)topLedCount)*(i+1)), 0, (width/topLedCount), boxSize);
  }

  // leds on bottom right, starting from bottom middle
  for (int i = 0; i < botLedCount; i++) {
    fill(colors[i]);
    rect(width-((width/(float)topLedCount)*(botLedCount-i)), (height-boxSize), (width/topLedCount), boxSize);
  }

  // leds on bottom left, starting from bottom left
  for (int i = 0; i < botLedCount; i++) {
    fill(colors[i+botLedCount+(2*sideLedCount)+topLedCount]);
    rect(i*(width/(float)topLedCount), (height-boxSize), (width/topLedCount), boxSize);
  }
  
  
  // Send colors to Arduino
  port.write(serialData);

}

/*
 *   If delete is pressed present area resizing window.
 *   If ESC is pressed set "running" to false.
 */
public void keyPressed() {
  if (key == DELETE) {
    final JFrame sizingFrame = new JFrame("Resize this window to cover image and press button to set image bounds");
    sizingFrame.setPreferredSize(new Dimension(400, 300));
    JButton button = new JButton("Set image bounds!");
    
    // probably overkill stuff to get size but i cant do it in processing
    button.addActionListener(new ActionListener() {
      @Override
      public void actionPerformed(ActionEvent e) {
          // Get frame bounds
          screenRect = sizingFrame.getBounds();
          sizingFrame.dispose();

      }
    });
    
    sizingFrame.add(button);
    sizingFrame.pack();
    sizingFrame.setVisible(true);
    
  }
  else if (key == ESC) { // stop running on ESC
    key = 0;
    if (running) {
      // make window black
      fill(0);
      rect(0, 0, width, height);
      
      // add text
      fill(255, 0, 0);
      textSize(32);
      textAlign(CENTER, CENTER);
      text("OFF", width/2, height/2);
      
      sendBlack();
      
      running = false;
      noLoop();
    }
    else {
      running = true;
      loop();
    }
  }
}

public void sendBlack() {
  serialData[0] = (byte)0xff;
  for (int i = 1; i < ledCount*3+1; i++) {
    serialData[i] = 0; 
  }
  // send a few times to prevent off sync
  for (int i = 0; i < 5; i++){
    port.write(serialData);
    delay(20);
  }
}

/*
 * Shut off lights before exiting
 */
public class DisposeHandler {
   
  DisposeHandler(PApplet pa)
  {
    pa.registerMethod("dispose", this);
  }
   
  public void dispose()
  {
    sendBlack();
  }
}
  static public void main(String[] passedArgs) {
    String[] appletArgs = new String[] { "ScreenColor" };
    if (passedArgs != null) {
      PApplet.main(concat(appletArgs, passedArgs));
    } else {
      PApplet.main(appletArgs);
    }
  }
}
