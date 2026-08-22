package remret;

import java.util.HashMap;
import java.util.Map;

import com.fs.starfarer.api.Global;

/**
 * Persistent bookkeeping for the retribution crisis. Everything lives in the
 * sector's persistent data map so it survives saves and does not depend on the
 * hostile activity intel existing (the player may rack up kills before founding
 * their first colony).
 */
public class RemRetData {

	public static final String KEY_GRUDGE = "remret_grudge";
	public static final String KEY_KILLS = "remret_kills";
	public static final String KEY_DEFEATS = "remret_invasionsDefeated";
	public static final String KEY_TRACE_TS = "remret_nexusTraceTimestamp";

	public static final String KILL_FRIGATE = "frigate";
	public static final String KILL_DESTROYER = "destroyer";
	public static final String KILL_CRUISER = "cruiser";
	public static final String KILL_CAPITAL = "capital";
	public static final String KILL_NEXUS = "nexus";

	public static float getGrudge() {
		Object val = Global.getSector().getPersistentData().get(KEY_GRUDGE);
		if (val instanceof Float) return (Float) val;
		return 0f;
	}

	public static void setGrudge(float value) {
		if (value < 0) value = 0;
		Global.getSector().getPersistentData().put(KEY_GRUDGE, value);
	}

	public static void addGrudge(float amount) {
		setGrudge(getGrudge() + amount);
	}

	@SuppressWarnings("unchecked")
	public static Map<String, Integer> getKillCounts() {
		Object val = Global.getSector().getPersistentData().get(KEY_KILLS);
		if (!(val instanceof Map)) {
			val = new HashMap<String, Integer>();
			Global.getSector().getPersistentData().put(KEY_KILLS, val);
		}
		return (Map<String, Integer>) val;
	}

	public static void addKill(String key) {
		Map<String, Integer> kills = getKillCounts();
		Integer curr = kills.get(key);
		if (curr == null) curr = 0;
		kills.put(key, curr + 1);
	}

	public static int getKills(String key) {
		Integer curr = getKillCounts().get(key);
		return curr == null ? 0 : curr;
	}

	public static int getInvasionsDefeated() {
		Object val = Global.getSector().getPersistentData().get(KEY_DEFEATS);
		if (val instanceof Integer) return (Integer) val;
		return 0;
	}

	public static void incrInvasionsDefeated() {
		Global.getSector().getPersistentData().put(KEY_DEFEATS, getInvasionsDefeated() + 1);
	}

	/** Campaign-clock timestamp of the last Nexus trace (0 = clock not started). */
	public static long getNexusTraceTimestamp() {
		Object val = Global.getSector().getPersistentData().get(KEY_TRACE_TS);
		if (val instanceof Long) return (Long) val;
		return 0L;
	}

	public static void setNexusTraceTimestamp(long timestamp) {
		Global.getSector().getPersistentData().put(KEY_TRACE_TS, timestamp);
	}
}
