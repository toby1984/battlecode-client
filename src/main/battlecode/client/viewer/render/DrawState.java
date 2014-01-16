package battlecode.client.viewer.render;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.geom.AffineTransform;
import java.awt.geom.Rectangle2D;
import java.awt.geom.Ellipse2D;
import java.awt.image.BufferedImage;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedList;
import java.util.List;
import java.util.Map;
import java.util.Map.Entry;

import battlecode.client.util.ImageFile;
import battlecode.client.viewer.AbstractDrawState;
import battlecode.client.viewer.DebugState;
import battlecode.client.viewer.FluxDepositState;
import battlecode.client.viewer.GameStateFactory;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.common.Upgrade;
import battlecode.common.GameConstants;
import battlecode.serial.RoundStats;
import battlecode.world.GameMap;

public class DrawState extends AbstractDrawState<DrawObject> {

	
  private static final ImageFile rNuker = new ImageFile("art/nuker1.png");
  private static final ImageFile rNuker2 = new ImageFile("art/nuker2.png");
  private static final ImageFile rNukeb = new ImageFile("art/nukeb1.png");
  private static final ImageFile rNukeb2 = new ImageFile("art/nukeb2.png");
  private static final ImageFile rexplode = new ImageFile("art/nukeexplode.png");
	
  protected static final Color dragShadow = new Color(0.5f, 0.5f, 0.5f, 0.5f);
  protected static final Color linkNone = new Color(0.f,0.f,0.f);
  protected static final Color linkA = new Color(1.f,0.f,0.f);
  protected static final Color linkB = new Color(0.f,0.f,1.f);
  protected static final Color linkBoth = new Color(.75f,0.f,.75f);
  protected static final ImageFile encampment = new ImageFile("art/encampment.png");

  private static class Factory implements GameStateFactory<DrawState> {

    public DrawState createState(GameMap map) {
      return new DrawState(map);
    }

    public DrawState cloneState(DrawState state) {
      return new DrawState(state);
    }

    public void copyState(DrawState src, DrawState dst) {
      dst.copyStateFrom(src);
    }
  }
  public static final GameStateFactory<DrawState> FACTORY = new Factory();
  //private Map<Integer, DrawObject> groundUnits;
  //private Map<Integer, DrawObject> airUnits;
  private List<DrawObject> towers;
  // these need to be drawn before all the units,
  // so don't draw them from DrawObjects
  //private ArrayList<TeleportAnim> teleportAnims;
  private MapLocation[][] convexHullsA, convexHullsB;
  // number of blocks in current draw state
  //int[][] blockNumber;

  public DrawState() {
    groundUnits = new LinkedHashMap<Integer, DrawObject>();
    airUnits = new LinkedHashMap<Integer, DrawObject>();
    encampments = new HashSet<MapLocation>();
    towers = new LinkedList<DrawObject>();
    fluxDeposits = new LinkedHashMap<Integer, FluxDepositState>();
    currentRound = -1;
    convexHullsA = new MapLocation[0][];
    convexHullsB = new MapLocation[0][];

  }

  private DrawState(GameMap map) {
    this();
    this.setGameMap(map);
  }

  private DrawState(DrawState clone) {
    this();
    copyStateFrom(clone);
  }

  protected DrawObject createDrawObject(RobotType type, Team team, int id) {
    return new DrawObject(type, team, id, this);
  }

  protected DrawObject createDrawObject(DrawObject o) {
    return new DrawObject(o);
  }

  public MapLocation[][] getConvexHullsA() {
    return convexHullsA;
  }

  public MapLocation[][] getConvexHullsB() {
    return convexHullsB;
  }
    
  public double getTeamResources(Team t) {
    return teamResources[t.ordinal()];
  }

  public double getResearchProgress(Team t, int i) {
    return researchProgress[t.ordinal()][i];
  }

  public synchronized void apply(RoundStats stats) {
      this.stats = stats;
    }

  public DrawObject getDrawObject(int id) {
    try {
      return getRobot(id);
    } catch (AssertionError e) {
      return null;
    }
  }

  private void drawDragged(Graphics2D g2, DebugState debug, DrawObject obj) {
    /*
      MapLocation loc = obj.getLocation();
      float dx = debug.getDX(), dy = debug.getDY();
      g2.setColor(dragShadow);
      g2.fill(new Rectangle2D.Float(Math.round(loc.x + dx),
      Math.round(loc.y + dy), 1, 1));
      AffineTransform pushed = g2.getTransform(); // push
      g2.translate(dx, dy);
      obj.draw(g2, true);
      g2.setTransform(pushed); // pop;
    */
  }

