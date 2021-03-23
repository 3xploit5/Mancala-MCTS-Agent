package at.magicbeansagent;

import at.pwd.boardgame.game.mancala.MancalaGame;
import at.pwd.boardgame.game.mancala.agent.MancalaAgent;
import at.pwd.boardgame.game.mancala.agent.MancalaAgentAction;
import at.pwd.boardgame.game.base.WinState;

import java.util.ArrayList;
import java.util.LinkedList;
import java.util.List;
import java.util.Random;

public class MagicBeansAgent implements MancalaAgent {
    private final Random random = new Random();

    private int MY_ID;
    private long STARTTIME = 0;
    private long COMPUTATIONTIME = 0;
    private int NUM_SLOTS;
    private int POINTS_TO_WIN;

    private final double DEFAULT_C = Math.sqrt(2.);

    private Node LAST_WINNER = null;


    /**
     * A node in the search tree. Each Node has a list of children.
     * A node represents one possible play and stores various information for that play.
     */
    private class Node {

        private final MancalaGame game;
        private final WinState winState;

        private Node parent;
        private final LinkedList<Node> children = new LinkedList<>();

        // 0 <= visitCount
        private int visitCount;
        // 0 <= winCount
        private int winCount;
        // this node's unique move
        private String slot;


        /// Tree trimming booleans
        // True if game is over or (all possible moves are expanded and all children are explored)
        private boolean explored = false;
        // True if node is enemy's move. Boolean for convenience.
        private final boolean isEnemyMove;
        // True if node is enemy's move and [game is over && enemy wins) || (any child isEnemyWin)]
        private boolean isEnemyWin = false;
        // True if my move and any child isEnemyWin
        private boolean tabu = false;

        //
        public Node(MancalaGame game) {
            this.game = game;
            this.winState = getWinner(game);
            this.isEnemyMove = game.getState().getCurrentPlayer() != MY_ID;
        }

        /**
         * @return True if the node can have more children, otherwise false.
         */
        public boolean expandable() {
            return this.children.size() < game.getSelectableSlots().size();
        }

        /**
         * Adds a child to the node.
         * @param k: Child represents the k^th available move.
         * @return The child that was added.
         */
        public Node addChild(int k) {
            assert expandable() : "Can't add child: Node not expandable";
            assert k >= 0 : "Can't add child: k must be positive integer.";

            List<String> possibleSlots = game.getSelectableSlots();
            for (Node child :children) {
                possibleSlots.remove(child.slot);
            }
            String slot = possibleSlots.get(Math.min(k, possibleSlots.size()));

            MancalaGame newGame = new MancalaGame(this.game);
            if (!newGame.selectSlot(slot)) newGame.nextPlayer();
            Node child = new Node(newGame);
            child.slot = slot;
            child.parent = this;
            children.add(child);
            return child;
        }

        /**
         * Adds a random child to the node.
         * @return The child that was added.
         */
        public Node addRandomChild() {
            List<String> possibleSlots = game.getSelectableSlots();
            for (Node child :children) possibleSlots.remove(child.slot);
            return addChild(random.nextInt(possibleSlots.size()));
        }


        /// Tree trimming methods
        /**
         * Checks if for all children explored == True .
         * @return True if all children are explored, false otherwise.
         */
        public boolean allChildrenExplored() {
            for (Node child : children) {
                if (!child.explored) return false;
            }
            return true;
        }

        /**
         * Checks if a node is fully expanded and all children are explored.
         * @return True if node fully expanded and all children are explored, false otherwise.
         */
        public boolean allMovesExplored() {return !expandable() && allChildrenExplored();}

        //
        private boolean anyChildEnemyWin(){
            for (Node child : children) {
                if (child.isEnemyWin) return true;
            }
            return false;
        }
        private boolean allChildrenTabu(){
            if (expandable()) return false;
            for (Node child : children) {
                if (!child.tabu) return false;
            }
            return true;
        }
        public boolean checkIfIsEnemyWin() {
            if (!isEnemyWin && isEnemyMove && ((gameOver(this) && winState.getPlayerId() != MY_ID) || this.anyChildEnemyWin())) {
                isEnemyWin = true;
            }
            return isEnemyWin;
        }
        public boolean checkIfTabu(){
            if (!tabu && !isEnemyMove && (anyChildEnemyWin() || allChildrenTabu())) tabu = true;
            return tabu;
        }


        /// Scoring
        /**
         * Calculates Upper Confidence Bound (UCB) for this node.
         * @param C Parameter used in vanilla UCB. If 0, returns pure win/visit ratio.
         * @return UCB of node
         */
        private double getUCB(Double C) {
            assert parent != null : "getUCB: parent must not be null.";
            int temp_vc = visitCount == 0 ? 1 : visitCount;
            C = C == null ? DEFAULT_C : C;
            return (((double) winCount) / temp_vc) + C * Math.sqrt(Math.log(parent.visitCount) / temp_vc);
        }

