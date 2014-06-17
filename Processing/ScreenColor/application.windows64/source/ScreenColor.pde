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

Serial port;
Robot robby;
Rectangle screenRect;

// sreenshot scaled size
int imgWidth = 96;
int imgHeight = 60;

Boolean running;

BufferedImage image;
Graphics graphics;
DisposeHandler dh;

/*
 *   SETUP
 */
void setup() {
  noStroke();
  // we need a dispose handler to shut off the lights on exit
  //FIXME: isn't run on exit when exported, don't know why
  dh = new DisposeHandler(this);
  port = new Serial(this, Serial.list()[0],9600); //set baud rate
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
int[] areaAvgColors(BufferedImage img, int startX, int startY, int w, int h) {
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
int[][] screenAvgColors() {
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

  // Get colors of all regions 
  int[] topLeft = areaAvgColors(image, 0, 0, imgWidth/2, imgHeight/3);
  int[] topRight = areaAvgColors(image, imgWidth/2, 0, imgWidth/2, imgHeight/3);
  int[] left = areaAvgColors(image, 0, imgHeight/3, imgWidth/4, (imgHeight - imgHeight/3));
  int[] right = areaAvgColors(image, (imgWidth - imgWidth/4), imgHeight/3, imgWidth/4, (imgHeight - imgHeight/3));

  // Write colors into one array
  int[][] colors = new int[4][3];

  colors[0] = topLeft;
  colors[1] = topRight;
  colors[2] = left;
  colors[3] = right;

  return colors;
}


/*
 *   DRAW 
 */
void draw() {
  background(0, 0, 0);
    
  int[][] clrs = screenAvgColors();

  // Draw the rectangles in the window
  color tl = color(clrs[0][0], clrs[0][1], clrs[0][2]);
  color tr = color(clrs[1][0], clrs[1][1], clrs[1][2]);
  color l = color(clrs[2][0], clrs[2][1], clrs[2][2]);
  color r = color(clrs[3][0], clrs[3][1], clrs[3][2]);

  // left and right
  fill(l);
  rect(0, height/3, width/4, (height - height/3));
  fill(r);
  rect((width-width/4), height/3, width/4, (height - height/3));

  // top left and top right
  fill(tl);
  rect(0,0,width/2, height/3);
  fill(tr);
  rect(width/2, 0, width/2, height/3);

  // Send colors to Arduino, loop to reduce flickering caused by time overhead from calculating avg colors
  for (int z = 5; z >= 0; z--) {
    port.write(0xff); //marker for sync
    // all 12 values one after the other
    for (int[] clr : clrs) {
      for (int val : clr) {
        port.write(val);
        //print(val + " ");
      }
    }
  }
  
}

/*
 *   If delete is pressed present area resizing window.
 *   If ESC is pressed set "running" to false.
 */
void keyPressed() {
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
      
      // send black
      port.write(0xff);
      for (int i = 0; i < 12; i++) {
         port.write(0); 
      }
      running = false;
      noLoop();
    }
    else {
      running = true;
      loop();
    }
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
    port.write(0xff);    
    for (int i = 0; i < 12; i++) {
      port.write(0); 
    }
  }
}
