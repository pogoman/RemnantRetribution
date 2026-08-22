package remret;

import com.fs.starfarer.api.Global;

/**
 * Central settings accessor. When LunaLib is enabled, values come live from the
 * in-game LunaSettings menu (data/config/LunaSettings.csv). Otherwise they fall
 * back to the bundled data/config/settings.json defaults, so the mod remains
 * fully functional standalone.
 *
 * All LunaLib class references are isolated in {@link LunaConfigBridge}, which
 * is only touched when LunaLib is actually present - so this class loads fine
 * with LunaLib absent.
 */
public class RemRetConfig {

	public static final String MOD_ID = "remret";

	private static Boolean lunaEnabled = null;

	public static boolean lunaAvailable() {
		if (lunaEnabled == null) {
			lunaEnabled = Global.getSettings().getModManager().isModEnabled("lunalib");
		}
		return lunaEnabled;
	}

	// ---- raw typed lookups (Luna first, settings.json fallback) ----

	private static int i(String key) {
		if (lunaAvailable()) {
			Integer v = LunaConfigBridge.getInt(key);
			if (v != null) return v;
		}
		return (int) Global.getSettings().getFloat(key);
	}

	private static float f(String key) {
		if (lunaAvailable()) {
			Float v = LunaConfigBridge.getFloat(key);
			if (v != null) return v;
		}
		return Global.getSettings().getFloat(key);
	}

	private static boolean b(String key, boolean def) {
		if (lunaAvailable()) {
			Boolean v = LunaConfigBridge.getBoolean(key);
			if (v != null) return v;
		}
		try {
			return Global.getSettings().getBoolean(key);
		} catch (Throwable t) {
			return def;
		}
	}

	// ---- kill values ----

	public static int pointsFrigate()   { return i("remret_pointsFrigate"); }
	public static int pointsDestroyer() { return i("remret_pointsDestroyer"); }
	public static int pointsCruiser()   { return i("remret_pointsCruiser"); }
	public static int pointsCapital()   { return i("remret_pointsCapital"); }
	public static int pointsNexus()     { return i("remret_pointsNexus"); }
	public static float pointsMult()    { return f("remret_pointsMult"); }

	// ---- threat / fleet presence ----

	public static float grudgePerMonthlyPoint()  { return f("remret_grudgePerMonthlyPoint"); }
	public static int   maxMonthlyProgress()     { return i("remret_maxMonthlyProgress"); }
	public static float minGrudgeForHunters()    { return f("remret_minGrudgeForHunters"); }
	public static float grudgeForFullMagnitude() { return f("remret_grudgeForFullMagnitude"); }
	public static float maxMagnitude()           { return f("remret_maxMagnitude"); }
	public static int   maxFleets()              { return i("remret_maxFleets"); }

	// ---- retaliation strike ----

	// ---- network fragmentation & permanent neutralization ----

	public static boolean canNeutralize()      { return b("remret_canNeutralize", true); }
	public static float fragmentationFloor()   { return f("remret_fragmentationFloor"); }
	public static float deathRattleMult()      { return f("remret_deathRattleMult"); }

	// ---- reconnaissance-in-force (minor event) ----

	public static float minGrudgeForProbe() { return f("remret_minGrudgeForProbe"); }
	public static float probeDifficulty()   { return f("remret_probeDifficulty"); }

	// ---- retaliation strike ----

	public static float minGrudgeForInvasion()       { return f("remret_minGrudgeForInvasion"); }
	public static float invasionBaseDifficulty()     { return f("remret_invasionBaseDifficulty"); }
	public static float invasionDifficultyPerGrudge(){ return f("remret_invasionDifficultyPerGrudge"); }
	public static float invasionMaxDifficulty()      { return f("remret_invasionMaxDifficulty"); }
	public static float escalationPerDefeat()        { return f("remret_escalationPerDefeat"); }
	public static float grudgeRemainingAfterDefeat() { return f("remret_grudgeRemainingAfterDefeat"); }

	// ---- nexus tracing intel ----

	public static boolean nexusIntel()    { return b("remret_nexusIntel", true); }
	public static float nexusTraceDays()  { return f("remret_nexusTraceDays"); }

	// ---- gate control fragments ----

	public static boolean gateFragments() { return b("remret_gateFragments", true); }
	public static boolean pkDrop()        { return b("remret_pkDrop", true); }
	public static boolean pkHandover()    { return b("remret_pkHandover", true); }

	// ---- debug ----

	public static boolean debugLogging()     { return b("remret_debugLogging", false); }
	public static boolean debugGrantColony() { return b("remret_debugGrantColony", false); }
	public static int     debugStartingGrudge() { return i("remret_debugStartingGrudge"); }
	public static boolean debugForceStrike() { return b("remret_debugForceStrike", false); }
}