  /**
   * Draws the current game state. This method is always called from the
   * Swing event-dispatch thread, and in particular blocks calls to
   * updateRound.
   * @param g2 The graphics context, transformed to MapLocation-space
   * @param debug The debug state, including MapLocation-space mouse state
   */
  public synchronized void draw(Graphics2D g2, DebugState debug) {
      if (RenderConfiguration.showSpawnRadii()) {
        /*
          for (DrawObject tower : towers) {
          tower.drawSpawnRadius(g2);
          }
        */
      }
      int dragID = debug.getDragID();
      int focusID = debug.getFocusID();
      int hoverID = -1;
      MapLocation hoverLoc = null;
      long controlBits = 0;
      Iterable<Map.Entry<Integer, DrawObject>> drawableSet = getDrawableSet();

      if (drawableSet == null) {
        return;
      }

//		AffineTransform pushed2 = g2.getTransform();
		
      // Woohoo the power grid!
//		g2.setStroke(new BasicStroke(.15f));
//		g2.translate(.5,.5);
//		for (Link l : links) {
//			if(l.connected[0])
//				if(l.connected[1])
//					g2.setColor(linkBoth);
//				else
//					g2.setColor(linkA);
//			else
//				if(l.connected[1])
//					g2.setColor(linkB);
//				else
//					g2.setColor(linkNone);
//			g2.drawLine(l.from.x,l.from.y,l.to.x,l.to.y);
//		}
      

      // cow densities
      double maxDensity = 0.0;
      for (int i = 0; i < neutralsDensity.length; i++) {
        for (int j = 0; j < neutralsDensity.length; j++) {
          maxDensity = Math.max(maxDensity, neutralsDensity[i][j]);
        }
      }
      
      double thresholdDensity = Math.max(1 / (1 - GameConstants.NEUTRALS_TURN_DECAY), .1 * maxDensity);
      if (RenderConfiguration.threshCows()) {
        maxDensity -= thresholdDensity;
      }
      
      for (int i = 0; i < neutralsDensity.length && RenderConfiguration.showCows(); i++) {
        for (int j = 0; j < neutralsDensity[0].length; j++) {
          //obtain color by checking proximity to pastrs
          boolean harvBlue = false, harvRed = false;
          double density = (int)neutralsDensity[i][j];
          if(RenderConfiguration.threshCows()) {
            if(density < thresholdDensity) continue;
            else density -= thresholdDensity;
          }
          if (neutralsTeamSet) {
              switch (neutralsTeam[i][j]) {
                case 1: harvRed = true; break;
                case 2: harvBlue = true; break;
                case 3: harvRed = true; harvBlue = true;
                default: break;
              }
          } else {
              for (Map.Entry<Integer, DrawObject> gUnitEnt : groundUnits.entrySet())
              {
                DrawObject gUnit = gUnitEnt.getValue();
                double checkRadiusSqr = 0;
                if(gUnit.getType() == RobotType.SOLDIER) checkRadiusSqr = .1;
                else if(gUnit.getType() == RobotType.PASTR) checkRadiusSqr = Math.pow(GameConstants.PASTR_RANGE, 1);
                else continue;
                MapLocation groundLoc = gUnit.getLocation();
                //distance check
                double distSqrToRobot = Math.pow(groundLoc.x - i, 2) + Math.pow(groundLoc.y - j, 2);
                if(distSqrToRobot <= checkRadiusSqr) {
                  if (gUnit.getTeam() == Team.A) harvRed = true;
                  else harvBlue = true;
                  if(harvBlue && harvRed) continue;
                }
              }
          }
          float lum = (float)(.5 * density / maxDensity + .25);
          float r = harvRed ? lum : 0;
          float b = harvBlue ? lum : 0;
          float g = !(harvRed || harvBlue) ? lum : 0;
          g2.setColor(new Color(r, g, b, 1.0f));
          // cap at the max possible size
          float maxPossible = 2000;
          float size = (float) Math.min(Math.sqrt(density / maxPossible), 1.0f);
          // make appear at the center
          float offset = ((1.0f - size) / 2);
          g2.fill(new Rectangle2D.Float(i + offset, j + offset, size, size));
        }
      }

      /*
      for (Entry<MapLocation, Team> entry : mineLocs.entrySet()) {
        MapLocation loc = entry.getKey();
        Team team = entry.getValue();
		
        if (team == Team.A) g2.setColor(new Color(1.f,0.f,0.f,.5f));
        else if (team == Team.B) g2.setColor(new Color(0.f,0.f,1.f,.5f));
        else g2.setColor(new Color(0.1f, 0.1f, 0.1f, 0.5f));
		
        g2.fill(new Rectangle2D.Float(loc.x+0.1f, loc.y+0.1f, .9f, .9f));
      }
      */
      /*
      BufferedImage encampSprite = encampment.image;
      for (MapLocation m : getEncampmentLocations()) {
        AffineTransform trans = AffineTransform.getTranslateInstance(m.x, m.y);
        trans.scale(1.0 / encampSprite.getWidth(), 1.0 / encampSprite.getHeight());
        g2.drawImage(encampSprite, trans, null);
        g2.setColor(new Color(1.0f,0.0f,1.0f,1.0f));
      }
      */
        
//		g2.setColor(new Color(1.f,0.f,0.f,.5f));
//		MapLocation coreLoc = coreLocs.get(Team.A);
//		g2.fill(new Ellipse2D.Float(coreLoc.x-1,coreLoc.y-1,2,2));
//		
//		g2.setColor(new Color(0.f,0.f,1.f,.5f));
//		coreLoc = coreLocs.get(Team.B);
//		g2.fill(new Ellipse2D.Float(coreLoc.x-1,coreLoc.y-1,2,2));

//		g2.setTransform(pushed2);

      for (Map.Entry<Integer, DrawObject> entry : drawableSet) {

        int id = entry.getKey();
        DrawObject obj = entry.getValue();
        if(obj.inTransport()) continue;

        if (id == dragID) {
          drawDragged(g2, debug, obj);
        } else {
          if (Math.abs(debug.getX() - obj.getDrawX() - 0.5) < 0.5
              && Math.abs(debug.getY() - obj.getDrawY() - 0.5) < 0.5) {
            hoverID = id;
            hoverLoc = obj.getLocation();
            controlBits = obj.getControlBits();
          }
          obj.draw(g2, id == focusID || id == hoverID);
        }
      }
        
      AffineTransform pushed = g2.getTransform();
      for (Team t : new Team[]{Team.A, Team.B})
      {
        g2.setTransform(pushed);
        int research = (int)(1.000001*getResearchProgress(t, Upgrade.NUKE.ordinal())*Upgrade.NUKE.numRounds);
        if (research > 350 && research<370)
        {
          DrawObject o = getHQ(t);
          g2.translate(o.getDrawX(), o.getDrawY());
          g2.translate(0.0, -(research-350)*0.45);
          g2.translate(0.0, -1);
          BufferedImage bi = t==Team.A ? rNuker.image : rNukeb.image;
          AffineTransform trans = AffineTransform.getScaleInstance((1.0 / bi.getWidth()) * o.getRelativeSize(), (1.0 / bi.getWidth()) * o.getRelativeSize());
          g2.drawImage(bi, trans, null);
        } else if (research > 375)
        {
          DrawObject o = getHQ(t.opponent());
          g2.translate(o.getDrawX(), o.getDrawY());
          g2.translate(0.0, -(404-research)*0.45);
          g2.translate(0.0, -1);
          BufferedImage bi = t==Team.A ? rNuker2.image : rNukeb2.image;
          AffineTransform trans = AffineTransform.getScaleInstance((1.0 / bi.getWidth()) * o.getRelativeSize(), (1.0 / bi.getWidth()) * o.getRelativeSize());
          g2.drawImage(bi, trans, null);
          if (research == 404)
          {
            double scale = 6.0;
            BufferedImage ei = rexplode.image;
            trans = AffineTransform.getScaleInstance((scale / ei.getWidth()) * o.getRelativeSize(), (scale / ei.getWidth()) * o.getRelativeSize());
            g2.translate(-(scale-1.0)/2, -(scale-1.0)/2-2);
            g2.drawImage(ei, trans, null);
          }
        }
        	
        	
      }
        
      if (!debug.isDragging()) {
        debug.setTarget(hoverID, hoverLoc, controlBits);
      }

    }
}
