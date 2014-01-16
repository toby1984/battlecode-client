package battlecode.client.viewer.render;

import battlecode.client.viewer.AbstractDrawObject.RobotInfo;
import java.awt.BasicStroke;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.Shape;
import java.awt.Stroke;
import java.awt.geom.AffineTransform;
import java.awt.geom.Arc2D;
import java.awt.geom.Ellipse2D;
import java.awt.geom.Line2D;
import java.awt.geom.Rectangle2D;
import java.awt.image.BufferedImage;
import java.awt.image.RescaleOp;
import java.io.File;

import battlecode.client.util.ImageFile;
import battlecode.client.util.ImageResource;
import battlecode.client.viewer.AbstractAnimation;
import battlecode.client.viewer.AbstractDrawObject;
import battlecode.client.viewer.ActionType;
import battlecode.common.GameConstants;
import battlecode.common.MapLocation;
import battlecode.common.RobotLevel;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.Upgrade;
import battlecode.common.Direction;

import java.util.ArrayList;

import static battlecode.client.viewer.AbstractAnimation.AnimationType.*;

class DrawObject extends AbstractDrawObject<Animation> {

  private static final double diagonalFactor = Math.sqrt(2);
  private static final Stroke thinStroke = new BasicStroke(0.05f);
  private static final Stroke mediumStroke = new BasicStroke(0.075f);
  private static final Stroke thickStroke = new BasicStroke(0.1f);
  private static final Stroke broadcastStroke = thinStroke;
  private static final Stroke attackStroke = mediumStroke;
  private static final Color tintTeamA = new Color(1, 0, 0, 0.125f);
  private static final Color tintTeamB = new Color(0, 0, 1, 0.125f);
//	private static final Color regenColor = new Color(0.f,.6f,0.f);
  private static final Stroke outlineStroke = new BasicStroke(0.10f, BasicStroke.CAP_SQUARE, BasicStroke.JOIN_MITER, 10.0f, new float[]{0.5f, 0.5f}, 0.25f);
  private static final Shape outline = new Rectangle2D.Float(0, 0, 1, 1);
  private static final RescaleOp oneHalf = new RescaleOp(new float[]{1f, 1f, 1f, .5f}, new float[4], null);
  private static final ImageResource<RobotInfo> ir = new ImageResource<RobotInfo>();
  private static final ImageResource<String> cir = new ImageResource<String>();
  private static final ImageFile crosshair = new ImageFile("art/crosshair.png");
  private static final ImageFile crosshairBlue = new ImageFile("art/crosshair2.png");
  private static final ImageFile hatchSensor = new ImageFile("art/hatch_sensor.png");
  private static final ImageFile hatchAttack = new ImageFile("art/hatch_attack.png");
  private ImageFile img;
  private ImageFile preEvolve;
  public static final AbstractAnimation.AnimationType[] preDrawOrder = new AbstractAnimation.AnimationType[]{TELEPORT};
  public static final AbstractAnimation.AnimationType[] postDrawOrder = new AbstractAnimation.AnimationType[]{MORTAR_ATTACK, MORTAR_EXPLOSION, ENERGON_TRANSFER};
  private int teleportRounds;
  private MapLocation teleportLoc;
  private RobotType rtype = null;
  private static final double medbayRadius = 0;//= Math.sqrt(RobotType.MEDBAY.attackRadiusMaxSquared);
  private static final double shieldsRadius = 0;//Math.sqrt(RobotType.SHIELDS.attackRadiusMaxSquared);
  private static final double soldierRadius = 0;//Math.sqrt(RobotType.SOLDIER.attackRadiusMaxSquared);
  private static final double artilleryRadius = 0;//Math.sqrt(GameConstants.ARTILLERY_SPLASH_RADIUS_SQUARED);
  private static final Color shieldColor = new Color(150,150,255,150);
  private static final Color regenColor = new Color(150,255,150,150);
  private final DrawState overallstate;
    
  public static final ImageFile[] hatImages;
    
  static {
    File[] files = (new File("art/hats/")).listFiles();
    hatImages = new ImageFile[files.length];
    for (int x=0; x<files.length; x++)
      hatImages[x] = new ImageFile(files[x].getAbsolutePath());
  }
    

  public DrawObject(RobotType type, Team team, int id, DrawState state) {
    super(type, team, id);
    img = preEvolve = ir.getResource(info, getAvatarPath(info));
    maxEnergon = type.maxHealth;
    rtype = type;
    overallstate = state;
  }


