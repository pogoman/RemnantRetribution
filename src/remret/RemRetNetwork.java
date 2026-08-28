package remret;

import java.awt.Color;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.util.Misc;

/**
 * Tracks the state of the Remnant command network: how many Nexus stations
 * survive versus the sector's baseline, the resulting "pressure" multiplier
 * (the crisis eases as the network fragments), and the permanent-neutralization
 * state reached by defeating the network's final convergence strike.
 */
public class RemRetNetwork {

	public static final String KEY_BASELINE = "remret_nexusBaseline";
	public static final String KEY_NEUTRALIZED = "remret_neutralized";
	public static final String KEY_PENDING_RATTLE = "remret_pendingDeathRattle";
	public static final String KEY_LAST_NEXUS_SYSTEM = "remret_lastNexusSystemId";
	/** Sector memory: set while the currently-active strike is the network's final convergence. */
	public static final String KEY_DEATH_RATTLE = "$remret_deathRattle";

	// campaign-clock cache for the sector-wide nexus census (statics are never
	// serialized, so this resets cleanly on every game load)
	private static int cachedCount = -1;
	private static long cachedAt = 0;

	/**
	 * Strict Nexus liveness. A station destroyed moments ago is still present in
	 * the system's fleet list (despawn happens later in the frame), so checking
	 * mere presence lets a wreck pass for a live Nexus during the very battle
	 * callback that killed it - vanilla's own convention for "destroyed station"
	 * is a station-mode fleet with no surviving members.
	 */
	public static boolean isNexusAlive(StarSystemAPI system) {
		if (system == null) return false;
		com.fs.starfarer.api.campaign.CampaignFleetAPI nexus =
				RemnantHostileActivityFactor.getRemnantNexus(system);
		if (nexus == null) return false;
		if (!nexus.isAlive()) return false;
		return !nexus.getFleetData().getMembersListCopy().isEmpty();
	}

	public static int liveNexusCount() {
		if (cachedCount >= 0 && Global.getSector().getClock().getElapsedDaysSince(cachedAt) < 1f) {
			return cachedCount;
		}
		int count = 0;
		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (isNexusAlive(system)) count++;
		}
		cachedCount = count;
		cachedAt = Global.getSector().getClock().getTimestamp();

		// establish/repair the baseline: live stations plus every nexus kill we
		// have recorded. Covers first-census on install, and other mods adding
		// stations later.
		int implied = count + RemRetData.getKills(RemRetData.KILL_NEXUS);
		if (implied > getBaseline()) {
			setBaseline(implied);
		}
		return count;
	}

	public static void invalidateCensus() {
		cachedCount = -1;
	}

	public static int getBaseline() {
		Object val = Global.getSector().getPersistentData().get(KEY_BASELINE);
		if (val instanceof Integer) return (Integer) val;
		return 0;
	}

	public static void setBaseline(int value) {
		Global.getSector().getPersistentData().put(KEY_BASELINE, value);
	}

	/** 1 = network untouched, 0 = every known Nexus destroyed. */
	public static float integrity() {
		int live = liveNexusCount();
		int baseline = getBaseline();
		if (baseline <= 0) return live > 0 ? 1f : 0f;
		float f = (float) live / (float) baseline;
		if (f < 0f) f = 0f;
		if (f > 1f) f = 1f;
		return f;
	}

	/**
	 * Multiplier applied to monthly progress, hunter-fleet magnitude, strike
	 * difficulty, and crisis-roll weight. Slides from 1 (network coherent) down
	 * to the configured floor as Nexuses die. Only active when permanent
	 * neutralization is enabled; the classic "machines don't forgive" mode keeps
	 * full pressure forever.
	 */
	public static float pressureMult() {
		if (!RemRetConfig.canNeutralize()) return 1f;
		float floor = RemRetConfig.fragmentationFloor();
		if (floor > 1f) floor = 1f;
		return floor + (1f - floor) * integrity();
	}

	public static boolean isNeutralized() {
		Object val = Global.getSector().getPersistentData().get(KEY_NEUTRALIZED);
		return val instanceof Boolean && (Boolean) val;
	}

	public static boolean isPendingDeathRattle() {
		Object val = Global.getSector().getPersistentData().get(KEY_PENDING_RATTLE);
		return val instanceof Boolean && (Boolean) val;
	}

	public static void setPendingDeathRattle(boolean value) {
		Global.getSector().getPersistentData().put(KEY_PENDING_RATTLE, value);
	}

	public static boolean isDeathRattleStrike() {
		return Global.getSector().getMemoryWithoutUpdate().getBoolean(KEY_DEATH_RATTLE);
	}

	public static void setDeathRattleStrike(boolean value) {
		if (value) {
			Global.getSector().getMemoryWithoutUpdate().set(KEY_DEATH_RATTLE, true);
		} else {
			Global.getSector().getMemoryWithoutUpdate().unset(KEY_DEATH_RATTLE);
		}
	}

	public static void setLastNexusSystemId(String systemId) {
		Global.getSector().getPersistentData().put(KEY_LAST_NEXUS_SYSTEM, systemId);
	}

	public static String getLastNexusSystemId() {
		Object val = Global.getSector().getPersistentData().get(KEY_LAST_NEXUS_SYSTEM);
		return val instanceof String ? (String) val : null;
	}

	/** Permanently ends the crisis: called when the final convergence strike is defeated. */
	public static void neutralize() {
		Global.getSector().getPersistentData().put(KEY_NEUTRALIZED, true);
		RemRetData.setGrudge(0f);
		setPendingDeathRattle(false);
		setDeathRattleStrike(false);

		MessageIntel msg = new MessageIntel(
				"With its command network destroyed and its final convergence defeated, "
				+ "the Remnant no longer poses a threat to your polity.",
				Misc.getBasePlayerColor());
		msg.setIcon(Global.getSector().getFaction(Factions.REMNANTS).getCrest());
		Global.getSector().getCampaignUI().addMessage(msg);

		RemRetDebug.log("Death rattle defeated - Remnant threat permanently neutralized.");
	}

	public static void announceDeathRattle(String targetName) {
		Color remnantColor = Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor();
		MessageIntel msg = new MessageIntel(
				"The destruction of the last Remnant Nexus has triggered a convergence: "
				+ "every surviving asset of the shattered network is being marshaled "
				+ "for a final strike against " + targetName + ".",
				remnantColor);
		msg.setIcon(Global.getSector().getFaction(Factions.REMNANTS).getCrest());
		Global.getSector().getCampaignUI().addMessage(msg);
	}
}
