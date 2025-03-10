package battleaimod.battleai;

import battleaimod.battleai.playorder.DefectPlayOrder;
import battleaimod.battleai.playorder.IronCladPlayOrder;
import battleaimod.battleai.playorder.SilentPlayOrder;
import com.megacrit.cardcrawl.cards.colorless.RitualDagger;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import ludicrousspeed.simulator.commands.Command;
import ludicrousspeed.simulator.commands.CommandList;
import savestate.CardState;
import savestate.SaveState;

import java.util.List;

public class StateNode {
    private final BattleAiController controller;
    public final StateNode parent;
    public final Command lastCommand;
    public String stateString;
    public SaveState saveState;

    private int minDamage = 5000;
    public List<Command> commands;
    private boolean initialized = false;
    private int commandIndex = -1;
    private boolean isDone = false;

    public StateNode(StateNode parent, Command lastCommand, BattleAiController controller) {
        this.parent = parent;
        this.lastCommand = lastCommand;
        this.controller = controller;
    }

    /**
     * Does the next step and returns true iff the parent should load state
     */
    public Command step() {
        if (saveState == null) {
            saveState = new SaveState();
        }

        if (commands == null) {
            populateCommands();
        }

        if (!initialized) {
            initialized = true;

            if (AbstractDungeon.player.isDead || AbstractDungeon.player.isDying) {
                if (controller.deathNode == null ||
                        (controller.deathNode != null && controller.deathNode.saveState.turn < saveState.turn)) {
                    System.err.println("dead 1");
                    controller.deathNode = this;
                    isDone = true;
                    return null;
                }
            }

            int damage = controller.startingHealth - saveState.getPlayerHealth();

            boolean isBattleWon = isBattleOver();
            if (!isBattleWon && damage < (controller.minDamage + 6)) {
                commandIndex = 0;
            } else {
//                System.err
//                        .printf("Found terminal state on init: damage this combat:%s; best damage: %s\n", damage, controller.minDamage);

                if (isBattleWon) {
                    if (controller.bestEnd == null || (getStateScore(this) > getStateScore(controller.bestEnd))
                            && saveState.getPlayerHealth() >= 1) {
                        controller.minDamage = damage;
                        controller.bestEnd = this;
                    }
                } else if (AbstractDungeon.player.isDead || AbstractDungeon.player.isDying) {
                    if (controller.deathNode == null ||
                            (controller.deathNode != null && controller.deathNode.saveState.turn < saveState.turn)) {
                        System.err.println("dead 2");
                        controller.deathNode = this;
                        isDone = true;
                    }
                }

                minDamage = damage;
                isDone = true;
                return null;
            }
        }

        if (commands.size() == 0) {
            isDone = true;
            return null;
        }


        Command toExecute = commands.get(commandIndex);
        commandIndex++;
        isDone = commandIndex >= commands.size();

        return toExecute;
    }

    private boolean isBattleOver() {
        return AbstractDungeon.getCurrRoom().monsters.areMonstersBasicallyDead();
    }

    private void populateCommands() {
        commands = CommandList.getAvailableCommands((card1, card2) -> {
            if (IronCladPlayOrder.CARD_RANKS.containsKey(card1
                    .cardID) && IronCladPlayOrder.CARD_RANKS
                    .containsKey(card2.cardID)) {
                return IronCladPlayOrder.CARD_RANKS
                        .get(card1.cardID) - IronCladPlayOrder.CARD_RANKS
                        .get(card2.cardID);
            } else if (SilentPlayOrder.CARD_RANKS.containsKey(card1
                    .cardID) && SilentPlayOrder.CARD_RANKS
                    .containsKey(card2.cardID)) {
                return SilentPlayOrder.CARD_RANKS
                        .get(card1.cardID) - SilentPlayOrder.CARD_RANKS
                        .get(card2.cardID);
            } else if (DefectPlayOrder.CARD_RANKS
                    .containsKey(card1.cardID) && DefectPlayOrder.CARD_RANKS
                    .containsKey(card2.cardID)) {
                return DefectPlayOrder.CARD_RANKS
                        .get(card1.cardID) - DefectPlayOrder.CARD_RANKS
                        .get(card2.cardID);
            }

            return card2.costForTurn - card1.costForTurn;
        });
    }

    public boolean isDone() {
        return isDone;
    }

    public int getPlayerHealth() {
        return saveState.getPlayerHealth();
    }

    public static int getPlayerDamage(StateNode node) {
        return node.controller.startingHealth - node.saveState.getPlayerHealth();
    }

    /**
     * This is used for end of battle score, only health matters here
     */
    public static int getStateScore(StateNode node) {
        int totalRitualDaggerDamage = 0;

        for (CardState card : node.saveState.playerState.hand) {
            switch (card.cardId) {
                case RitualDagger.ID:
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                default:
                    break;
            }
        }

        for (CardState card : node.saveState.playerState.drawPile) {
            switch (card.cardId) {
                case RitualDagger.ID:
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                default:
                    break;
            }
        }

        for (CardState card : node.saveState.playerState.discardPile) {
            switch (card.cardId) {
                case RitualDagger.ID:
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                default:
                    break;
            }
        }

        for (CardState card : node.saveState.playerState.exhaustPile) {
            switch (card.cardId) {
                case RitualDagger.ID:
                    totalRitualDaggerDamage += card.baseDamage;
                    break;
                default:
                    break;
            }
        }

        int ritualDaggerScore = totalRitualDaggerDamage * 80;
        int lessonLearnedScore = node.saveState.lessonLearnedCount * 100;

        return node.saveState.playerState.gold * 2 +
                ritualDaggerScore +
                getPlayerDamage(node) * -1 +
                TurnNode.getPotionScore(node.saveState) +
                TurnNode.getRelicScore(node.saveState) +
                lessonLearnedScore;
    }
}
