package battleaimod;

import basemod.BaseMod;
import basemod.ReflectionHacks;
import basemod.TopPanelItem;
import basemod.interfaces.OnStartBattleSubscriber;
import basemod.interfaces.PostInitializeSubscriber;
import basemod.interfaces.PostUpdateSubscriber;
import basemod.interfaces.PreUpdateSubscriber;
import battleaimod.battleai.BattleAiController;
import battleaimod.battleai.CommandRunnerController;
import battleaimod.networking.AiClient;
import battleaimod.networking.AiServer;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.evacipated.cardcrawl.modthespire.ui.ModSelectWindow;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.cards.CardGroup;
import com.megacrit.cardcrawl.cards.blue.Hologram;
import com.megacrit.cardcrawl.cards.blue.Seek;
import com.megacrit.cardcrawl.cards.colorless.*;
import com.megacrit.cardcrawl.cards.green.Nightmare;
import com.megacrit.cardcrawl.cards.green.ToolsOfTheTrade;
import com.megacrit.cardcrawl.cards.purple.*;
import com.megacrit.cardcrawl.cards.red.Headbutt;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.helpers.CardLibrary;
import com.megacrit.cardcrawl.helpers.PotionHelper;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.monsters.exordium.Lagavulin;
import com.megacrit.cardcrawl.monsters.exordium.SlimeBoss;
import com.megacrit.cardcrawl.relics.*;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.ThoughtBubble;
import ludicrousspeed.LudicrousSpeedMod;
import savestate.PotionState;
import savestate.SaveState;
import savestate.SaveStateMod;
import savestate.fastobjects.ScreenShakeFast;

import java.io.IOException;
import java.util.HashMap;
import java.util.Iterator;

import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.actionManager;

@SpireInitializer
public class BattleAiMod implements PostInitializeSubscriber, PostUpdateSubscriber, OnStartBattleSubscriber, PreUpdateSubscriber {
    public final static long MESSAGE_TIME_MILLIS = 1500L;

    public static String steveMessage = null;

    public static boolean forceStep = false;
    public static AiServer aiServer = null;
    public static AiClient aiClient = null;
    public static boolean shouldStartAiFromServer = false;
    public static BattleAiController battleAiController = null;

    public static CommandRunnerController rerunController = null;

    public static SaveState saveState;
    public static boolean goFast = false;
    public static boolean shouldStartClient = false;
    public static boolean isServer;

    public BattleAiMod() {
        BaseMod.subscribe(this);
        BaseMod.subscribe(new LudicrousSpeedMod());

        // Shut off the MTS console window, It increasingly slows things down
        ModSelectWindow window = ReflectionHacks.getPrivateStatic(Loader.class, "ex");
        window.removeAll();

        CardCrawlGame.screenShake = new ScreenShakeFast();
    }

    public static void sendGameState() {
        if (battleAiController != null) {
            battleAiController.step();

            if (battleAiController.isDone()) {
                battleAiController = null;
            }
        }
    }

    public static void initialize() {
        BattleAiMod mod = new BattleAiMod();
    }

