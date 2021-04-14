package battleaimod;

import basemod.BaseMod;
import basemod.TopPanelItem;
import basemod.interfaces.*;
import battleaimod.battleai.BattleAiController;
import battleaimod.fastobjects.ScreenShakeFast;
import battleaimod.networking.AiClient;
import battleaimod.networking.AiServer;
import battleaimod.savestate.SaveState;
import com.badlogic.gdx.graphics.Texture;
import com.evacipated.cardcrawl.modthespire.Loader;
import com.evacipated.cardcrawl.modthespire.lib.SpireInitializer;
import com.megacrit.cardcrawl.actions.AbstractGameAction;
import com.megacrit.cardcrawl.actions.GameActionManager;
import com.megacrit.cardcrawl.actions.utility.WaitAction;
import com.megacrit.cardcrawl.characters.CharacterManager;
import com.megacrit.cardcrawl.core.CardCrawlGame;
import com.megacrit.cardcrawl.core.Settings;
import com.megacrit.cardcrawl.dungeons.AbstractDungeon;
import com.megacrit.cardcrawl.rooms.AbstractRoom;
import com.megacrit.cardcrawl.vfx.ThoughtBubble;

import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.IOException;

import static com.megacrit.cardcrawl.dungeons.AbstractDungeon.actionManager;

@SpireInitializer
public class BattleAiMod implements PostInitializeSubscriber, PostUpdateSubscriber, PostDungeonUpdateSubscriber, OnStartBattleSubscriber, PreUpdateSubscriber {
    public static boolean mustSendGameState = false;
    public static boolean readyForUpdate;
    public static boolean forceStep = false;
    private static AiServer aiServer = null;
    public static AiClient aiClient = null;
    public static boolean shouldStartAiFromServer = false;
    public static BattleAiController battleAiController = null;
    private static boolean canStep = false;
    public static SaveState saveState;
    public static boolean goFast = false;
    public static boolean shouldStartClient = false;

    public BattleAiMod() {
        CharacterManager desk;
        BaseMod.subscribe(this);
        BaseMod.subscribe(new SpeedController());
//        Settings.ACTION_DUR_XFAST = 0.01F;
//        Settings.ACTION_DUR_FASTER = 0.02F;
//        Settings.ACTION_DUR_FAST = 0.025F;
//        Settings.ACTION_DUR_MED = 0.05F;
//        Settings.ACTION_DUR_LONG = .10F;
//        Settings.ACTION_DUR_XLONG = .15F;

        String isServer = System.getProperty("isServer");
        if (isServer != null) {
            System.err.println("there's a boolean");
            if (Boolean.parseBoolean(isServer)) {
                Settings.isDemo = true;
                goFast = true;
            }
        }

        Loader load;

        CardCrawlGame.screenShake = new ScreenShakeFast();
    }

    public static void sendGameState() {
        if (battleAiController == null && shouldStartAiFromServer) {
            shouldStartAiFromServer = false;
            battleAiController = new BattleAiController(saveState);
        }
        if (battleAiController != null && BattleAiController.shouldStep()) {
//                if (canStep) {
            if (canStep || true) {
//                if (canStep || !battleAiController.runCommandMode) {
                canStep = false;

                battleAiController.step();
            }

            if (battleAiController.isDone) {
                battleAiController = null;
            }
        }
    }

    public static void initialize() {
        BattleAiMod mod = new BattleAiMod();

    }

