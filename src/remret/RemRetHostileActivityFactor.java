package remret;

import java.awt.Color;
import java.util.Random;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CampaignFleetAPI;
import com.fs.starfarer.api.campaign.SectorEntityToken;
import com.fs.starfarer.api.campaign.StarSystemAPI;
import com.fs.starfarer.api.campaign.comm.IntelInfoPlugin.ListInfoMode;
import com.fs.starfarer.api.campaign.econ.MarketAPI;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.FleetTypes;
import com.fs.starfarer.api.impl.campaign.ids.Stats;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseEventIntel.EventStageData;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseFactorTooltip;
import com.fs.starfarer.api.impl.campaign.intel.events.BaseHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.HAERandomEventData;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel.Stage;
import com.fs.starfarer.api.impl.campaign.intel.MessageIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.RemnantHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.intel.group.FGRaidAction.FGRaidType;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel;
import com.fs.starfarer.api.impl.campaign.intel.group.FleetGroupIntel.FGIEventListener;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI;
import com.fs.starfarer.api.impl.campaign.intel.group.GenericRaidFGI.GenericRaidParams;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission;
import com.fs.starfarer.api.impl.campaign.missions.FleetCreatorMission.FleetStyle;
import com.fs.starfarer.api.impl.campaign.missions.hub.HubMissionWithTriggers.ComplicationRepImpact;
import com.fs.starfarer.api.impl.campaign.rulecmd.salvage.MarketCMD.BombardType;
import com.fs.starfarer.api.ui.LabelAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI.TooltipCreator;
import com.fs.starfarer.api.util.Misc;
import com.fs.starfarer.api.util.WeightedRandomPicker;

/**
 * The retribution crisis: hunter-killer groups in player systems while the
 * grudge is live, and - if this factor wins the crisis roll - a full
 * retaliation strike aimed at saturation-bombarding the player's largest
 * colony, launched from the nearest surviving Remnant presence in the sector.
 */
public class RemRetHostileActivityFactor extends BaseHostileActivityFactor implements FGIEventListener {

	public static final String INVASION_KEY = "$remret_invasion";
	public static final String PROBE_KEY = "$remret_probe";
	public static final String HUNTER_FLEET_FLAG = "$remretHunter";

	public RemRetHostileActivityFactor(HostileActivityEventIntel intel) {
		super(intel);
	}

	public String getProgressStr(BaseEventIntel intel) {
		return "";
	}

	public String getDesc(BaseEventIntel intel) {
		return "Remnant retribution";
	}

	public String getNameForThreatList(boolean first) {
		return "Remnant hunters";
	}

	public Color getDescColor(BaseEventIntel intel) {
		if (getProgress(intel) <= 0) {
			return Misc.getGrayColor();
		}
		return Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor();
	}

	public Color getNameColor(float mag) {
		if (mag <= 0f) {
			return Misc.getGrayColor();
		}
		return Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor();
	}

	public TooltipCreator getMainRowTooltip(BaseEventIntel intel) {
		return new BaseFactorTooltip() {
			public void createTooltip(TooltipMakerAPI tooltip, boolean expanded, Object tooltipParam) {
				tooltip.addPara("The Remnant network holds a grudge against your polity for the "
						+ "destruction of its kind. Hunter-killer groups have been dispatched to "
						+ "your space, and worse may be in the making.", 0f);
			}
		};
	}

	public boolean shouldShow(BaseEventIntel intel) {
		return getProgress(intel) > 0;
	}

	@Override
	public int getMaxNumFleets(StarSystemAPI system) {
		return RemRetConfig.maxFleets();
	}

	@Override
	public float getSpawnInHyperProbability(StarSystemAPI system) {
		return 0.1f;
	}

