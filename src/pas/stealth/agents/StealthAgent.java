package src.pas.stealth.agents;


// SYSTEM IMPORTS
import edu.cwru.sepia.action.Action;
import edu.cwru.sepia.environment.model.history.History.HistoryView;
import edu.cwru.sepia.environment.model.state.ResourceNode;
import edu.cwru.sepia.environment.model.state.ResourceNode.ResourceView;
import edu.cwru.sepia.environment.model.state.State.StateView;
import edu.cwru.sepia.environment.model.state.Unit.UnitView;
import edu.cwru.sepia.util.Direction;


import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.PriorityQueue;
import java.util.Set;
import java.util.Stack;

// JAVA PROJECT IMPORTS
import edu.bu.pas.stealth.agents.AStarAgent;                // the base class of your class
import edu.bu.pas.stealth.agents.AStarAgent.AgentPhase;     // INFILTRATE/EXFILTRATE enums for your state machine
import edu.bu.pas.stealth.agents.AStarAgent.ExtraParams;    // base class for creating your own params objects
import edu.bu.pas.stealth.distance.DistanceMetric;
import edu.bu.pas.stealth.graph.Vertex;                     // Vertex = coordinate
import edu.bu.pas.stealth.graph.Path;                       // see the documentation...a Path is a linked list



