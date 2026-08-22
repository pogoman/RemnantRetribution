package remret;

import com.fs.starfarer.api.EveryFrameScript;
import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.impl.campaign.rulecmd.HA_CMD;
import com.fs.starfarer.api.util.IntervalUtil;

/**
 * Watches for the player holding the planetkiller (however acquired) and
 * offers the Path handover while no permanent pather agreement exists.
 * Transient; re-added by the mod plugin on every load.
 */
public class RemRetPkHandoverScript implements EveryFrameScript {

	protected IntervalUtil interval = new IntervalUtil(2f, 3f);

	@Override
	public boolean isDone() {
		return false;
	}

	@Override
	public boolean runWhilePaused() {
		return false;
	}

	@Override
	public void advance(float amount) {
		float days = Global.getSector().getClock().convertToDays(amount);
		interval.advance(days);
		if (!interval.intervalElapsed()) return;

		RemRetPkHandoverIntel existing = getIntel();

		if (!RemRetConfig.pkHandover()
				|| HA_CMD.playerPatherAgreementIsPermanent()
				|| !RemRetPkHandoverIntel.playerHasPk()) {
			if (existing != null && !existing.isEnding() && !existing.isEnded()) {
				existing.endAfterDelay();
			}
			return;
		}

		if (existing == null) {
			RemRetPkHandoverIntel intel = new RemRetPkHandoverIntel();
			Global.getSector().getIntelManager().addIntel(intel);
			RemRetDebug.log("Path handover offer opened (player holds the planetkiller).");
		}
	}

	protected RemRetPkHandoverIntel getIntel() {
		for (com.fs.starfarer.api.campaign.comm.IntelInfoPlugin p
				: Global.getSector().getIntelManager().getIntel(RemRetPkHandoverIntel.class)) {
			return (RemRetPkHandoverIntel) p;
		}
		return null;
	}
}