  public DrawObject(DrawObject copy) {
    super(copy);
    img = copy.img;
    preEvolve = copy.preEvolve;
    maxEnergon = copy.maxEnergon;
    preEvolve = copy.preEvolve;
    teleportRounds = copy.teleportRounds;
    teleportLoc = copy.teleportLoc;
    rtype = copy.rtype;
    if (animations.containsKey(ENERGON_TRANSFER)) {
      EnergonTransferAnim a = (EnergonTransferAnim) animations.get(ENERGON_TRANSFER);
      a.setSource(this);
    }
    overallstate = copy.overallstate;
  }

  public static void loadAll() {
    for (RobotType type : RobotType.values()) {
      for (Team team : Team.values()) {
        /*if (team == Team.NEUTRAL) {
          continue;
          }*/
        RobotInfo robotInfo = new RobotInfo(type, team);
        ir.getResource(robotInfo, getAvatarPath(robotInfo));
      }
    }
  }

  private static String getAvatarPath(RobotInfo ri) {
    return getAvatarPath(ri.type.toString().toLowerCase(), ri.team);

  }

  private static String getAvatarPath(String type, Team team) {
    return "art/" + type + (team == Team.NEUTRAL ? "0" : (team == Team.A ? "1" : "2")) + ".png";
  }

  public double getRelativeSize() {
    return 1.;
    //return this.getType().relativeSize();
  }
    
  private int getViewRange() {
    return info.type.sensorRadiusSquared;
  }

  public void drawRangeHatch(Graphics2D g2) {
    AffineTransform pushed = g2.getTransform();
    final int viewrange = getViewRange();
    {
      g2.translate(loc.x, loc.y);
      try {
        BufferedImage sensorImg = hatchSensor.image;
        BufferedImage attackImg = hatchAttack.image;
        for (int i = -11; i <= 11; i++) for (int j = -11; j <= 11; j++) {
            int distSq = i * i + j * j;
            if (distSq <= viewrange) {
              if (inAngleRange(i, j, info.type.sensorCosHalfTheta)) {
                AffineTransform trans = AffineTransform.getTranslateInstance(i, j);
                trans.scale(1.0 / sensorImg.getWidth(), 1.0 / sensorImg.getHeight());
                g2.drawImage(sensorImg, trans, null);
              }
            }
            if ((info.type.canAttack)
                && info.type.attackRadiusMinSquared <= distSq
                && distSq <= info.type.attackRadiusMaxSquared
                && inAngleRange(i, j, info.type.attackCosHalfTheta)) {
              AffineTransform trans = AffineTransform.getTranslateInstance(i, j);
              trans.scale(1.0 / attackImg.getWidth(), 1.0 / attackImg.getHeight());
              g2.drawImage(attackImg, trans, null);
            }
          }
      } catch (NullPointerException npe) {
      } // oh well
    }
    g2.setTransform(pushed);
  }

  private static MapLocation origin = new MapLocation(0, 0);

  private boolean inAngleRange(int dx, int dy, double cosHalfTheta) {
    MapLocation dirVec = origin.add(dir);
    int a = dirVec.x;
    int b = dirVec.y;
    int dotProd = a * dx + b * dy;
    if (dotProd < 0) {
      if (cosHalfTheta > 0) {
        return false;
      }
    } else if (cosHalfTheta < 0) {
      return true;
    }
    double rhs = cosHalfTheta * cosHalfTheta * (dx * dx + dy * dy) * (a * a + b * b);
    if (dotProd < 0) {
      return (dotProd * dotProd <= rhs + 0.00001d);
    } else {
      return (dotProd * dotProd >= rhs - 0.00001d);
    }
  }