	@Override
	public CampaignFleetAPI createFleet(StarSystemAPI system, Random random) {
		float mag = getEffectMagnitude(system);

		int difficulty = 3 + Math.round(mag * 8f) + random.nextInt(3);
		if (difficulty > 10) difficulty = 10;

		FleetCreatorMission m = new FleetCreatorMission(random);
		m.beginFleet();

		m.createQualityFleet(difficulty, Factions.REMNANTS, system.getLocation());
		m.triggerSetFleetType(FleetTypes.PATROL_LARGE);
		m.triggerSetPatrol();
		m.triggerMakeHostile();
		m.triggerMakeLowRepImpact();
		m.triggerFleetAllowLongPursuit();

		CampaignFleetAPI fleet = m.createFleet();

		if (fleet != null) {
			fleet.setName("Hunter-Killer Group");
			fleet.setNoFactionInName(false);
			// set directly: trigger-based flags don't persist on the fleet
			// (they're tied to the throwaway mission's lifecycle)
			fleet.getMemoryWithoutUpdate().set(HUNTER_FLEET_FLAG, true);
		}

		return fleet;
	}

	public void addBulletPointForEvent(HostileActivityEventIntel intel, EventStageData stage, TooltipMakerAPI info,
			ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		Color c = Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor();
		if (stage.id == Stage.MINOR_EVENT) {
			info.addPara("Impending Remnant reconnaissance", initPad, tc, c, "Remnant");
		} else {
			info.addPara("Impending Remnant retaliation", initPad, tc, c, "Remnant");
		}
	}

	public void addBulletPointForEventReset(HostileActivityEventIntel intel, EventStageData stage, TooltipMakerAPI info,
			ListInfoMode mode, boolean isUpdate, Color tc, float initPad) {
		if (stage.id == Stage.MINOR_EVENT) {
			info.addPara("Remnant reconnaissance averted", tc, initPad);
		} else {
			info.addPara("Remnant retaliation averted", tc, initPad);
		}
	}

	public void addStageDescriptionForEvent(HostileActivityEventIntel intel, EventStageData stage, TooltipMakerAPI info) {
		float small = 8f;
		float opad = 10f;

		Color c = Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor();

		String targetName = "one of your colonies";
		if (stage.rollData instanceof HAERandomEventData) {
			HAERandomEventData data = (HAERandomEventData) stage.rollData;
			if (data.custom instanceof MarketAPI) {
				targetName = ((MarketAPI) data.custom).getName();
			}
		}

		if (stage.id == Stage.MINOR_EVENT) {
			info.addPara("Remnant fleet movements suggest a reconnaissance-in-force is being "
					+ "assembled to probe the defenses of " + targetName + " - the network "
					+ "assessing your polity's strength before committing to true retaliation.",
					small, Misc.getNegativeHighlightColor(), "reconnaissance-in-force");

			LabelAPI minorLabel = info.addPara("The recon elements will disrupt what they can and "
					+ "withdraw. Whether you defeat them or not, the network learns what it "
					+ "came to learn - the true retaliation comes later.", opad);
			minorLabel.setHighlight("the true retaliation comes later");
			minorLabel.setHighlightColors(Misc.getNegativeHighlightColor());

			addBorder(info, Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor());
			return;
		}

		boolean deathRattle = RemRetConfig.canNeutralize() && RemRetNetwork.liveNexusCount() == 0;

		if (deathRattle) {
			info.addPara("With its command network decapitated, what remains of the Remnant is "
					+ "converging for a final strike: every surviving asset, marshaled with orders "
					+ "to saturation-bombard " + targetName + ".",
					small, Misc.getNegativeHighlightColor(), "saturation-bombard");

			LabelAPI label = info.addPara("Defeat this final convergence, and the Remnant threat "
					+ "to your polity ends permanently.", opad);
			label.setHighlight("ends permanently");
			label.setHighlightColors(Misc.getPositiveHighlightColor());

			stage.beginResetReqList(info, true, "crisis", opad);
			info.addPara("The %s is defeated", 0f, c, "final convergence");
			stage.endResetReqList(info, false, "crisis", -1, -1);
		} else {
			info.addPara("Your fleet's sustained destruction of Remnant forces has provoked a response. "
					+ "Intel evaluates a high probability that the Remnant network is assembling a "
					+ "retaliation force with orders to saturation-bombard " + targetName + ".",
					small, Misc.getNegativeHighlightColor(), "saturation-bombard");

			LabelAPI label = info.addPara("Defeating the retaliation force will cause the network to "
					+ "substantially lower its threat assessment of your polity - though continuing "
					+ "to destroy Remnant forces will rebuild it in time.", opad);
			label.setHighlight("substantially lower its threat assessment");
			label.setHighlightColors(Misc.getPositiveHighlightColor());

			StarSystemAPI sourceSys = getSystemById(RemRetData.getImpendingSourceSystemId());
			if (sourceSys != null) {
				LabelAPI src = info.addPara("The strike is being marshaled at a Remnant Nexus in the "
						+ sourceSys.getNameWithLowercaseType() + ". Destroy it before the fleet "
						+ "launches and the retaliation is averted entirely. Its location has been "
						+ "marked.", opad);
				src.setHighlight(sourceSys.getNameWithLowercaseType(), "averted entirely");
				src.setHighlightColors(c, Misc.getPositiveHighlightColor());
			}

			stage.beginResetReqList(info, true, "crisis", opad);
			info.addPara("The %s is defeated", 0f, c, "retaliation force");
			if (sourceSys != null) {
				info.addPara("The %s is destroyed before launch", 0f, c, "source Nexus");
			}
			stage.endResetReqList(info, false, "crisis", -1, -1);
		}

		addBorder(info, Global.getSector().getFaction(Factions.REMNANTS).getBaseUIColor());
	}