        /**
         * Calculates a score for every child and returns best child. If multiple children are tied, one is chosen at random.
         * @param UCB_C: Parameter passed to getUCB().
         * @param avoidExplored: If true, does not return children with explored=true.
         * @param avoidTabu: If true, does not return children with tabu=true.
         * @return Child node with highest score.
         */
        public Node getChildWithBestScore(Double UCB_C, boolean getWorst, boolean avoidExplored, boolean avoidTabu) {
            assert children.size() > 0 : "getChildWithBestUCB: No children available.";

            ArrayList<Node> candidateList = new ArrayList<>();
            double bestScore = getWorst ? Double.POSITIVE_INFINITY : 0;
            for (Node child : children) {

                double childScore = child.getUCB(UCB_C);

                if (((!getWorst && childScore >= bestScore) || (getWorst && childScore <= bestScore)) &&
                        (!child.explored || !avoidExplored) && (!child.tabu || !avoidTabu)) {
                    if (childScore != bestScore) {
                        candidateList.clear();
                        bestScore = childScore;
                    }
                    candidateList.add(child);
                }

                // Fully explored and winning all games = guaranteed win.
                if (!avoidExplored && child.explored && child.getUCB(0.) >= 1.) {
                    return child;
                }

            }
            // Backup.
            if (candidateList.size() == 0) {
                if (avoidTabu) return getChildWithBestScore(UCB_C, getWorst, avoidExplored, false);
                if (avoidExplored) return getChildWithBestScore(UCB_C, getWorst,false, false);
                return children.getFirst();
            }
            return candidateList.get(random.nextInt(candidateList.size()));
        }
        public Node getChildWithBestScore(Double UCB_C, boolean avoidExplored, boolean avoidTabu) {
            return getChildWithBestScore(UCB_C, false, avoidExplored, avoidTabu);
        }

        /**
         * Just for testing: Return String with score of each child.
         * @param C UCB parameter
         * @return String with scores
         */
        public String _getAllChildScores(Double C){
            assert children.size() > 0 : "_getAllChildScores: No children available.";
            StringBuilder output = new StringBuilder("\n");
            for (Node child : children) output.append(String.format("Slot %s: %.3f \n", child.slot, child.getUCB(C)));
            return output.toString();
        }
        public String _getAllChildWinVisit(){
            assert children.size() > 0 : "_getAllChildWinVisit: No children available.";
            StringBuilder output = new StringBuilder("\n");
            for (Node child : children) {
                String tempExploredStr = child.explored ? "    (fully explored)" : "(not fully explored)";
                output.append(String.format("Slot %s %s Wins/Visits: %d/%d = %.3f \n", child.slot, tempExploredStr, child.winCount,
                        child.visitCount, (child.winCount*1.0/child.visitCount)));
            }
            return output.toString();
        }
        public boolean hasWinningChild(){
            for (Node child: children) {
                if(child.explored && child.getUCB(0.) >= 1) return true;
            }
            return false;
        }

    }

    /**
     * Main MCTS based routine for a single turn of a Mancala game.
     * @param computationTime max computation time available.
     * @param mancalaGame a game state.
     * @return the children node from root with the highest score, i.e. best move found.
     */
    @Override
    public MancalaAgentAction doTurn(int computationTime, MancalaGame mancalaGame) {
        this.MY_ID = mancalaGame.getState().getCurrentPlayer();
        this.NUM_SLOTS = Math.max(Integer.parseInt(mancalaGame.getBoard().getDepotOfPlayer(0)),
                Integer.parseInt(mancalaGame.getBoard().getDepotOfPlayer(1))) - 2;
        this.POINTS_TO_WIN = mancalaGame.getBoard().getStonesPerSlot() * NUM_SLOTS;
        this.STARTTIME = System.currentTimeMillis();
        this.COMPUTATIONTIME = computationTime;

        if (mancalaGame.getSelectableSlots().size() == 1){
            System.out.println("Playing only available slot.\n");
            return new MancalaAgentAction(mancalaGame.getSelectableSlots().get(0));
        }

        // Load winning node from previous turn and find offspring that matches current game and use it as root.
        Node root = null;
        if (LAST_WINNER != null) root = getMatchingNode(mancalaGame, LAST_WINNER);
        root = root == null ? new Node(mancalaGame) : root;
        root.parent = null;

        // if (root.children.size()>0) System.out.println("\nLoaded Search-Tree Root Children:" + root._getAllChildWinVisit());

        if (gameOver(root)){   // Just for quality of life.
            System.out.println("Game is over, playing first slot\n");
            LAST_WINNER = null;
            return new MancalaAgentAction(mancalaGame.getSelectableSlots().get(0));
        }

        Node leaf;
        ArrayList<Node> todoList = new ArrayList<>();

        double perc_1 = 0.5;
        double perc_2 = 0.75;
        double perc_3 = 0.95;

        while (inTime() && !root.explored && !root.hasWinningChild()) {
            todoList.clear();

            // Selection Strategy
            if (inTime(perc_1))         leaf = select(root, "", 10.);
            else if (inTime(perc_2))    leaf = select(root, "enemy_perspective", 5.);
            else if (inTime(perc_3))    leaf = select(root, "enemy_perspective", DEFAULT_C);
            else                        leaf = select(root, "", 0.);

            // Expansion Strategy
            if (inTime(perc_1))         todoList = expand(leaf);
            else if (inTime(perc_3))    todoList = expand(leaf, "k-random", 3);
            else                        todoList = expand(leaf, "k-random", 1);

            for (Node candidate : todoList) {   // Do simulation & backpropagation for each candidate separately
                // Simulation
                WinState result = simulate(candidate.game);
                // Backpropagation
                backPropagation(candidate, result);
            }
        }
        Node winner = root.getChildWithBestScore(DEFAULT_C, false, false, false);

        LAST_WINNER = winner;
        LAST_WINNER.parent = null;

        return new MancalaAgentAction(winner.slot);
    }

