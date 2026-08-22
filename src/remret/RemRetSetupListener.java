package remret;

import com.fs.starfarer.api.campaign.listeners.ColonyCrisesSetupListener;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;

/**
 * Registers the retribution crisis with the hostile activity event. The game
 * calls finishedAddingCrisisFactors() whenever the event intel (re)builds its
 * crisis list - this is the officially supported hook for mod-added crises.
 */
public class RemRetSetupListener implements ColonyCrisesSetupListener {

	public void finishedAddingCrisisFactors(HostileActivityEventIntel intel) {
		addToIntel(intel);
	}

	public static void addToIntel(HostileActivityEventIntel intel) {
		if (intel == null) return;
		if (RemRetNetwork.isNeutralized()) return; // the war is over, permanently
		if (intel.getActivityOfClass(RemRetHostileActivityFactor.class) != null) return;

		RemRetHostileActivityFactor factor = new RemRetHostileActivityFactor(intel);
		intel.addActivity(factor, new RemRetActivityCause(intel));
	}
}