	public String getEventStageIcon(HostileActivityEventIntel intel, EventStageData stage) {
		return Global.getSector().getFaction(Factions.REMNANTS).getCrest();
	}

	public TooltipCreator getStageTooltipImpl(final HostileActivityEventIntel intel, final EventStageData stage) {
		if (stage.id == Stage.MINOR_EVENT) {
			return getDefaultEventTooltip("Remnant reconnaissance-in-force", intel, stage);
		}
		if (stage.id == Stage.HA_EVENT) {
			return getDefaultEventTooltip("Remnant retaliation", intel, stage);
		}
		return null;
	}

	public float getEventFrequency(HostileActivityEventIntel intel, EventStageData stage) {
		if (stage.id == Stage.MINOR_EVENT) {
			if (RemRetNetwork.isNeutralized()) return 0f;
			if (RemRetNetwork.isPendingDeathRattle()) return 0f;

			float grudge = RemRetData.getGrudge();
			if (grudge < RemRetConfig.minGrudgeForProbe()) return 0f;

			if (isProbeActive() || isInvasionActive()) return 0f;

			MarketAPI target = findAttackTarget(intel, stage);
			if (target == null || target.getStarSystem() == null) return 0f;

			if (findInvasionSource(target) == null) return 0f;

			return (10f + grudge / 50f) * RemRetNetwork.pressureMult();
		}
		if (stage.id == Stage.HA_EVENT) {
			if (RemRetNetwork.isNeutralized()) return 0f;
			// the final convergence launches directly from advance(), not via the bar
			if (RemRetNetwork.isPendingDeathRattle()) return 0f;

			float grudge = RemRetData.getGrudge();
			if (grudge < RemRetConfig.minGrudgeForInvasion()) return 0f;

			if (isInvasionActive()) return 0f;

			MarketAPI target = findAttackTarget(intel, stage);
			if (target == null || target.getStarSystem() == null) return 0f;

			if (findInvasionSource(target) == null) return 0f;

			return (10f + grudge / 25f) * RemRetNetwork.pressureMult();
		}
		return 0f;
	}

	public static boolean isInvasionActive() {
		return isRaidRefActive(INVASION_KEY);
	}

	public static boolean isProbeActive() {
		return isRaidRefActive(PROBE_KEY);
	}

	protected static boolean isRaidRefActive(String memoryKey) {
		Object curr = Global.getSector().getMemoryWithoutUpdate().get(memoryKey);
		if (curr instanceof GenericRaidFGI) {
			GenericRaidFGI raid = (GenericRaidFGI) curr;
			if (!raid.isEnded() && !raid.isEnding()) {
				return true;
			}
		}
		return false;
	}

