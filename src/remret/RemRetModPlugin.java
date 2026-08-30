package remret;

import com.fs.starfarer.api.BaseModPlugin;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.listeners.ListenerManagerAPI;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;

public class RemRetModPlugin extends BaseModPlugin {

	@Override
	public void onGameLoad(boolean newGame) {
		// crisis registration hook, for when the hostile activity intel is
		// created or rebuilt (transient: re-added every load, never saved)
		ListenerManagerAPI listeners = Global.getSector().getListenerManager();
		if (!listeners.hasListenerOfClass(RemRetSetupListener.class)) {
			listeners.addListener(new RemRetSetupListener(), true);
		}

		// battle watcher for Remnant kills (transient campaign listener)
		Global.getSector().addTransientListener(new RemRetKillTracker());

		// nexus trace clock: reveals Nexus map markers while hunters are active
		Global.getSector().addTransientScript(new RemRetNexusTraceScript());

		// planetkiller handover offer (transient; only acts while the player
		// holds the weapon and no permanent pather agreement exists)
		Global.getSector().addTransientScript(new RemRetPkHandoverScript());

		// repair saves where Nexus intel predates the constructor registering it
		// as a script - without this, destroyed Nexuses were never noticed and
		// the trace clock stalled on the first traced target forever
		RemRetNexusIntel.repairScriptRegistration();

		// repair saves where a previous handover left the permanent agreement
		// marker set but the flag everything reads expired out from under it
		RemRetPkHandoverIntel.repairPermanentAgreement();

		// if this save already has the crisis intel running, join it now
		HostileActivityEventIntel intel = HostileActivityEventIntel.get();
		if (intel != null) {
			RemRetSetupListener.addToIntel(intel);
		}

		// opt-in debug/testing actions (grant colony, inject grudge)
		RemRetDebug.runOnGameLoad();
	}
}