    @Override
    public void receivePostInitialize() {
        CardLibrary.cards.remove(Headbutt.ID);

        // Silent
        CardLibrary.cards.remove(Nightmare.ID);
        CardLibrary.cards.remove(ToolsOfTheTrade.ID);

        // Defect
        CardLibrary.cards.remove(Seek.ID);
        CardLibrary.cards.remove(Hologram.ID);

        // Colorless
        // TODO AttackFromDeckToHandAction
        CardLibrary.cards.remove(SecretWeapon.ID);
        CardLibrary.cards.remove(SecretTechnique.ID);
        CardLibrary.cards.remove(TheBomb.ID);
        CardLibrary.cards.remove(Forethought.ID);
        CardLibrary.cards.remove(Discovery.ID);

        // Watcher
        // Scry
        CardLibrary.cards.remove(CutThroughFate.ID);
        CardLibrary.cards.remove(JustLucky.ID);
        CardLibrary.cards.remove(ThirdEye.ID);
        CardLibrary.cards.remove(Foresight.ID);
        CardLibrary.cards.remove(Weave.ID);
        CardLibrary.cards.remove(ForeignInfluence.ID);
        CardLibrary.cards.remove(Omniscience.ID);

        CardLibrary.cards.remove(Wish.ID);
        CardLibrary.cards.remove(Meditate.ID);
        CardLibrary.cards.remove(Nirvana.ID);

        HashMap<String, AbstractRelic> sharedRelics = ReflectionHacks
                .getPrivateStatic(RelicLibrary.class, "sharedRelics");
        sharedRelics.put(NilrysCodex.ID, RelicLibrary.getRelic("Enchiridion").makeCopy());
        sharedRelics.put(Toolbox.ID, RelicLibrary.getRelic(MedicalKit.ID).makeCopy());

        sharedRelics.remove(GamblingChip.ID);
        sharedRelics.remove(PrayerWheel.ID);
        sharedRelics.remove(Orrery.ID);

        HashMap<String, AbstractRelic> purpleRelics = ReflectionHacks
                .getPrivateStatic(RelicLibrary.class, "purpleRelics");
        purpleRelics.remove(GoldenEye.ID);
        purpleRelics.remove(Melange.ID);

        Iterator<String> actualPotions = PotionHelper.potions.iterator();
        while (actualPotions.hasNext()) {
            String potionId = actualPotions.next();
            for (String toRemove : PotionState.UNPLAYABLE_POTIONS) {
                if (potionId.equals(toRemove)) {
                    actualPotions.remove();
                    continue;
                }
            }
        }

        String isServerFlag = System.getProperty("isServer");

        if (isServerFlag != null) {
            if (Boolean.parseBoolean(isServerFlag)) {
                BattleAiMod.isServer = true;
            }
        }

        ReflectionHacks.setPrivateStaticFinal(BaseMod.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(TheSpecimen.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(AbstractDungeon.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(Lagavulin.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(SlimeBoss.class, "logger", new SilentLogger());
        ReflectionHacks.setPrivateStaticFinal(CardGroup.class, "logger", new SilentLogger());

        if (isServer) {
            Settings.MASTER_VOLUME = 0;
            Settings.isDemo = true;
            goFast = true;
            SaveStateMod.shouldGoFast = true;
            LudicrousSpeedMod.plaidMode = true;


            Settings.ACTION_DUR_XFAST = 0.001F;
            Settings.ACTION_DUR_FASTER = 0.002F;
            Settings.ACTION_DUR_FAST = 0.0025F;
            Settings.ACTION_DUR_MED = 0.005F;
            Settings.ACTION_DUR_LONG = .01F;
            Settings.ACTION_DUR_XLONG = .015F;

            if (aiServer == null) {
                aiServer = new AiServer();
            }
        } else {
            Settings.MASTER_VOLUME = .0F;
        }

        CardCrawlGame.sound.update();
        setUpOptionsMenu();
    }

    public void receivePostUpdate() {
        if (steveMessage != null) {
            String messageToDisplay = " Processing... NL " + steveMessage;
            steveMessage = null;

            AbstractDungeon.effectList
                    .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, (float) MESSAGE_TIME_MILLIS / 1000.F, messageToDisplay, true));

        }
        if (battleAiController == null && shouldStartAiFromServer) {
            shouldStartAiFromServer = false;
            LudicrousSpeedMod.controller = battleAiController = new BattleAiController(saveState);
        }
    }

    private void setUpOptionsMenu() {
        BaseMod.addTopPanelItem(new StartAiClientTopPanel());
    }

    public class StartAiClientTopPanel extends TopPanelItem {
        public static final String ID = "battleaimod:startclient";

        public StartAiClientTopPanel() {
            super(new Texture("Icon.png"), ID);
        }

        @Override
        protected void onClick() {
            if (aiClient == null) {
                try {
                    aiClient = new AiClient();
                } catch (IOException e) {
                    e.printStackTrace();
                }
            }

            if (aiClient != null) {
                aiClient.sendState();
            }
        }
    }

    @Override
    public void receiveOnBattleStart(AbstractRoom abstractRoom) {
//        shouldStartClient = true;
    }

    @Override
    public void receivePreUpdate() {
        if (battleAiController == null && shouldStartAiFromServer) {
            shouldStartAiFromServer = false;
            battleAiController = new BattleAiController(saveState);
            LudicrousSpeedMod.controller = battleAiController;
        }

        if (actionManager.actions.isEmpty() && actionManager.currentAction == null) {
            if (shouldStartClient) {
                shouldStartClient = false;
                AbstractDungeon.effectList
                        .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 2.0F, "Hello World", true));

                actionManager.actions.add(new WaitAction(2.0F));
                actionManager.actions.add(new AbstractGameAction() {
                    @Override
                    public void update() {
                        AbstractDungeon.effectList
                                .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 3.0F, "Here we go", true));

                        if (BattleAiMod.aiClient == null) {
                            try {
                                BattleAiMod.aiClient = new AiClient();
                            } catch (IOException e) {
                                e.printStackTrace();
                            }
                        }

                        isDone = true;

                        if (BattleAiMod.aiClient != null) {
                            BattleAiMod.aiClient.sendState();
                        }
                    }
                });
            }
        }
    }
}