    /**
     * Starting from the root node we look for a node to expand using the chosen strategy.
     * @param root: Root node of the tree.
     * @param strategy: Selection strategy. Default: UCT with C=sqrt(2) and T=0.
     * @return Candidate node to expand.
     */
    private Node select(Node root, String strategy, Double UCB_C){
        Node candidate = root;

        // Default values
        int T = (int) Math.ceil(NUM_SLOTS/2.);

        switch(strategy) {
            case "T":   // If a child has less than T expanded nodes, it is chosen for expansion.
                while (candidate.children.size() > T){
                    candidate = candidate.getChildWithBestScore(UCB_C, true, false);
                }
                break;
            case "enemy_perspective":   // on enemy move, the move with worst score is chosen.
                while (candidate.children.size() > 0) {
                    if (candidate.children.getFirst().isEnemyMove) {
                        candidate = candidate.getChildWithBestScore(UCB_C, true, true, false);
                    } else {
                        candidate = candidate.getChildWithBestScore(UCB_C, false, true, false);
                    }
                }
                break;
            case "avoidTabu":
                while (candidate.children.size() > 0){
                    candidate = candidate.getChildWithBestScore(UCB_C, true, true);
                }
                break;
            default:    // Vanilla UCT, C=sqrt(2), T=0
                while (candidate.children.size() > 0){
                    candidate = candidate.getChildWithBestScore(UCB_C, true, false);
                }
        }
        return candidate;
    }

    /**
     * Expands the input node according to selected strategy.
     * @param parent: Node to expand.
     * @param strategy: Expansion strategy. Default: Expand once at random.
     * @param k: Number of nodes in "k-random" strategy.
     * @return An ArrayList of the newly created child nodes ready for simulation. (Empty ArrayList if game ends with parent)
     */
    private ArrayList<Node> expand(Node parent, String strategy, int k){
        ArrayList<Node> candidateList = new ArrayList<>();

        if (gameOver(parent) || !parent.expandable()) return candidateList;

        if ("k-random".equals(strategy)) {
            for (int i = 0; i < k; i++) {
                if (parent.expandable()) {
                    candidateList.add(parent.addRandomChild());
                } else break;
            }
        } else {   // Expand all possible moves
            while (parent.expandable()) {
                candidateList.add(parent.addChild(0));
            }
        }
        return candidateList;
    }
    private ArrayList<Node> expand(Node parent){
        return expand(parent, "", 2);
    }

    /**
     * Given a game instance, moves are played according to selected strategy until the game is over.
     * @param game: A Mancala game.
     * @return The game state of the played out game.
     */
    private WinState simulate(MancalaGame game){
        game = new MancalaGame(game);

        while(!gameOver(game) && inTime() && game.getSelectableSlots().size() > 0) {
            String play;
            do {
                List<String> legalMoves = game.getSelectableSlots();
                play = legalMoves.get(random.nextInt(legalMoves.size()));
            } while(game.selectSlot(play));
            game.nextPlayer();
        }
        return getWinner(game);
    }

