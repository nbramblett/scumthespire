package battleaimod.savestate;

import basemod.ReflectionHacks;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.megacrit.cardcrawl.helpers.RelicLibrary;
import com.megacrit.cardcrawl.relics.AbstractRelic;
import com.megacrit.cardcrawl.relics.Necronomicon;
import com.megacrit.cardcrawl.relics.Orichalcum;
import com.megacrit.cardcrawl.relics.RedSkull;

public class RelicState {
    private final String relicId;
    private final int counter;
    private final boolean grayscale;

    private final boolean orichalcumTrigger;

    private final boolean necronomiconActivated;

    private final boolean redSkullIsActive;

    public RelicState(AbstractRelic relic) {
        this.relicId = relic.relicId;
        this.counter = relic.counter;
        this.grayscale = relic.grayscale;

        if (relic instanceof Orichalcum) {
            this.orichalcumTrigger = ((Orichalcum) relic).trigger;
        } else {
            orichalcumTrigger = false;
        }

        if (relic instanceof Necronomicon) {
            this.necronomiconActivated = ReflectionHacks
                    .getPrivate(relic, Necronomicon.class, "activated");
        } else {
            necronomiconActivated = false;
        }

        if (relic instanceof RedSkull) {
            this.redSkullIsActive = ReflectionHacks
                    .getPrivate(relic, RedSkull.class, "isActive");
        } else {
            this.redSkullIsActive = false;
        }
    }

    public RelicState(String jsonString) {
        JsonObject parsed = new JsonParser().parse(jsonString).getAsJsonObject();

        this.relicId = parsed.get("relic_id").getAsString();
        this.counter = parsed.get("counter").getAsInt();
        this.grayscale = parsed.get("grayscale").getAsBoolean();

        this.orichalcumTrigger = parsed.get("orichalcum_trigger").getAsBoolean();

        this.necronomiconActivated = parsed.get("necronomicon_activated").getAsBoolean();

        this.redSkullIsActive = parsed.get("red_skull_is_active").getAsBoolean();
    }

    public AbstractRelic loadRelic() {
        AbstractRelic result = RelicLibrary.getRelic(relicId).makeCopy();
        result.counter = counter;
        result.grayscale = grayscale;

        if (result instanceof Orichalcum) {
            ((Orichalcum) result).trigger = orichalcumTrigger;
        }

        if (result instanceof Necronomicon) {
            ReflectionHacks
                    .setPrivate(result, Necronomicon.class, "activated", necronomiconActivated);
        }

        if (result instanceof RedSkull) {
            ReflectionHacks
                    .setPrivate(result, RedSkull.class, "isActive", redSkullIsActive);
        }

        return result;
    }

    public String encode() {
        JsonObject relicStateJson = new JsonObject();

        relicStateJson.addProperty("relic_id", relicId);
        relicStateJson.addProperty("counter", counter);
        relicStateJson.addProperty("grayscale", grayscale);

        relicStateJson.addProperty("orichalcum_trigger", orichalcumTrigger);
        relicStateJson.addProperty("necronomicon_activated", necronomiconActivated);

        relicStateJson.addProperty("red_skull_is_active", redSkullIsActive);

        return relicStateJson.toString();
    }
}