	public void rollEvent(HostileActivityEventIntel intel, EventStageData stage) {
		HAERandomEventData data = new HAERandomEventData(this, stage);
		MarketAPI target = findAttackTarget(intel, stage);
		if (target == null) return;
		data.custom = target;
		stage.rollData = data;

		// the retaliation (but not the recon probe) can be averted by destroying
		// the Nexus it is marshaling from - lock that Nexus in and reveal it
		if (stage.id == Stage.HA_EVENT) {
			lockAndRevealSource(target);
		}

		intel.sendUpdateIfPlayerHasIntel(data, false);
	}

	/**
	 * Locks in the surviving Nexus this impending retaliation will launch from
	 * and reveals its location, so the player can race to destroy it and avert
	 * the strike before it fires. Does nothing (leaves no lock) if the strike
	 * would come from a dormant Remnant system with no destructible Nexus.
	 */
	protected void lockAndRevealSource(MarketAPI target) {
		RemRetData.setImpendingSourceSystemId(null);

		SectorEntityToken source = findInvasionSource(target);
		if (source == null || !(source.getContainingLocation() instanceof StarSystemAPI)) return;

		StarSystemAPI sys = (StarSystemAPI) source.getContainingLocation();
		if (!RemRetNetwork.isNexusAlive(sys)) return; // dormant fallback / dead station

		RemRetData.setImpendingSourceSystemId(sys.getId());

		// priority reveal: always show the source of an imminent strike, even if
		// the one-at-a-time trace throttle would otherwise hold it back
		if (RemRetConfig.nexusIntel() && !RemRetNexusIntel.existsFor(sys)) {
			Global.getSector().getIntelManager().addIntel(new RemRetNexusIntel(sys));
			RemRetDebug.log("Impending retaliation source revealed: " + sys.getBaseName() + ".");
		}
	}