    private static BufferedImage resizeImage(BufferedImage originalImage, int targetWidth, int targetHeight) throws IOException {
        int originaHeight = originalImage.getHeight();
        int originalWidth = originalImage.getWidth();

        double originalRatio = (double) originaHeight / (double) originalWidth;
        double targetRatio = (double) targetHeight / (double) targetWidth;

        int actualWidth;
        int actualHeight;

        if (originalRatio > targetRatio) {
            // match height
            actualHeight = Math.min(targetHeight, originaHeight);
            actualWidth = (int) (actualHeight / originalRatio);
        } else {
            actualWidth = Math.min(targetWidth, originalWidth);
            actualHeight = (int) (actualWidth * originalRatio);
        }

        BufferedImage resizedImage = new BufferedImage(actualWidth, actualHeight, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics2D = resizedImage.createGraphics();
        graphics2D.drawImage(originalImage, 0, 0, actualWidth, actualHeight, null);
        graphics2D.dispose();
        return resizedImage;
    }

    public void receivePostInitialize() {
        setUpOptionsMenu();
    }

    public void receivePostUpdate() {
        if (battleAiController == null && shouldStartAiFromServer) {
            shouldStartAiFromServer = false;
            battleAiController = new BattleAiController(saveState);
            readyForUpdate = true;
        }
        if (!mustSendGameState && GameStateListener.checkForMenuStateChange()) {
            mustSendGameState = true;
        }
    }

    public void receivePostDungeonUpdate() {
        if (GameStateListener.checkForDungeonStateChange()) {
            mustSendGameState = true;
            if (AbstractDungeon.actionManager != null && AbstractDungeon.actionManager.phase == GameActionManager.Phase.WAITING_ON_USER) {
                readyForUpdate = true;
            } else {
                System.err.println("but the action manager is doing stuff");
            }

        }
        if (AbstractDungeon.getCurrRoom().isBattleOver) {
            GameStateListener.signalTurnEnd();
        }
    }

    private void setUpOptionsMenu() {
//        BaseMod.addTopPanelItem(new StartAIPanel());

        BaseMod.addTopPanelItem(new StartAiServerTopPanel());
        BaseMod.addTopPanelItem(new StartAiClientTopPanel());

//        BaseMod.addTopPanelItem(new SaveStateTopPanel());
//        BaseMod.addTopPanelItem(new LoadStateTopPanel());
    }


    public class SaveStateTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:SaveState";

        public SaveStateTopPanel() {
            super(new Texture("save.png"), ID);
        }

        @Override
        protected void onClick() {
            System.out.println("you clicked on save");
            saveState = new SaveState();

            readyForUpdate = true;
        }
    }

    public class StartAIPanel extends TopPanelItem {
        public static final String ID = "yourmodname:SaveState";

        public StartAIPanel() {
            super(new Texture("save.png"), ID);
        }

        @Override
        protected void onClick() {
            battleAiController = new BattleAiController(new SaveState(), true);

            goFast = true;
            readyForUpdate = true;
        }
    }

    public class LoadStateTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:LoadState";

        public LoadStateTopPanel() {
            super(new Texture("Icon.png"), ID);
        }

        @Override
        protected void onClick() {
            readyForUpdate = true;
            receivePostUpdate();

            if (saveState != null) {
                saveState.loadState();
            }
        }
    }

    public class StepTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:Step";

        public StepTopPanel() {
            super(new Texture("Icon.png"), ID);
        }

        @Override
        protected void onClick() {
            canStep = true;
            readyForUpdate = true;
            receivePostUpdate();
        }
    }

    public class StartAiClientTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:Step";

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

    public class StartAiServerTopPanel extends TopPanelItem {
        public static final String ID = "yourmodname:startAi";

        public StartAiServerTopPanel() {
            super(new Texture("save.png"), ID);
        }

        @Override
        protected void onClick() {
            if (aiServer == null) {
                aiServer = new AiServer();
            }
        }
    }

    @Override
    public void receiveOnBattleStart(AbstractRoom abstractRoom) {
        System.err.println("this is happening");
//        shouldStartClient = true;
    }

    @Override
    public void receivePreUpdate() {
        if (actionManager.actions.isEmpty() && actionManager.currentAction == null) {
            if (shouldStartClient) {
                shouldStartClient = false;
                AbstractDungeon.effectList
                        .add(new ThoughtBubble(AbstractDungeon.player.dialogX, AbstractDungeon.player.dialogY, 2.0F, "Hello World", true));

                actionManager.actions.add(new WaitAction(2.0F));
                actionManager.actions.add(new AbstractGameAction() {
                    @Override
                    public void update() {
                        System.err.println("The action too");
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