  public void draw(Graphics2D g2, boolean focused) {

    if (RenderConfiguration.showRangeHatch() && focused) {
      drawRangeHatch(g2);
    }

    AffineTransform pushed = g2.getTransform();
    { // push
      g2.translate(getDrawX(), getDrawY());
      drawImmediate(g2, focused);

      if (broadcast != 0x00 && RenderConfiguration.showBroadcast()) {
        g2.setStroke(broadcastStroke);
        double drdR = visualBroadcastRadius * 0.05; // dradius/dRound
        for (int i = 0; i < 20; i++) {
          if ((broadcast & (1 << i)) != 0x00) {
            double r = i * drdR;
            g2.setColor(new Color(1, 0, 1, 0.05f * (20 - i)));
            g2.draw(new Ellipse2D.Double(0.5 - r, 0.5 - r, 2 * r, 2 * r));
          }
        }
      }

//			if(regen > 0 && RenderConfiguration.showSpawnRadii()) {
//				g2.setStroke(broadcastStroke);
//				g2.setColor(regenColor);
//				g2.draw(new Ellipse2D.Double(.5-regenRadius,.5-regenRadius,2*regenRadius,2*regenRadius));
//			}
    }
    g2.setTransform(pushed); // pop
    // these animations shouldn't be drawn in the HUD, and they expect
    // the origin of the Graphics2D to be the MapLocation (0,0)
    for (AbstractAnimation.AnimationType type : postDrawOrder) {
      if (type.shown() && animations.containsKey(type)) {
        animations.get(type).draw(g2);
      }
    }
    drawAction(g2);
  }

