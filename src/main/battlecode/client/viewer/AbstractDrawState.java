package battlecode.client.viewer;

import battlecode.client.viewer.render.RenderConfiguration;
import battlecode.common.ComponentClass;
import battlecode.common.ComponentType;
import battlecode.common.MapLocation;
import battlecode.common.RobotType;
import battlecode.common.Team;
import battlecode.serial.RoundStats;
import battlecode.world.GameMap;
import battlecode.world.signal.*;

import java.util.ArrayList;
import java.util.EnumMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;

public abstract class AbstractDrawState<DrawObject extends AbstractDrawObject> extends GameState {

    protected abstract DrawObject createDrawObject(RobotType type, Team team, int id);
	protected abstract DrawObject createDrawObject(DrawObject o);
    protected Map<Integer, DrawObject> groundUnits;
    protected Map<Integer, DrawObject> airUnits;
    protected Map<Integer, FluxDepositState> fluxDeposits;
    protected double[] teamHP = new double[2];
    //protected List<DrawObject> archonsA;
    //protected List<DrawObject> archonsB;
    //protected ArrayList<ComponentType> aTeamComponents = new ArrayList<ComponentType>();
    //protected ArrayList<ComponentType> bTeamComponents = new ArrayList<ComponentType>();
    //protected ComponentType[] topWeapons = new ComponentType[2];
    //protected ComponentType[] topArmors = new ComponentType[2];
    //protected ComponentType[] topMiscs = new ComponentType[2];
    protected Map<ComponentType, Integer> componentTypeCountA = new EnumMap<ComponentType, Integer>(ComponentType.class);
    protected Map<ComponentType, Integer> componentTypeCountB = new EnumMap<ComponentType, Integer>(ComponentType.class);
    protected Map<RobotType, Integer> chassisTypeCountA = new EnumMap<RobotType, Integer>(RobotType.class);
    protected Map<RobotType, Integer> chassisTypeCountB = new EnumMap<RobotType, Integer>(RobotType.class);
	protected Map<Team, List<DrawObject>> archons;
	protected Map<Team,DrawObject> cores = new EnumMap<Team,DrawObject>(Team.class);
    protected static MapLocation origin = null;
    protected GameMap gameMap;
    protected int currentRound;
    protected RoundStats stats = null;
    protected Iterable<Map.Entry<Integer, DrawObject>> drawables =
            new Iterable<Map.Entry<Integer, DrawObject>>() {

                public Iterator<Map.Entry<Integer, DrawObject>> iterator() {
                    return new UnitIterator();
                }
            };

    private class UnitIterator implements Iterator<Map.Entry<Integer, DrawObject>> {

        private Iterator<Map.Entry<Integer, DrawObject>> it =
                groundUnits.entrySet().iterator();
        private boolean ground = true;

        public boolean hasNext() {
            return it.hasNext() || (ground && !airUnits.isEmpty());
        }

        public Map.Entry<Integer, DrawObject> next() {
            if (!it.hasNext() && ground) {
                ground = false;
                it = airUnits.entrySet().iterator();
            }
            return it.next();
        }

        public void remove() {
            it.remove();
        }
    };

	public AbstractDrawState() {
		archons = new EnumMap<Team, List<DrawObject>>(Team.class);
		archons.put(Team.A,new ArrayList<DrawObject>());
		archons.put(Team.B,new ArrayList<DrawObject>());
	}

	protected synchronized void copyStateFrom(AbstractDrawState<DrawObject> src) {
        groundUnits.clear();
        archons.get(Team.A).clear();
        archons.get(Team.B).clear();
        for (Map.Entry<Integer, DrawObject> entry : src.groundUnits.entrySet()) {
            DrawObject copy = createDrawObject(entry.getValue());
            groundUnits.put(entry.getKey(), copy);
            tryAddArchon(copy);
        }
        airUnits.clear();
        for (Map.Entry<Integer, DrawObject> entry : src.airUnits.entrySet()) {
            DrawObject copy = createDrawObject(entry.getValue());
            airUnits.put(entry.getKey(), copy);
        }
        fluxDeposits.clear();
        for (Map.Entry<Integer, FluxDepositState> entry : src.fluxDeposits.entrySet()) {
            fluxDeposits.put(entry.getKey(), new FluxDepositState(entry.getValue()));
        }
		if(src.cores.get(Team.A)!=null)
			cores.put(Team.A,airUnits.get(src.cores.get(Team.A).getID()));
		else
			cores.remove(Team.A);
		if(src.cores.get(Team.B)!=null)
			cores.put(Team.B,airUnits.get(src.cores.get(Team.B).getID()));
		else
			cores.remove(Team.B);
        stats = src.stats;
        componentTypeCountA = new EnumMap<ComponentType, Integer>(src.componentTypeCountA);
        componentTypeCountB = new EnumMap<ComponentType, Integer>(src.componentTypeCountB);
        chassisTypeCountA = new EnumMap<RobotType, Integer>(src.chassisTypeCountA);
        chassisTypeCountB = new EnumMap<RobotType, Integer>(src.chassisTypeCountB);

        if (src.gameMap != null) {
            gameMap = src.gameMap;
        }

        currentRound = src.currentRound;
    }

