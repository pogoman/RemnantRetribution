package remret;

import java.awt.Color;
import java.util.Set;

import com.fs.starfarer.api.Global;
import com.fs.starfarer.api.campaign.CargoAPI.CargoItemType;
import com.fs.starfarer.api.campaign.SpecialItemData;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.CustomRepImpact;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActionEnvelope;
import com.fs.starfarer.api.impl.campaign.CoreReputationPlugin.RepActions;
import com.fs.starfarer.api.impl.campaign.ids.Factions;
import com.fs.starfarer.api.impl.campaign.ids.Items;
import com.fs.starfarer.api.impl.campaign.ids.Tags;
import com.fs.starfarer.api.impl.campaign.intel.BaseIntelPlugin;
import com.fs.starfarer.api.impl.campaign.intel.events.HALuddicPathDealFactor;
import com.fs.starfarer.api.impl.campaign.intel.events.HostileActivityEventIntel;
import com.fs.starfarer.api.impl.campaign.intel.events.LuddicPathHostileActivityFactor;
import com.fs.starfarer.api.impl.campaign.rulecmd.HA_CMD;
import com.fs.starfarer.api.ui.IntelUIAPI;
import com.fs.starfarer.api.ui.SectorMapAPI;
import com.fs.starfarer.api.ui.TooltipMakerAPI;
import com.fs.starfarer.api.util.Misc;

/**
 * While the player carries the planetkiller, the Luddic Path reaches out
 * through intermediaries: hand over the God-cursed totem, and holy peace is
 * yours until the End of Days - the same permanent pather agreement the
 * vanilla questline grants, without the questline. Managed by
 * {@link RemRetPkHandoverScript}.
 */
public class RemRetPkHandoverIntel extends BaseIntelPlugin {

	public static final String BUTTON_HANDOVER = "remret_button_pk_handover";

	/**
	 * The flag vanilla's questline handover sets; rules.csv reads it as
	 * $player.turnedInPlanetkiller and uses it to stop offering the weapon to
	 * the other factions.
	 */
	public static final String TURNED_IN_PK = "$turnedInPlanetkiller";

	/** Mirrors vanilla's "AdjustRep luddic_path 10" on the questline handover. */
	public static final float HANDOVER_REP_GAIN = 0.1f;

	/**
	 * Works around a vanilla bug in {@link HA_CMD#setPatherAgreement}: on the
	 * permanent path it sets $patherAgreementPermanent with no expiry, but then
	 * still writes $patherAgreement with the passed-in duration - which is 0
	 * for "permanent", so that key expires on the next memory advance. Vanilla
	 * never hits this (its only caller is the mega-tithe, always with a
	 * positive duration; the questline sets both keys straight from rules.csv),
	 * but we do.
	 * <p>
	 * Everything that actually reads the agreement - Pather cells going
	 * dormant, the Path crisis factor, the crisis-bar negation - checks
	 * $patherAgreement, not the "permanent" marker. Without this the handover
	 * buys nothing but a line of flavour text on the faction screen.
	 * <p>
	 * Rewrites the key with no expiry at all. Unsets first so no expiry left
	 * over from the previous write can survive the overwrite.
	 */
	public static void setPermanentAgreement() {
		HA_CMD.setPatherAgreement(true, 0f);
		Global.getSector().getPlayerMemoryWithoutUpdate().unset(HA_CMD.PATHER_AGREEMENT);
		Global.getSector().getPlayerMemoryWithoutUpdate().set(HA_CMD.PATHER_AGREEMENT, true);
	}

	/**
	 * Load-time repair for saves where the handover already fired and the flag
	 * has since expired. The permanent marker set without the flag it is
	 * supposed to accompany is unreachable in vanilla - clearing an agreement
	 * goes through setPatherAgreement(false, ..), which unsets both - so this
	 * only ever fires on a save this mod broke. See
	 * {@link #setPermanentAgreement()}.
	 */
	public static void repairPermanentAgreement() {
		if (!HA_CMD.playerPatherAgreementIsPermanent()) return;
		if (HA_CMD.playerHasPatherAgreement()) return;
		setPermanentAgreement();
		RemRetDebug.log("Restored expired $patherAgreement (permanent agreement was set).");
	}

	public static boolean playerHasPk() {
		return Global.getSector().getPlayerFleet().getCargo()
				.getQuantity(CargoItemType.SPECIAL,
						new SpecialItemData(Items.PLANETKILLER, null)) > 0;
	}

	@Override
	public String getName() {
		return "The Path Covets the Planetkiller";
	}

	@Override
	public String getIcon() {
		String crest = Global.getSector().getFaction(Factions.LUDDIC_PATH).getCrest();
		if (crest != null) return crest;
		return super.getIcon();
	}