  public void drawImmediate(Graphics2D g2, boolean drawOutline, boolean isHUD) {
    Color c = getTeam() == Team.A ? Color.RED : Color.BLUE;
    c = c.brighter().brighter().brighter();
    // these animations should be drawn in the HUD, and they expect
    // the origin of the Grpahics2D to be this robot's position
    for (AbstractAnimation.AnimationType type : preDrawOrder) {
      if (type.shown() && animations.containsKey(type)) {

        animations.get(type).draw(g2);
      }
    }

    if (animations.containsKey(DEATH_EXPLOSION)) {
      if (DEATH_EXPLOSION.shown() || isHUD) {
        Animation deathExplosion = animations.get(DEATH_EXPLOSION);
        if (deathExplosion.isAlive()) {
          deathExplosion.draw(g2);
        }
      }
    } else {

      boolean showEnergon = RenderConfiguration.showEnergon() || drawOutline;
      boolean showFlux = (RenderConfiguration.showFlux() || drawOutline);

      if (showEnergon) {
        Rectangle2D.Float rect = new Rectangle2D.Float(0, 1, 1, 0.15f);
        g2.setColor(Color.BLACK);
        g2.fill(rect);
        float frac = Math.min((float) (energon / maxEnergon), 1);
        rect.width = frac;
        if (frac < 0)
          frac = 0;
        if (turnedOn) {
          g2.setColor(new Color(Math.min(1 - 0.5f * frac, 1.5f - 1.5f * frac),
                                Math.min(1.5f * frac, 0.5f + 0.5f * frac), 0));
        } else {
          g2.setColor(new Color(.5f - .5f * frac, .5f - .5f * frac, .5f + .5f * frac));
        }
        g2.fill(rect);
                
        // drawing shields
        {
          frac = Math.min((float)(shields/maxEnergon), 1);
          rect = new Rectangle2D.Float(0, 1, 1.0f, 0.075f);
          rect.width = frac;
                	
          if (frac < 0)
            frac = 0;
          g2.setColor(new Color(.5f * frac, .5f * frac, .5f + .5f * frac));
          g2.fill(rect);
        }
      }

//			if(showFlux) {
//			    Rectangle2D.Float rect;
//				if(showEnergon)
//					rect = new Rectangle2D.Float(0, 1.15f, 1, 0.15f);
//				else
//					rect = new Rectangle2D.Float(0, 1, 1, 0.15f);
//                g2.setColor(Color.BLACK);
//                g2.fill(rect);
//                float frac = Math.min((float) (flux / info.type.maxFlux), 1);frac = Math.min((float) (energon / maxEnergon), 1);
//                rect.width = frac;
//                if (frac < 0)
//                    frac = 0;
//                g2.setColor(new Color(frac,0,.5f+.5f*frac));
//                g2.fill(rect);
//			}
			
      // actions
      if (actionAction != null && actionAction != ActionType.IDLE) {
        Rectangle2D.Float rect;
        if(showEnergon)
          rect = new Rectangle2D.Float(0, 1.15f, 1, 0.15f);
        else
          rect = new Rectangle2D.Float(0, 1, 1, 0.15f);
        g2.setColor(Color.BLACK);
        g2.fill(rect);
        float frac = Math.min(1-((float)roundsUntilActionIdle / Math.max(totalActionRounds,1)), 1);
        if (totalActionRounds == 0)
          frac = 1;
        rect.width = frac;
        if (frac < 0)
          frac = 0;
        switch (actionAction)
        {
        case MINING:			g2.setColor(new Color(1.0f, 0, 0.8f)); 		break;
        case MININGSTOPPING: 	g2.setColor(new Color(1.0f, 0.0f, 0.0f)); 	break;
        case DEFUSING: 			g2.setColor(Color.cyan); 					break;
        case CAPTURING: 		g2.setColor(new Color(0.3f, 0.3f, 1.0f)); 	break;
        default:;
        }
//                g2.setColor(new Color(frac,0,.5f+.5f*frac));
        g2.fill(rect);
      }

       // could be used for rotations or such, remember origin for rotation
      AffineTransform trans = new AffineTransform();

      assert preEvolve != null;
      BufferedImage image = getTypeSprite();
      // load soldier from a horizontal sprite sheet
      if (getType() == RobotType.SOLDIER) {
        // sprite sheet is East 0, clockwise
        // direction sheet is North 0, clockwise
        int sheetIndex = (dir.ordinal() - Direction.EAST.ordinal() + 8) % 8;
        int soldierHeight = image.getHeight();
        if (!isAttacking()) {
          sheetIndex += 8;
        }
        image = image.getSubimage(sheetIndex * soldierHeight, 0,
                                  soldierHeight, soldierHeight);
      }
      
      if (image != null) {
        if (isHUD) {
          trans.scale(1.0 / image.getWidth(), 1.0 / image.getHeight());
        } else {
          trans.scale((1.0 / image.getWidth()) * this.getRelativeSize(), (1.0 / image.getHeight()) * this.getRelativeSize());
        }
        
        //PASTR capture ranges
        if (getType() == RobotType.PASTR) {
          g2.setColor(c);
          g2.setStroke(broadcastStroke);
          int size = (int)(Math.pow(GameConstants.PASTR_RANGE, .5) * 2);
          g2.draw(new Ellipse2D.Float(-.5f * (size - 1), -.5f * (size - 1), size, size));
        }
        
        g2.drawImage(image, trans, null);

        // hats
        if (RenderConfiguration.showHats()) {
          double hatscale = 1.5;
          AffineTransform pushed = g2.getTransform();
          g2.translate((2.0-hatscale)/4.0, 0.2);
          double width = image.getWidth();
          trans = AffineTransform.getScaleInstance(hatscale/image.getWidth() * this.getRelativeSize(), hatscale/image.getWidth() * this.getRelativeSize());
          for (int x=0; x<hats.length(); x++)
          {
                		
            image = hatImages[(int)hats.charAt(x)].image;
            g2.translate(0, -hatscale/width*(image.getHeight()-2));
            g2.drawImage(image, trans, null);
                		
          }
          g2.setTransform(pushed);
        }
      } else {
        //System.out.println("null image in DrawObject.drawImmediate");
      }
            
      if ( (RenderConfiguration.showActionLines() || drawOutline) && getType() == RobotType.SOLDIER)
      {
        if (movementAction == ActionType.MOVING)
        {
          g2.setColor(c);
          g2.setStroke(thickStroke);
          g2.draw(new Line2D.Double(0.5, 0.5,
                                    0.5 - dir.dx, 0.5 - dir.dy));
        }
        if (targetLoc!=null && actionAction==ActionType.DEFUSING)
        {
          g2.setColor(Color.cyan);
          g2.setStroke(mediumStroke);
          g2.draw(new Line2D.Double(0.5, 0.5, targetLoc.x-loc.x+0.5, targetLoc.y-loc.y+0.5));
        }
      }
    }

    if (drawOutline) {
      g2.setColor(Color.YELLOW);
      g2.setStroke(outlineStroke);
      g2.draw(outline);
    }

  }

  public void drawImmediate(Graphics2D g2, boolean drawOutline) {
    drawImmediate(g2, drawOutline, false);
  }

  // used by the HUD
  public void drawImmediateNoScale(Graphics2D g2, boolean drawOutline) {
    drawImmediate(g2, drawOutline, true);
  }

  private boolean isAttacking() {
    return roundsUntilAttackIdle>0 || attackAction == ActionType.ATTACKING;
  }