    /**
     * The tree is traversed upwards and the visitCount is incremented. If the Path is winnable, the winCount is too.
     * @param node: Node from which to start the backpropagation.
     * @param simulationResult: Result of the simulated game started at input node.
     */
    private void backPropagation(Node node, WinState simulationResult){

        if (simulationResult.getState() != WinState.States.SOMEONE) return;   // We don't backprop if simulation did not finish.

        if (gameOver(node)) {
            node.explored = true;
        }

        boolean hasWon = simulationResult.getPlayerId() == MY_ID;
        Node tempNode = node;
        while (tempNode != null) {
            tempNode.visitCount++;
            if (hasWon) tempNode.winCount++;

            /// We (mis-)use backPropagation() to update some booleans
            // We update the explored status of nodes on the path.
            if (tempNode.allMovesExplored()) tempNode.explored = true;

            // We check if the node allows the enemy to win.
            if (tempNode.checkIfIsEnemyWin()) tempNode.winCount = 0;

            // We check if a node has a child that allows the enemy to win. If so, that node becomes tabu.
            if (tempNode.checkIfTabu()) tempNode.winCount = 0;

            tempNode = tempNode.parent;
        }
    }

    /**
     * Extends checkIfPlayerWins() with additional win conditions:
     * Game ends if either depot has more than half of the available stones,
     * or if checkIfPlayerWins().getState == SOMEONE.
     * @param game: A MancalaGame.
     * @return WinState according to the game over conditions above.
     */
    private WinState getWinner(MancalaGame game) {
        WinState winState = game.checkIfPlayerWins();
        if (winState.getState() == WinState.States.NOBODY) {
            for (int id=0; id < 2; id++) {
                if (game.getState().stonesIn(game.getBoard().getDepotOfPlayer(id)) > POINTS_TO_WIN) {
                    return new WinState(WinState.States.SOMEONE, id);
                }
            }
        }
        return winState;
    }
    // booleans for ease of use.
    private boolean gameOver(MancalaGame game) {
        return getWinner(game).getState() == WinState.States.SOMEONE;
    }
    private boolean gameOver(Node node) {return node.winState.getState() == WinState.States.SOMEONE;}

    //
    private boolean inTime(){
        return (System.currentTimeMillis() - STARTTIME) < (COMPUTATIONTIME*1000 - 600);
    }
    private boolean inTime(double percent){ // Allows us to use strategies depending on remaining runtime
        return (System.currentTimeMillis() - STARTTIME) < percent*(COMPUTATIONTIME*1000 - 600);
    }


    // Helper functions for loading old node
    public String getGameBoardString(MancalaGame game) {
        StringBuilder output = new StringBuilder("");
        int max_slot = Integer.parseInt(game.getBoard().getDepotOfPlayer(0)) * 2 - 2;
        for (int i = 1; i<= max_slot; i++) {
            output.append(" ").append(game.getState().stonesIn(Integer.toString(i)));
        }
        return output.toString();
    }
    private boolean compareGames(MancalaGame gameA, MancalaGame gameB) {
        return getGameBoardString(gameA).equals(getGameBoardString(gameB)) &&
                gameA.getState().getCurrentPlayer() == gameB.getState().getCurrentPlayer();
    }
    private Node getMatchingNode(MancalaGame current, Node lastWinner) {
        if (compareGames(current, lastWinner.game)) return lastWinner;

        for (Node child : lastWinner.children) {
            if (compareGames(current, child.game)) return child;
            else if (child.game.getState().getCurrentPlayer() != MY_ID) return getMatchingNode(current, child);
        }
        return null;
    }

    public void prettyPrintBoard(MancalaGame game) {
        // slot id count clockwise starting with depot left
        int playerDepot_Left = Integer.parseInt(game.getBoard().getDepotOfPlayer(1));    // should always be "1"
        int playerDepot_Right = Integer.parseInt(game.getBoard().getDepotOfPlayer(0));
        int max_slot = Integer.max(playerDepot_Right, playerDepot_Left) * 2 - 2;

        StringBuilder output = new StringBuilder("---------------------------------------------\n");

        output.append("   ");
        for (int i = playerDepot_Left+1; i<playerDepot_Right; i++) {
            output.append(String.format("%2d ",game.getState().stonesIn(Integer.toString(i))));
        }
        output.append("\n");

        output.append(game.getState().stonesIn(Integer.toString(playerDepot_Left)));
        for (int i = playerDepot_Left+1; i<playerDepot_Right; i++) {
            output.append("   ");
        }
        output.append(String.format("   %d\n", game.getState().stonesIn(Integer.toString(playerDepot_Right))));

        output.append("   ");
        for (int i = max_slot; i>=playerDepot_Right+1; i--) {
            output.append(String.format("%2d ",game.getState().stonesIn(Integer.toString(i))));
        }
        output.append("\n");

        output.append("---------------------------------------------");
        System.out.println(output.toString());
    }

    @Override
    public String toString() {
        return "Magic Beans";
    }
}