public class StealthAgent
    extends AStarAgent
{

    // Fields of this class
    // TODO: add your fields here! For instance, it might be a good idea to
    // know when you've killed the enemy townhall so you know when to escape!
    // TODO: implement the state machine for following a path once we calculate it
    //       this will for sure adding your own fields.
    
    // Fields tracking game state
    private int enemyChebyshevSightLimit;  // Enemy sight range
    private Stack<Vertex> currentPlan;  // Path to follow
    private Vertex nextVertexToMoveTo;  // Next move location
    private Map<Integer, Vertex> previousEnemyPositions;  // Stores last known enemy locations
    private Vertex startingPosition;  // Where the agent started
    private Integer goldResourceNodeId;
    private Integer myUnitId;  // ID of the agent’s unit
    private Integer enemyUnitId;  // ID of the enemy Townhall
    private Set<Integer> otherEnemyUnitIDs;  // IDs of enemy archers
    private boolean townhallDestroyed;  // Whether the Townhall is destroyed
    private AgentPhase currentPhase;  // Current phase (INFILTRATE / EXFILTRATE)
    private Vertex goalObjective;
    private Vertex goldVertex;

    public StealthAgent(int playerNum)
    {
        super(playerNum);
        this.currentPlan = new Stack<>();
        this.goldResourceNodeId = null;
        this.startingPosition = null;
    
        this.nextVertexToMoveTo = null;
        this.previousEnemyPositions = new HashMap<>();
        this.otherEnemyUnitIDs = new HashSet<>();  // Track enemy archers
        this.enemyChebyshevSightLimit = -1;  // Default invalid value
        this.myUnitId = -1;  // Set to null until assigned
        this.enemyUnitId = -1;  // Set to null until assigned
        this.townhallDestroyed = false;  // Starts as false
        this.currentPhase = AgentPhase.INFILTRATE;  // Start in attack mode
    }

    // TODO: add some getter methods for your fields! Thats the java way to do things!
    // Getter methods
    public int getEnemyChebyshevSightLimit() { return this.enemyChebyshevSightLimit; }
    //public int getMyUnitID() { return this.myUnitID; }
    public int getEnemyUnitID() { return this.enemyUnitId; }
    //public int getEnemyTargetUnitID() { return this.enemyUnitId; }
    public int getEnemyTargetUnitID() { return this.enemyUnitId;}
    //public Set<Integer> getOtherEnemyUnitIDs() { return this.otherEnemyUnitIDs; }
    public Stack<Vertex> getCurrentPlan() { return this.currentPlan; }
    public Vertex getNextVertexToMoveTo() { return this.nextVertexToMoveTo; }
    public Vertex getStartingPosition() { return this.startingPosition; }
    public boolean isTownhallDestroyed() { return this.townhallDestroyed; }
    public AgentPhase getCurrentPhase() { return this.currentPhase; }
    public Map<Integer, Vertex> getPreviousEnemyPositions() { return this.previousEnemyPositions; }
    public final Integer getGoldResourceNodeId() { return this.goldResourceNodeId; }
    private void setEnemyChebyshevSightLimit(int limit) { this.enemyChebyshevSightLimit = limit; }
    private void setGoldResourceNodeId(Integer i) { this.goldResourceNodeId = i; }
   

    ///////////////////////////////////////// Sepia methods to override ///////////////////////////////////

    /**
        TODO: if you add any fields to this class it might be a good idea to initialize them here
              if they need sepia information!
     */

    @Override
    public Map<Integer, Action> initialStep(StateView state,
                                            HistoryView history)
    {
        super.initialStep(state, history); // call AStarAgent's initialStep() to set helpful fields and stuff
        // now some fields are set for us b/c we called AStarAgent's initialStep()
        // let's calculate how far away enemy units can see us...this will be the same for all units (except the base)
        // which doesn't have a sight limit (nor does it care about seeing you)
        // iterate over the "other" (i.e. not the base) enemy units until we get a UnitView that is not null

        // Identify our Footman
    for (Integer unitId : state.getUnitIds(this.getPlayerNumber())) {
        this.myUnitId = unitId;
        break; // We only have one unit
    }

    if (this.myUnitId == null) {
        System.err.println("[ERROR] No Footman found for player " + this.getPlayerNumber());
        System.exit(-1);
    }

    // Identify enemy Townhall (checking all enemy players)
    for (Integer playerNum : state.getPlayerNumbers()) {
        if (playerNum == this.getPlayerNumber()) {
            continue; // Skip our own player number
        }

        for (Integer unitId : state.getUnitIds(playerNum)) {
            UnitView unit = state.getUnit(unitId);
            if (unit.getTemplateView().getName().equalsIgnoreCase("townhall")) {
                this.enemyUnitId = unitId;
                System.out.println("[DEBUG] Townhall found at: " + unit.getXPosition() + "," + unit.getYPosition());
                break;
            }
        }
    }

    if (this.enemyUnitId == null) {
        System.err.println("[ERROR] No Townhall found at the start of the game!");
        System.exit(-1);
    }

    // Track enemy archers
    for (Integer playerNum : state.getPlayerNumbers()) {
        if (playerNum == this.getPlayerNumber()) {
            continue; // Skip our own player number
        }

        for (Integer unitId : state.getUnitIds(playerNum)) {
            UnitView unit = state.getUnit(unitId);
            if (!unit.getTemplateView().getName().equalsIgnoreCase("townhall")) {
                this.otherEnemyUnitIDs.add(unitId);
            }
        }
    }
    //iNITIAL STEP: USE FOR gOLD
     Integer goldResourceNodeId = null;
        for (ResourceView resource : state.getAllResourceNodes()) {
            if (resource.getType() == ResourceNode.Type.GOLD_MINE) {
                goldResourceNodeId = resource.getID();
                break; 
            }
        }

        if (goldResourceNodeId == null) {
            System.err.println("[ERROR] ScriptedAgent.initialStep: Could not find gold resource");
            System.exit(-1);
        }
        this.setGoldResourceNodeId(goldResourceNodeId);


        UnitView otherEnemyUnitView = null;
        Iterator<Integer> otherEnemyUnitIDsIt = this.getOtherEnemyUnitIDs().iterator();
        while(otherEnemyUnitIDsIt.hasNext() && otherEnemyUnitView == null)
        {
            otherEnemyUnitView = state.getUnit(otherEnemyUnitIDsIt.next());
        }

        if(otherEnemyUnitView == null)
        {
            System.err.println("[ERROR] StealthAgent.initialStep: could not find a non-null 'other' enemy UnitView??");
            System.exit(-1);
        }

        // lookup an attribute from the unit's "template" (which you can find in the map .xml files)
        // When I specify the unit's (i.e. "footman"'s) xml template, I will use the "range" attribute
        // as the enemy sight limit
        this.setEnemyChebyshevSightLimit(otherEnemyUnitView.getTemplateView().getRange());

    // If no townhall is found, print an error but continue
    if (this.getEnemyTargetUnitID() == -1) {  // This should be enemyUnitId, not enemyTargetUnitID
        System.err.println("[ERROR] No Townhall found! This may cause issues later.");
    }

    // Store starting position for future exfiltration
    UnitView myUnit = state.getUnit(this.getMyUnitID());
    if (myUnit != null) {
        this.startingPosition = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());;
    }

    // Initialize previous enemy positions tracking
    for (Integer enemyID : this.getOtherEnemyUnitIDs()) {
        UnitView enemy = state.getUnit(enemyID);
        if (enemy != null) {
            previousEnemyPositions.put(enemyID, new Vertex(enemy.getXPosition(), enemy.getYPosition()));
        }
    }

    // Start in INFILTRATE phase
    setAgentPhase(AgentPhase.INFILTRATE);

    return null;
    }

    /**
        TODO: implement me! This is the method that will be called every turn of the game.
              This method is responsible for assigning actions to all units that you control
              (which should only be a single footman in this game)
     */
    @Override
    public Map<Integer, Action> middleStep(StateView state,
                                           HistoryView history)
    {
        Map<Integer, Action> actions = new HashMap<Integer, Action>();
        //myUnit
        UnitView myUnit = state.getUnit(this.getMyUnitID());
        Vertex myVertex = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());

        Integer goldResourceNodeId = this.getGoldResourceNodeId(); // Store the ID first
        ResourceView goldResource = state.getResourceNode(goldResourceNodeId);;

        //EnemyUnit
        UnitView enemyUnit = state.getUnit(this.getEnemyUnitID());

        if(goldResource != null && enemyUnit != null){
            goldVertex = new Vertex(goldResource.getXPosition(), goldResource.getYPosition());
            goalObjective = goldVertex;
        }
        else if(goldResource == null && enemyUnit != null){
            Vertex enemyVertex = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());
            goalObjective = enemyVertex;
        }
        else{
            goalObjective = getStartingPosition();
            System.out.println("[INFO] Returning to Safe Zone.");
        }
        
        System.out.println("[INFO] Current goal: " + goalObjective);

        // Plan
        if (currentPlan.isEmpty() || shouldReplacePlan(state, null)){
            currentPlan.clear();
            System.out.println("[DEBUG] Recalculating escape path...");
            Path escapePath = aStarSearch(myVertex, goalObjective, state, null);
            if (escapePath != null) {
                currentPlan = convertPathToStack(escapePath);
            } else {
                System.out.println("[ERROR] No valid escape path found!");
            }

        }

        // Move
        if (!currentPlan.isEmpty()) {
            Vertex nextMove = currentPlan.pop();
            if (DistanceMetric.chebyshevDistance(myVertex, nextMove) <= 1){
                System.out.println("[ACTION] Moving to: " + nextMove);
                actions.put(this.getMyUnitID(), Action.createPrimitiveMove(this.getMyUnitID(), getDirectionToMoveTo(myVertex, nextMove)));
            } 
        }

        // Physical Actions

        //Stealing Gold
        if(goldResource != null && enemyUnit != null){
            if (DistanceMetric.chebyshevDistance(goldVertex, myVertex) <= 1){
                 //  Determine correct gather direction
                 Direction gatherDirection = getDirectionToMoveTo(myVertex, goldVertex);
                 System.out.println("[ACTION] Stealing gold...");
                 actions.put(this.getMyUnitID(), Action.createPrimitiveGather(this.getMyUnitID(), gatherDirection));
                 System.out.println("[INFO] Gold stolen!");
            }
        }
        //Attack if Townhall Adjacent
        else if (goldResource == null && getAgentPhase() == AgentPhase.INFILTRATE && enemyUnit != null) {
            System.out.println("[ACTION] At Townhall! Attacking...");
            Vertex townhallVertex = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());
            if(DistanceMetric.chebyshevDistance(myVertex, townhallVertex) <= 1){
                actions.put(this.getMyUnitID(), Action.createPrimitiveAttack(this.getMyUnitID(), enemyUnitId));
            }
            
        }

        //Escape if in Exfiltrate Mode
        if (getAgentPhase() == AgentPhase.EXFILTRATE && myVertex.equals(startingPosition)) {
            System.out.println("[SUCCESS] Agent has safely returned. Mission Complete.");
        }

        return actions;
    
    }

    ////////////////////////////////// End of Sepia methods to override //////////////////////////////////

    /////////////////////////////////// AStarAgent methods to override ///////////////////////////////////

    public Collection<Vertex> getNeighbors(Vertex v,
                                           StateView state,
                                           ExtraParams extraParams)
    {
        List<Vertex> neighbors = new ArrayList<>();
        int x = v.getXCoordinate();
        int y = v.getYCoordinate();
        int[][] directions = {
            {1, 0}, {-1, 0}, {0, 1}, {0, -1}, 
            {1, 1}, {1, -1}, {-1, -1}, {-1, 1}
        };

        for (int[] dir : directions) {
            int newX = x + dir[0];
            int newY = y + dir[1];
  
            Vertex neighbor = new Vertex(newX, newY);

            if(state.getResourceNode(this.getGoldResourceNodeId()) != null){
                ResourceView goldResource = state.getResourceNode(this.getGoldResourceNodeId());
                int goldX = goldResource.getXPosition();
                int goldY = goldResource.getYPosition();
                Vertex gold = new Vertex(goldX, goldY);
                if(state.inBounds(newX, newY) && (!state.isResourceAt(newX, newY) || gold.equals(neighbor))){
                    neighbors.add(new Vertex(newX, newY));
                }
            }
            else
            {
                if (state.inBounds(newX, newY) && !state.isResourceAt(newX, newY)) {
                    neighbors.add(new Vertex(newX, newY));
                }
            }
        }

        return neighbors;
    }

    private float heuristic(Vertex v1, Vertex v2) {
        return Math.abs(v1.getXCoordinate() - v2.getXCoordinate()) + 
               Math.abs(v1.getYCoordinate() - v2.getYCoordinate());
    }

    public Path aStarSearch(Vertex src,
                            Vertex dst,
                            StateView state,
                            ExtraParams extraParams)
    {

        System.out.println("[DEBUG] Starting A* search from " + src + " to " + dst);

        PriorityQueue<Path> openSet = new PriorityQueue<>(Comparator.comparingDouble(path -> path.getTrueCost() + path.getEstimatedPathCostToGoal()));
        Set<Vertex> closedSet = new HashSet<>();

        openSet.add(new Path(src, 0f, 0f, null));
        closedSet.add(src);

        while (!openSet.isEmpty()) {
            Path currentPath = openSet.poll();
            Vertex currentVertex = currentPath.getDestination();

            if (currentVertex.equals(dst)) {
                return currentPath;
            }

            for (Vertex neighbor : getNeighbors(currentVertex, state, extraParams)) {
                if (!closedSet.contains(neighbor)) {
                    float edgeCost = getEdgeWeight(currentVertex, neighbor, state, extraParams);
                    float heuristicCost = heuristic(neighbor, dst);
                    openSet.add(new Path(neighbor, edgeCost, heuristicCost, currentPath));
                    closedSet.add(neighbor);
                }
            }
        }

        System.out.println("[ERROR] A* search failed. No path found.");
        return null;
    }

    public float getEdgeWeight(Vertex src,
                               Vertex dst,
                               StateView state,
                               ExtraParams extraParams){
    
        float cost = 1f;

        for (int id : this.getOtherEnemyUnitIDs()){
            UnitView enemyUnit = state.getUnit(id);
            if(enemyUnit != null){
                Vertex enemyVertex = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());

                float dist = DistanceMetric.chebyshevDistance(dst, enemyVertex);

                if (dist < this.getEnemyChebyshevSightLimit() + 3){
                    cost += 100000f/dist;
                }
            }
        }
        return cost;  // Default movement cost

    }
    public boolean shouldReplacePlan(StateView state,
                                     ExtraParams extraParams)
    {
        for (int id : this.getOtherEnemyUnitIDs()){
            UnitView enemyUnit = state.getUnit(id);
            if(enemyUnit != null){
            Vertex enemyVertex = new Vertex(enemyUnit.getXPosition(), enemyUnit.getYPosition());
            UnitView myUnit = state.getUnit(getMyUnitID());
            Vertex myUnitVertex = new Vertex(myUnit.getXPosition(), myUnit.getYPosition());

            float dist = DistanceMetric.chebyshevDistance(myUnitVertex, enemyVertex);

                if (dist < this.getEnemyChebyshevSightLimit() + 3){
                    return true;
                
                }
            }   
        }
        return false;
    
    }



// Extra Helper Functions
    // Converts a Path to a Stack of vertices for movement execution.
    private Stack<Vertex> convertPathToStack(Path path) {
    Stack<Vertex> movementStack = new Stack<>();
    path = path.getParentPath();
    while (path.getParentPath() != null) {
        movementStack.push(path.getDestination());
        path = path.getParentPath();
    }
    return movementStack;  // No more reversing issues!
    }
    

    //////////////////////////////// End of AStarAgent methods to override ///////////////////////////////

}