	@Override
	protected void addBulletPoints(TooltipMakerAPI info, ListInfoMode mode, boolean isUpdate,
								   Color tc, float initPad) {
		info.addPara("Hand it over: permanent peace with the Luddic Path", initPad, tc,
				Misc.getHighlightColor(), "permanent peace");
	}

	@Override
	public boolean hasSmallDescription() {
		return true;
	}

	@Override
	public void createSmallDescription(TooltipMakerAPI info, float width, float height) {
		float opad = 10f;
		Color h = Misc.getHighlightColor();

		info.addPara("Word of the planetkiller in your hold has reached the Luddic Path. "
				+ "Through intermediaries - a shrine curate here, a shepherd's courier there - "
				+ "an understanding is quietly offered: surrender the God-cursed totem to the "
				+ "True Faithful, and your worlds will know holy peace until the End of Days.",
				opad);
		info.addPara("This is the same %s the Path grants in exchange for the weapon by other "
				+ "means: cells on your colonies stand down, permanently. The Path does not "
				+ "want to use it. A demonic thing feared is a relic; a relic held is power.",
				opad, h, "permanent agreement");
		info.addPara("Or keep it. It is, after all, only a weapon.", opad);

		if (playerHasPk()) {
			info.addButton("Surrender the planetkiller to the Path", BUTTON_HANDOVER,
					width - 2 * opad, 20f, opad * 2f);
		} else {
			info.addPara("The planetkiller is no longer in your fleet's cargo.", opad,
					Misc.getNegativeHighlightColor(), h, "");
		}
	}

	@Override
	public void buttonPressConfirmed(Object buttonId, IntelUIAPI ui) {
		if (!BUTTON_HANDOVER.equals(buttonId)) {
			super.buttonPressConfirmed(buttonId, ui);
			return;
		}
		if (!playerHasPk()) {
			ui.updateUIForItem(this);
			return;
		}

		CustomRepImpact impact = new CustomRepImpact();
		impact.delta = HANDOVER_REP_GAIN;
		Global.getSector().adjustPlayerReputation(
				new RepActionEnvelope(RepActions.CUSTOM, impact, null, true),
				Factions.LUDDIC_PATH);

		Global.getSector().getPlayerFleet().getCargo().removeItems(CargoItemType.SPECIAL,
				new SpecialItemData(Items.PLANETKILLER, null), 1);
		setPermanentAgreement();
		Global.getSector().getPlayerMemoryWithoutUpdate().set(TURNED_IN_PK, true);

		// the rest of what vanilla's "HA_CMD gavePKToPather" does: credit the
		// colony crisis bar for the deal, and call off a Path attack that has
		// already been rolled or launched
		HostileActivityEventIntel ha = HostileActivityEventIntel.get();
		if (ha != null) {
			ha.addFactor(new HALuddicPathDealFactor(-Global.getSettings().getInt("HA_givePK")));
		}
		LuddicPathHostileActivityFactor.avertOrAbortAttack();

		com.fs.starfarer.api.impl.campaign.intel.MessageIntel msg =
				new com.fs.starfarer.api.impl.campaign.intel.MessageIntel(
						"The planetkiller passes into the keeping of the True Faithful. "
						+ "\"Holy peace be upon you, until the End of Days when all shall "
						+ "be judged.\" Luddic Path cells across your worlds stand down - "
						+ "permanently.", Misc.getTextColor());
		String crest = Global.getSector().getFaction(Factions.LUDDIC_PATH).getCrest();
		if (crest != null) msg.setIcon(crest);
		Global.getSector().getCampaignUI().addMessage(msg);

		RemRetDebug.log("Planetkiller handed to the Path: permanent pather agreement set.");
		dismiss();
		// the entry is gone from the list now, so rebuild the screen rather
		// than asking it to refresh an item that no longer exists
		ui.recreateIntelUI();
	}

	/**
	 * Clears this entry out of the intel screen for good.
	 * <p>
	 * endAfterDelay() alone is not enough here: it only marks the intel as
	 * ending and leaves a 3-day countdown for advance() to run down, and this
	 * plugin never gets advanced - saves show it parked at the initial 3.0
	 * indefinitely while vanilla intel in the same save counts down normally.
	 * endImmediately() sets ended synchronously instead, and the explicit
	 * removeIntel() (a no-op if it has already gone) means the entry does not
	 * depend on that countdown at all.
	 * <p>
	 * Safe to call on an entry that is already ending or removed, which is what
	 * lets {@link RemRetPkHandoverScript} clear the leftovers in saves made
	 * before this fix.
	 */
	public void dismiss() {
		endImmediately();
		Global.getSector().getIntelManager().removeIntel(this);
	}

	@Override
	public Set<String> getIntelTags(SectorMapAPI map) {
		Set<String> tags = super.getIntelTags(map);
		tags.add(Tags.INTEL_STORY);
		tags.add(Factions.LUDDIC_PATH);
		return tags;
	}
}