	protected static StarSystemAPI getSystemById(String id) {
		if (id == null) return null;
		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (system.getId().equals(id)) return system;
		}
		return null;
	}

	public boolean fireEvent(HostileActivityEventIntel intel, EventStageData stage) {
		MarketAPI target = null;
		if (stage.rollData instanceof HAERandomEventData &&
				((HAERandomEventData) stage.rollData).custom instanceof MarketAPI) {
			target = (MarketAPI) ((HAERandomEventData) stage.rollData).custom;
		}
		if (target == null || !target.isPlayerOwned() || target.getStarSystem() == null) {
			target = findAttackTarget(intel, stage);
		}
		if (target == null || target.getStarSystem() == null) return false;

		if (stage.id == Stage.MINOR_EVENT) {
			SectorEntityToken source = findInvasionSource(target);
			if (source == null) return false;
			stage.rollData = null;
			return startProbe(source, target, getRandomizedStageRandom(5));
		}

		// retaliation
		SectorEntityToken source;
		String lockedId = RemRetData.getImpendingSourceSystemId();
		if (lockedId != null) {
			// a source Nexus was locked in at roll time. If it has since been
			// destroyed, the strike is AVERTED - it does not relocate to another
			// Nexus. Strict liveness matters here: killing the source's escorts
			// can push the bar to 600 inside the same battle callback that killed
			// the station, while its wreck is still in the system's fleet list -
			// a mere presence check would launch the strike from the corpse.
			StarSystemAPI lockedSys = getSystemById(lockedId);
			if (!RemRetNetwork.isNexusAlive(lockedSys)) {
				stage.rollData = null;
				RemRetData.setImpendingSourceSystemId(null);
				announceAverted();
				return false;
			}
			source = RemnantHostileActivityFactor.getRemnantNexus(lockedSys);
		} else {
			// no destructible Nexus was locked (strike sourced from a dormant
			// Remnant system) - cannot be averted this way
			source = findInvasionSource(target);
			if (source == null) return false;
		}

		stage.rollData = null;
		RemRetData.setImpendingSourceSystemId(null);

		// with the network decapitated, any strike that still fires is a final
		// convergence - defeating it neutralizes the Remnants for good
		boolean deathRattle = RemRetConfig.canNeutralize() && RemRetNetwork.liveNexusCount() == 0;

		return startInvasion(source, target, getRandomizedStageRandom(3), deathRattle);
	}

	/**
	 * The minor event: a reconnaissance-in-force - a handful of Remnant fleets
	 * probing the target system's defenses and raiding for intel, without
	 * bombardment. The network's warning shot before true retaliation.
	 */
	public boolean startProbe(SectorEntityToken source, MarketAPI target, Random random) {
		GenericRaidParams params = new GenericRaidParams(new Random(random.nextLong()), true);

		params.makeFleetsHostile = true;
		params.remnant = true;

		params.factionId = Factions.REMNANTS;

		MarketAPI fake = Global.getFactory().createMarket("remret_probe_source_" + source.getId(),
				source.getName(), 3);
		fake.setPrimaryEntity(source);
		fake.setFactionId(Factions.REMNANTS);
		fake.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat("remret", 1f);

		params.source = fake;

		params.prepDays = 7f + 7f * random.nextFloat();
		params.payloadDays = 30f + 10f * random.nextFloat();

		// a raid, not a bombardment: disrupt, assess, withdraw
		params.raidParams.where = target.getStarSystem();
		params.raidParams.type = FGRaidType.SEQUENTIAL;
		params.raidParams.tryToCaptureObjectives = false;
		params.raidParams.allowedTargets.add(target);
		params.raidParams.allowNonHostileTargets = true;
		// a probe assessing your response SHOULD be baited into fights - that's
		// the point of it (the strike, by contrast, is single-minded)
		params.raidParams.doNotGetSidetracked = false;
		params.noun = "reconnaissance";
		params.forcesNoun = "Remnant recon elements";

		params.style = FleetStyle.QUALITY;
		params.repImpact = ComplicationRepImpact.NONE;

		float totalDifficulty = RemRetConfig.probeDifficulty() * RemRetNetwork.pressureMult();

		while (totalDifficulty > 0) {
			int min = 4;
			int maxSize = 6;
			int diff = min + random.nextInt(maxSize - min + 1);
			params.fleetSizes.add(diff);
			totalDifficulty -= diff;
		}

		GenericRaidFGI raid = new GenericRaidFGI(params);
		raid.setListener(this);
		Global.getSector().getIntelManager().addIntel(raid);

		Global.getSector().getMemoryWithoutUpdate().set(PROBE_KEY, raid);

		RemRetDebug.log("Reconnaissance-in-force launched at " + target.getName() + ".");

		return true;
	}

	public MarketAPI findAttackTarget(HostileActivityEventIntel intel, EventStageData stage) {
		WeightedRandomPicker<MarketAPI> picker = new WeightedRandomPicker<MarketAPI>(getRandomizedStageRandom(3));
		for (MarketAPI market : Misc.getPlayerMarkets(false)) {
			if (market.getStarSystem() == null) continue;
			float size = market.getSize();
			picker.add(market, size * size * size);
		}
		return picker.pick();
	}

	/**
	 * The strike is launched from the nearest surviving Remnant Nexus; failing
	 * that, from the nearest Remnant-themed star system (dormant forces,
	 * reactivated). Returns null only if the Remnants have been eradicated
	 * from the sector entirely.
	 */
	public static SectorEntityToken findInvasionSource(MarketAPI target) {
		if (target == null || target.getStarSystem() == null) return null;

		SectorEntityToken best = null;
		float bestDist = Float.MAX_VALUE;

		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			CampaignFleetAPI nexus = RemnantHostileActivityFactor.getRemnantNexus(system);
			if (nexus == null) continue;
			float dist = Misc.getDistanceLY(system.getLocation(), target.getStarSystem().getLocation());
			if (dist < bestDist) {
				bestDist = dist;
				best = nexus;
			}
		}
		if (best != null) return best;

		for (StarSystemAPI system : Global.getSector().getStarSystems()) {
			if (!system.hasTag(Tags.THEME_REMNANT)) continue;
			if (system.getCenter() == null) continue;
			float dist = Misc.getDistanceLY(system.getLocation(), target.getStarSystem().getLocation());
			if (dist < bestDist) {
				bestDist = dist;
				best = system.getCenter();
			}
		}
		return best;
	}

	public boolean startInvasion(SectorEntityToken source, MarketAPI target, Random random, boolean deathRattle) {
		GenericRaidParams params = new GenericRaidParams(new Random(random.nextLong()), true);

		params.makeFleetsHostile = true;
		params.remnant = true;

		params.factionId = Factions.REMNANTS;

		MarketAPI fake = Global.getFactory().createMarket("remret_source_" + source.getId(),
				source.getName(), 3);
		fake.setPrimaryEntity(source);
		fake.setFactionId(Factions.REMNANTS);
		fake.getStats().getDynamic().getMod(Stats.FLEET_QUALITY_MOD).modifyFlat("remret", 1f);

		params.source = fake;

		params.prepDays = 14f + 14f * random.nextFloat();
		params.payloadDays = 45f + 15f * random.nextFloat();

		params.raidParams.where = target.getStarSystem();
		params.raidParams.type = FGRaidType.SEQUENTIAL;
		params.raidParams.tryToCaptureObjectives = false;
		params.raidParams.allowedTargets.add(target);
		params.raidParams.allowNonHostileTargets = true;
		params.raidParams.setBombardment(BombardType.SATURATION);
		// single-minded: prosecute the bombardment, don't chase the player around
		// the system (fleets still join battles that come within ~1000 units)
		params.raidParams.doNotGetSidetracked = true;
		params.noun = deathRattle ? "convergence" : "retaliation";
		params.forcesNoun = "Remnant forces";

		params.style = FleetStyle.QUALITY;
		params.repImpact = ComplicationRepImpact.NONE;

		float grudge = RemRetData.getGrudge();
		float totalDifficulty;
		if (deathRattle) {
			// everything the shattered network has left, in one strike
			totalDifficulty = RemRetConfig.invasionMaxDifficulty() * RemRetConfig.deathRattleMult();
		} else {
			totalDifficulty = RemRetConfig.invasionBaseDifficulty()
					+ grudge * RemRetConfig.invasionDifficultyPerGrudge();

			float max = RemRetConfig.invasionMaxDifficulty();
			if (totalDifficulty > max) totalDifficulty = max;

			float escalation = 1f + RemRetData.getInvasionsDefeated()
					* RemRetConfig.escalationPerDefeat();
			totalDifficulty *= escalation;

			// a fragmented network can muster less
			totalDifficulty *= RemRetNetwork.pressureMult();
		}

		totalDifficulty -= 10;
		params.fleetSizes.add(10);

		while (totalDifficulty > 0) {
			int min = 6;
			int maxSize = 10;
			int diff = min + random.nextInt(maxSize - min + 1);
			params.fleetSizes.add(diff);
			totalDifficulty -= diff;
		}

		RemRetStrikeFGI raid = new RemRetStrikeFGI(params);
		raid.setListener(this);
		Global.getSector().getIntelManager().addIntel(raid);

		Global.getSector().getMemoryWithoutUpdate().set(INVASION_KEY, raid);
		RemRetNetwork.setDeathRattleStrike(deathRattle);

		RemRetDebug.log((deathRattle ? "FINAL CONVERGENCE" : "Retaliation strike") + " launched at "
				+ target.getName() + " (grudge " + (int) grudge + ").");

		return true;
	}

	public void reportFGIAborted(FleetGroupIntel intel) {
		Object probe = Global.getSector().getMemoryWithoutUpdate().get(PROBE_KEY);
		if (intel == probe) {
			// the recon was a warning shot; driving it off changes little -
			// the network got the data it came for either way
			Global.getSector().getMemoryWithoutUpdate().unset(PROBE_KEY);
			RemRetDebug.log("Reconnaissance force defeated.");
			return;
		}

		Global.getSector().getMemoryWithoutUpdate().unset(INVASION_KEY);

		if (RemRetNetwork.isDeathRattleStrike()) {
			// the network's last gasp has been defeated: permanent neutralization
			RemRetNetwork.neutralize();
			removeFromEvent();
			return;
		}

		float remaining = RemRetConfig.grudgeRemainingAfterDefeat();
		RemRetData.setGrudge(RemRetData.getGrudge() * remaining);
		RemRetData.incrInvasionsDefeated();
		RemRetDebug.log("Retaliation strike defeated; grudge reduced to " + (int) RemRetData.getGrudge() + ".");
	}

	/** Pulls this crisis out of the colony crises event entirely. */
	protected void removeFromEvent() {
		EventStageData stage = this.intel.getDataFor(Stage.HA_EVENT);
		if (stage != null && stage.rollData instanceof HAERandomEventData &&
				((HAERandomEventData) stage.rollData).factor == this) {
			this.intel.resetHA_EVENT();
		}
		this.intel.removeActivityCause(RemRetHostileActivityFactor.class, RemRetActivityCause.class);
	}

	@Override
	public void advance(float amount) {
		super.advance(amount);

		// the last Nexus just died: launch the final convergence directly,
		// without waiting for the crisis bar
		if (RemRetNetwork.isPendingDeathRattle() && !RemRetNetwork.isNeutralized()
				&& !isInvasionActive()) {
			MarketAPI target = findAttackTarget(intel, null);
			if (target != null && target.getStarSystem() != null) {
				SectorEntityToken source = findDeathRattleSource(target);
				if (source != null) {
					RemRetNetwork.setPendingDeathRattle(false);
					if (startInvasion(source, target, getRandomizedStageRandom(4), true)) {
						RemRetNetwork.announceDeathRattle(target.getName());
					}
				}
			}
			// no valid target/source yet (e.g. no colonies): stays pending,
			// retried next frame batch until conditions are met
		}

		// while a retaliation is impending, watch the Nexus it will launch from
		EventStageData stage = intel.getDataFor(Stage.HA_EVENT);
		if (stage != null && stage.rollData instanceof HAERandomEventData &&
				((HAERandomEventData) stage.rollData).factor == this) {
			HAERandomEventData data = (HAERandomEventData) stage.rollData;

			// a retaliation that became impending before a source was locked -
			// e.g. the mod was installed or updated mid-campaign with a crisis
			// already in progress - gets its source established and revealed now,
			// so it can be averted like any other. (Fresh crises lock at roll.)
			String sourceId = RemRetData.getImpendingSourceSystemId();
			if (sourceId == null && data.custom instanceof MarketAPI) {
				lockAndRevealSource((MarketAPI) data.custom);
				sourceId = RemRetData.getImpendingSourceSystemId();
			}

			// aversion: the locked source Nexus has been destroyed before launch
			if (sourceId != null) {
				StarSystemAPI sys = getSystemById(sourceId);
				if (!RemRetNetwork.isNexusAlive(sys)) {
					avertImpendingStrike();
					return;
				}
			}

			// fallback: no Remnant presence left anywhere to source the strike
			if (data.custom instanceof MarketAPI) {
				MarketAPI target = (MarketAPI) data.custom;
				if (findInvasionSource(target) == null) {
					intel.resetHA_EVENT();
					RemRetData.setImpendingSourceSystemId(null);
				}
			}
		}
	}

	/**
	 * The player destroyed the source Nexus in time: cancel the impending strike.
	 * Used by advance(), which must reset the bar itself. (fireEvent's caller
	 * resets the bar, so that path calls announceAverted() directly.)
	 */
	protected void avertImpendingStrike() {
		intel.resetHA_EVENT();
		RemRetData.setImpendingSourceSystemId(null);
		announceAverted();
	}

	protected void announceAverted() {
		MessageIntel msg = new MessageIntel(
				"The Remnant Nexus marshaling the impending retaliation has been destroyed. "
				+ "The strike against your colonies has been averted.",
				Misc.getPositiveHighlightColor());
		msg.setIcon(Global.getSector().getFaction(Factions.REMNANTS).getCrest());
		Global.getSector().getCampaignUI().addMessage(msg);

		RemRetDebug.log("Impending retaliation averted - source Nexus destroyed before launch.");
	}

	/**
	 * The final convergence marshals at the system where the last Nexus died,
	 * if known - otherwise the nearest Remnant-themed system.
	 */
	protected SectorEntityToken findDeathRattleSource(MarketAPI target) {
		String systemId = RemRetNetwork.getLastNexusSystemId();
		if (systemId != null) {
			for (StarSystemAPI system : Global.getSector().getStarSystems()) {
				if (systemId.equals(system.getId()) && system.getCenter() != null) {
					return system.getCenter();
				}
			}
		}
		return findInvasionSource(target);
	}
}