	public List<DrawObject> getArchons(Team t) {
		return archons.get(t);
	}

	public DrawObject getPowerCore(Team t) {
		return cores.get(t);
	}

    protected Iterable<Map.Entry<Integer, DrawObject>> getDrawableSet() {
        if (!RenderConfiguration.showGround() && !RenderConfiguration.showAir()) {
            return null;
        }

        if (!RenderConfiguration.showGround()) {
            return airUnits.entrySet();
        }
        if (!RenderConfiguration.showAir()) {
            return groundUnits.entrySet();
        }
        return drawables;
    }

    protected DrawObject getRobot(int id) {
        DrawObject obj = groundUnits.get(id);
        if (obj == null) {
            obj = airUnits.get(id);
            assert obj != null : "Robot #" + id + " not found";
        }
        return obj;
    }

    public Map<RobotType, Integer> getRobotTypeTypeCount(Team t) {
        Map<RobotType, Integer> orig = (t == Team.A) ? chassisTypeCountA : chassisTypeCountB;
        Map<RobotType, Integer> byclass = new EnumMap<RobotType, Integer>(RobotType.class);
        for (RobotType ct : orig.keySet())
            if (orig.get(ct) > 0)
                byclass.put(ct, orig.get(ct));
        return byclass;
    }

    public Map<ComponentType, Integer> getComponentTypeCount(Team t) {
        return (t == Team.A) ? componentTypeCountA : componentTypeCountB;
    }

    public Map<ComponentType, Integer> getComponentTypeCount(Team t, ComponentClass c) {
        Map<ComponentType, Integer> orig = (t == Team.A) ? componentTypeCountA : componentTypeCountB;
        Map<ComponentType, Integer> byclass = new EnumMap<ComponentType, Integer>(ComponentType.class);
        for (ComponentType ct : orig.keySet()) {
            if (ct.componentClass == c && orig.get(ct) > 0) {
                byclass.put(ct, orig.get(ct));
            }
        }
        return byclass;
    }

    protected void removeRobot(int id) {
        DrawObject previous = groundUnits.remove(id);
        if (previous == null) {
            previous = airUnits.remove(id);
            assert previous != null : "Robot #" + id + " not found";
        }
    }

    protected void putRobot(int id, DrawObject unit) {
        if (unit.getType().isAirborne()) {
            DrawObject previous = airUnits.put(id, unit);
            assert previous == null : "Robot #" + id + " already exists";
        } else {
            DrawObject previous = groundUnits.put(id, unit);
            assert previous == null : "Robot #" + id + " already exists";
        }
    }

    protected void tryAddArchon(DrawObject archon) {
        if (archon.getType() == RobotType.ARCHON)
			archons.get(archon.getTeam()).add(archon);
    }

    //public List<DrawObject> getArchons(Team team) {
    //    return (team == Team.A ? archonsA : archonsB);
    //}
    public RoundStats getRoundStats() {
        return stats;
    }

    public void setGameMap(GameMap map) {
        gameMap = new GameMap(map);
        origin = gameMap.getMapOrigin();
    }

    public GameMap getGameMap() {
        return gameMap;
    }

    protected abstract void mineFlux(DrawObject object);

    protected void updateRound() {
        currentRound++;
        for (Iterator<Map.Entry<Integer, DrawObject>> it = drawables.iterator();
                it.hasNext();) {
            DrawObject obj = it.next().getValue();
            obj.updateRound();
            if (!obj.isAlive()) {
                it.remove();
                //if (obj.getType() == RobotType.ARCHON) {
                //	(obj.getTeam() == Team.A ? archonsA : archonsB).remove(obj);
                //}
            }
            //if (obj.getType() == RobotType.WOUT) {
            //	mineFlux(obj);
            //}
        }
    }

    public void visitAttackSignal(AttackSignal s) {
        getRobot(s.getRobotID()).setAttacking(s.getTargetLoc(), s.getTargetHeight());
        
    }

    public void visitBroadcastSignal(BroadcastSignal s) {
        getRobot(s.getRobotID()).setBroadcast();
        
    }

    public void visitDeathSignal(DeathSignal s) {
        int team = getRobot(s.getObjectID()).getTeam().ordinal();
        DrawObject robot = getRobot(s.getObjectID());
        if (team < 2) {
            teamHP[team] -= getRobot(s.getObjectID()).getEnergon();
            Map<RobotType, Integer> ctc = (robot.getTeam() == Team.A) ? this.chassisTypeCountA : this.chassisTypeCountB;
            ctc.put(robot.getType(), ctc.get(robot.getType()) - 1);
        }
        
        robot.destroyUnit();
        
    }

    public void visitEnergonChangeSignal(EnergonChangeSignal s) {
        int[] robotIDs = s.getRobotIDs();
        double[] energon = s.getEnergon();
        for (int i = 0; i < robotIDs.length; i++) {
            int team = getRobot(robotIDs[i]).getTeam().ordinal();
            if (team < 2)
                teamHP[team] -= getRobot(robotIDs[i]).getEnergon();
            getRobot(robotIDs[i]).setEnergon(energon[i]);
            if (team < 2)
                teamHP[team] += energon[i];
        }
        
    }