  private void drawAction(Graphics2D g2) {
    if (isAttacking() && RenderConfiguration.showAttack())
    {
      g2.setColor(getTeam() == Team.A ? Color.RED : Color.BLUE);
      g2.setStroke(mediumStroke);
            
      if (rtype == null)
      {
        if(targetLoc==null) {
          // scorcher
//    				g2.draw(new Arc2D.Double(getDrawX()-scorcherRadius+.5,getDrawY()-scorcherRadius+.5,2*scorcherRadius,2*scorcherRadius,90-RobotType.SCORCHER.attackAngle/2.+attackDir.ordinal()*(-45),RobotType.SCORCHER.attackAngle,Arc2D.PIE));
        } else {
          BufferedImage target;
          if (getTeam() == Team.A) {
            target = crosshair.image;
          } else {
            target = crosshairBlue.image;
          }
          if (target != null) {
            AffineTransform trans = AffineTransform.getTranslateInstance(targetLoc.x, targetLoc.y);
            trans.scale(1.0 / target.getWidth(), 1.0 / target.getHeight());
            g2.drawImage(target, trans, null);
          }

          g2.draw(new Line2D.Double(getDrawX() + 0.5, getDrawY() + 0.5,
                                    targetLoc.x + 0.5, targetLoc.y + 0.5));
        }
      } else {
        switch (rtype) {
        case NOISETOWER:
        case SOLDIER:
          //g2.draw(new Ellipse2D.Double(getDrawX()+.5-soldierRadius,getDrawY()+.5-soldierRadius,2*soldierRadius,2*soldierRadius));
          g2.draw(new Line2D.Double(getDrawX() + 0.5, getDrawY() + 0.5,
                                    targetLoc.x + 0.5, targetLoc.y + 0.5));

          break;
        case HQ:
          if (true)//roundsUntilAttackIdle == RobotType.ARTILLERY.attackDelay-1)
          {
            BufferedImage target;
            if (getTeam() == Team.A) {
              target = crosshair.image;
            } else {
              target = crosshairBlue.image;
            }
            if (target != null) {
              AffineTransform trans = AffineTransform.getTranslateInstance(targetLoc.x, targetLoc.y);
              trans.scale(1.0 / target.getWidth(), 1.0 / target.getHeight());
              g2.drawImage(target, trans, null);
            }

            g2.draw(new Line2D.Double(getDrawX() + 0.5, getDrawY() + 0.5,
                                      targetLoc.x + 0.5, targetLoc.y + 0.5));
            g2.draw(new Ellipse2D.Double(targetLoc.x+.5-artilleryRadius,targetLoc.y+.5-artilleryRadius,2*artilleryRadius,2*artilleryRadius));
          }
            		
          break;
        }
      }
            
			
    }

  }

  private BufferedImage getTypeSprite() {
    /*
      if (action == ActionType.TRANSFORMING) {

      return (roundsUntilIdle/4 % 2 == 0 ? preEvolve.image : img.image);
      }
    */
    return img.image;
  }

  public void evolve(RobotType type) {
    super.evolve(type);
    img = ir.getResource(info, getAvatarPath(info));
  }

  public void setTeam(Team team) {
    super.setTeam(team);
    img = ir.getResource(info, getAvatarPath(info));
  }

  public void setMaxEnergon(double maxEnergon) {
    this.maxEnergon = maxEnergon;
  }

  public TeleportAnim createTeleportAnim(MapLocation src, MapLocation loc) {
    return new TeleportAnim(src, loc);
  }

  public ExplosionAnim createDeathExplosionAnim(boolean isArchon) {
    return new ExplosionAnim();
  }

  public MortarAttackAnim createMortarAttackAnim(MapLocation target) {
    return new MortarAttackAnim(loc, target);
  }

  public EnergonTransferAnim createEnergonTransferAnim(MapLocation loc, RobotLevel height, float amt, boolean isFlux) {
    return new EnergonTransferAnim(this, loc, amt, isFlux);
  }

  public ExplosionAnim createMortarExplosionAnim(Animation mortarAttackAnim) {
    ExplosionAnim anim = new ExplosionAnim(((MortarAttackAnim) mortarAttackAnim).getTargetLoc(), 1.8);
    anim.setExplosionToggle(ExplosionAnim.ExplosionToggle.DETONATES);
    return anim;
  }

  public void updateRound() {
    super.updateRound();

    if (teleportRounds > 0) {
      teleportRounds--;
      if (teleportLoc == null && teleportRounds % 3 == 2) {
        dir = dir.rotateRight().rotateRight();
      }
    }
  }

  public void activateTeleporter() {
    teleportRounds = 1;
  }

  public void activateTeleport(MapLocation teleportLoc) {
    teleportRounds = 1;
    this.teleportLoc = teleportLoc;
  }
}