    public void visitFluxChangeSignal(FluxChangeSignal s) {
        int[] robotIDs = s.getRobotIDs();
        double[] flux = s.flux;
        for (int i = 0; i < robotIDs.length; i++) {
            getRobot(robotIDs[i]).setFlux(flux[i]);
        }
        
    }

	public void visitIndicatorStringSignal(IndicatorStringSignal s) {
        if (!RenderConfiguration.isTournamentMode()) {
            getRobot(s.getRobotID()).setString(s.getStringIndex(), s.getNewString());
        }
        
    }

    public void visitControlBitsSignal(ControlBitsSignal s) {
        getRobot(s.getRobotID()).setControlBits(s.getControlBits());
        
    }

    public void visitMovementOverrideSignal(MovementOverrideSignal s) {
        getRobot(s.getRobotID()).setLocation(s.getNewLoc());
        
    }

    public void visitMovementSignal(MovementSignal s) {
        DrawObject obj = getRobot(s.getRobotID());
        boolean teleported = !obj.loc.isAdjacentTo(s.getNewLoc());
        //TODO: this should probably be from a teleported signal
        obj.setLocation(s.getNewLoc());
        if (teleported) {
            obj.setTeleport(obj.loc, s.getNewLoc());
        } else {

            obj.setMoving(s.isMovingForward());
        }
        
    }

    //synchronized to make sure we don't get concurrency issues.
    public synchronized void visitEquipSignal(EquipSignal s) {
        //We have our robot update its components so that we can show it in the infopanel.
        DrawObject obj = getRobot(s.robotID);
        obj.addComponent(s.component);
        Team objTeam = obj.getTeam();


        Map<ComponentType, Integer> componentTypeCount = (objTeam == Team.A) ? componentTypeCountA : componentTypeCountB;
        //Iterate through and get the counts
        if (!componentTypeCount.containsKey(s.component)) {
            componentTypeCount.put(s.component, 1);
        } else {
            componentTypeCount.put(s.component, componentTypeCount.get(s.component) + 1);
        }

        if (s.component.componentClass == ComponentClass.COMM)
            obj.updateBroadcastRadius(s.component.range);
        
    }

    public void visitSetDirectionSignal(SetDirectionSignal s) {
        getRobot(s.getRobotID()).setDirection(s.getDirection());
        
    }

    public DrawObject spawnRobot(SpawnSignal s) {
        DrawObject spawn = createDrawObject(s.getType(), s.getTeam(), s.getRobotID());
        spawn.setLocation(s.getLoc());
        spawn.setDirection(s.getDirection());
        putRobot(s.getRobotID(), spawn);
		if(s.getType()==RobotType.POWER_NODE&&s.getTeam()!=Team.NEUTRAL)
			cores.put(s.getTeam(),spawn);
        tryAddArchon(spawn);
        int team = getRobot(s.getRobotID()).getTeam().ordinal();
        if (team < 2) {
            teamHP[team] += getRobot(s.getRobotID()).getEnergon();
            Map<RobotType, Integer> ctc = (s.getTeam() == Team.A) ? this.chassisTypeCountA : this.chassisTypeCountB;
            if (ctc.containsKey(s.getType()))
                ctc.put(s.getType(), ctc.get(s.getType()) + 1);
            else
                ctc.put(s.getType(), 1);
        }
        return spawn;
    }

    public void visitSpawnSignal(SpawnSignal s) {
        spawnRobot(s);
        
    }

    public void visitBytecodesUsedSignal(BytecodesUsedSignal s) {
        int[] robotIDs = s.getRobotIDs();
        int[] bytecodes = s.getNumBytecodes();
        for (int i = 0; i < robotIDs.length; i++) {
            getRobot(robotIDs[i]).setBytecodesUsed(bytecodes[i]);
        }
        
    }

	public void visitLoadSignal(LoadSignal s) {
		getRobot(s.passengerID).load();
	}

	public void visitUnloadSignal(UnloadSignal s) {
		getRobot(s.passengerID).unload(s.unloadLoc);
	}

    public void visitMineBirthSignal(MineBirthSignal s) {
        fluxDeposits.put(s.id, new FluxDepositState(s.id, s.location, s.roundsAvaliable));

    }

    public void visitMineDepletionSignal(MineDepletionSignal s) {
        fluxDeposits.get(s.id).setRoundsAvailable(s.roundsAvaliable);
    }

	public void visitNodeCaptureSignal(NodeCaptureSignal s) {
		getRobot(s.robotID).setTeam(s.newTeam);
	}

    public void visitTurnOnSignal(TurnOnSignal s) {
        for (int i : s.robotIDs)
            getRobot(i).setPower(true);
    }

    public void visitTurnOffSignal(TurnOffSignal s) {
        getRobot(s.robotID).setPower(false);
    }
}
