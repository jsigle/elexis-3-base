/*******************************************************************************
 * Copyright (c) 2013-2021 J. Sigle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    J. Sigle - added a preference page to omnivore to control  a new automatic archiving functionality  
 *    N. Giger - separated parts of PreferencePage.java into Preferences.java and PreferenceConstants.java
 *    J. Sigle - review re-including what was missed when adopting omnivore_js back into omnivore
 *    
 *******************************************************************************/

package ch.elexis.omnivore.data;

import static ch.elexis.omnivore.PreferenceConstants.BASEPATH;
import static ch.elexis.omnivore.PreferenceConstants.DATE_MODIFIABLE;
import static ch.elexis.omnivore.PreferenceConstants.OmnivoreMax_Filename_Length_Min;
import static ch.elexis.omnivore.PreferenceConstants.OmnivoreMax_Filename_Length_Default;
import static ch.elexis.omnivore.PreferenceConstants.OmnivoreMax_Filename_Length_Max;
import static ch.elexis.omnivore.PreferenceConstants.PREFERENCE_DEST_DIR;
import static ch.elexis.omnivore.PreferenceConstants.PREFERENCE_SRC_PATTERN;
import static ch.elexis.omnivore.PreferenceConstants.PREF_DEST_DIR;
import static ch.elexis.omnivore.PreferenceConstants.PREF_MAX_FILENAME_LENGTH;
import static ch.elexis.omnivore.PreferenceConstants.PREF_SRC_PATTERN;
import static ch.elexis.omnivore.PreferenceConstants.STOREFS;
import static ch.elexis.omnivore.PreferenceConstants.STOREFSGLOBAL;
import static ch.elexis.omnivore.PreferenceConstants.nPREF_DEST_DIR;
import static ch.elexis.omnivore.PreferenceConstants.nPREF_SRC_PATTERN;

import org.eclipse.jface.preference.IPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.activator.CoreHubHelper;
import ch.elexis.core.ui.preferences.SettingsPreferenceStore;

public class Preferences {
	
	private static SettingsPreferenceStore fsSettingsStore;
	public static Logger log = LoggerFactory.getLogger(Preferences.class);
	
	/**
	 * reload the fs settings store
	 */
	private static void initGlobalConfig(){
		log.debug("Preferences.java initGlobalConfig: is fsSettingsStore == null ? <{}>", (fsSettingsStore == null));
			
		if (fsSettingsStore == null) {
			log.debug("Preferences.java initGlobalConfig: Workaround for bug ... 9501 running, probably resetting Omnivore user settings...");
			
			//  workaround for bug https://redmine.medelexis.ch/issues/9501 -> migrate old key to new key
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/store_in_fs_global",
				STOREFSGLOBAL, true);
			// bug from omnivore
			CoreHubHelper.transformConfigKey("ch.elexis.omnivore//store_in_fs_global",
				STOREFSGLOBAL, true);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/store_in_fs", STOREFS, true);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/store_in_fs", STOREFS, false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/basepath", BASEPATH, true);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/basepath", BASEPATH, false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/categories", STOREFS, false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/date_modifiable", STOREFS,
				false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/columnwidths", STOREFS,
				false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/savecolwidths", STOREFS,
				false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/sortdirection", STOREFS,
				false);
			CoreHubHelper.transformConfigKey("plugins/omnivore-direct/savesortdirection", STOREFS,
				false);
			
			boolean isGlobal = CoreHub.globalCfg.get(STOREFSGLOBAL, false);
			if (isGlobal) {
				fsSettingsStore = new SettingsPreferenceStore(CoreHub.globalCfg);
			} else {
				fsSettingsStore = new SettingsPreferenceStore(CoreHub.localCfg);
			}
		} else {
			log.debug("Preferences.java initGlobalConfig: fsSettingsStore already there, workaround not running");
		}
	}
	
	public static boolean storeInFilesystem(){
		initGlobalConfig();
		return fsSettingsStore.getBoolean(STOREFS);
	}
	
	public static String getBasepath(){
		initGlobalConfig();
		return fsSettingsStore.getString(BASEPATH);
	}
	
	public static boolean getDateModifiable(){
		return CoreHub.localCfg.get(DATE_MODIFIABLE, false);
	}
	
	// ----------------------------------------------------------------------------
		/**
		 * Returns a currently value from the preference store,
		 * observing default settings and min/max settings for that parameter
		 * 
		 * Can be called with an already available preferenceStore.
		 * If none is passed, one will be temporarily instantiated on the fly.
		 * 
		 * @return The requested integer parameter
		 * 
		 * @author Joerg Sigle
		 */
		
		public static Integer getOmnivoreMax_Filename_Length(){
			IPreferenceStore preferenceStore = new SettingsPreferenceStore(CoreHub.localCfg);
			int ret = preferenceStore.getInt(PREF_MAX_FILENAME_LENGTH);
					
			if (ret == 0) {
				ret = OmnivoreMax_Filename_Length_Default;
				
			if (ret < OmnivoreMax_Filename_Length_Min) {ret = OmnivoreMax_Filename_Length_Min;};
			if (ret > OmnivoreMax_Filename_Length_Max) {ret = OmnivoreMax_Filename_Length_Max;};

			}
			return ret;
			
		//TODO: The follwoing alternative implementation was included in 2.1.7js / Omnivore_js
		//but is now missing from Niklaus' port to Elexis 3.x
		//Please review and check whether it is definitely obsolete -
		//and then, why I did include it in my original implementation.
		//One possible reason might be (but this is a simple guess) because
		//might have given the preferences of my extended omnivore_js a different
		//"preferenceStore" than the one used by the original omnivore,
		//and Niklaus may have modified it to go into the "normal" PreferenceStore.
		//But well, it's 8 years later now, so who knows...
		//Original 2.1.7js / omnivore_js code is in:
		//jsigle@blackbox  Sa M‰r 27  15:28:44  /mnt/sdb3/Elexis-workspace/elexis-2.1.7-20130523/elexis-bootstrap-js-201712191036-last-20130605based-with-MSWord_js-as-used-by-JH-since-201701-before-gitpush  
		//$ kate ./elexis-base/ch.elexis.omnivore/src/ch/elexis/omnivore/preferences/PreferencePage.java
		//Current 3.7js / omnivore code is in:
		///mnt/think3/c/Users/jsigle/git/elexis/3.7/git/./elexis-3-base/bundles/ch.elexis.omnivore.ui/src/ch/elexis/omnivore/ui/preferences/
		//Preferences.java, PreferenceConstants.java, PreferencePage.java	
		/*
	    public static Integer getOmnivore_jsMax_Filename_Length(IPreferenceStore preferenceStore) {
				
			Integer Omnivore_jsMax_Filename_Length=Omnivore_jsMax_Filename_Length_Default;		//Start by establishing a valid default setting
			try {
				Omnivore_jsMax_Filename_Length=Integer.parseInt(preferenceStore.getString(PREF_MAX_FILENAME_LENGTH).trim());  //20130325js max filename length before error message is shown is now configurable
			} catch (Throwable throwable) {
			    //do not consume
			}
			if (Omnivore_jsMax_Filename_Length<Omnivore_jsMax_Filename_Length_Min) {Omnivore_jsMax_Filename_Length=Omnivore_jsMax_Filename_Length_Min;};
			if (Omnivore_jsMax_Filename_Length>Omnivore_jsMax_Filename_Length_Max) {Omnivore_jsMax_Filename_Length=Omnivore_jsMax_Filename_Length_Max;};
					
			return Omnivore_jsMax_Filename_Length;
		}
		 */
		
		}
		
	// ----------------------------------------------------------------------------
	/**
	 * Returns the number of rules to process for automatic archiving
	 * 
	 * @author Joerg Sigle
	 */
	
	public static Integer getOmnivorenRulesForAutoArchiving(){
		// 20130325js: For automatic archiving of incoming files:
		// The smaller number of entries available for Src and Dest determines
		// how many rule editing field pairs are provided on the actual preferences page, and
		// processed later on.
		// Now: Determine the number of slots for rule defining src and target strings,
		// and compute the actual number of rules to be the larger of these two.
		// Normally, they should be identical, if the dummy arrays used for initialization above
		// have had the same size.
		Integer nRules = nPREF_SRC_PATTERN;
		if (nPREF_DEST_DIR > nPREF_SRC_PATTERN) {
			nRules = nPREF_DEST_DIR;
		}
		
		return nRules;
	}
	
	// ----------------------------------------------------------------------------
	/**
	 * Returns configured content of rules for automatic archiving
	 * 
	 * @param Rule
	 *            number whose match pattern shall be retrieved. Cave: Visible only internally to
	 *            the program, this index is 0 based, whereas the preference page for the user shows
	 *            1-based "Rule n" headings.
	 * 
	 * @return Either null if the index is out of bounds, or if the respective String is technically
	 *         undefined (which should never be the case); or the respective String (which may also
	 *         be "", i.e. an empty string), if the user has cleared or left clear the respective
	 *         input field.
	 * 
	 * @author Joerg Sigle
	 */
	
	public static String getOmnivoreRuleForAutoArchivingSrcPattern(Integer i){
		if ((i < 0) || (i >= getOmnivorenRulesForAutoArchiving())) {
			return null;
		}
		
		// The preferences keys should already have been constructed by init - but if not, let's do
		// it here for the one that we need now:
		if (PREF_SRC_PATTERN[i].equals("")) {
			PREF_SRC_PATTERN[i] = PREFERENCE_SRC_PATTERN + i.toString().trim(); //$NON-NLS-1$
		}
		return CoreHub.localCfg.get(PREF_SRC_PATTERN[i], "").trim();
	}
	
	// ----------------------------------------------------------------------------
	/**
	 * Returns configured content of rules for automatic archiving
	 * 
	 * @param Rule
	 *            number whose destination directory shall be retrieved. Cave: Visible only
	 *            internally to the program, this index is 0 based, whereas the preference page for
	 *            the user shows 1-based "Rule n" headings.
	 * 
	 * @return Either null if the index is out of bounds, or if the respective String is technically
	 *         undefined (which should never be the case); or the respective String (which may also
	 *         be "", i.e. an empty string), if the user has cleared or left clear the respective
	 *         input field.
	 * 
	 * @author Joerg Sigle
	 */
	
	public static String getOmnivoreRuleForAutoArchivingDestDir(Integer i){
		if ((i < 0) || (i >= getOmnivorenRulesForAutoArchiving())) {
			return null;
		}
		
		// The preferences keys should already have been constructed by init - but if not, let's do
		// it here for the one that we need now:
		if (PREF_DEST_DIR[i].equals("")) {
			PREF_DEST_DIR[i] = PREFERENCE_DEST_DIR + i.toString().trim(); //$NON-NLS-1$
		}
		return CoreHub.localCfg.get(PREF_DEST_DIR[i], "").trim();
	}
	
	
	public static void setFsSettingStore(SettingsPreferenceStore settingsPreferenceStore){
		Preferences.fsSettingsStore = settingsPreferenceStore;
	}
	
	public static SettingsPreferenceStore getFsSettingsStore(){
		return fsSettingsStore;
	}
	
	// 20130411js: Make the temporary filename configurable
	// which is generated to extract the document from the database for viewing.
	// Thereby, simplify tasks like adding a document to an e-mail.
	// For most elements noted below, we can set the maximum number of digits
	// to be used (taken from the source from left); which character to add thereafter;
	// and whether to fill leading digits by a given character.
	// This makes a large number of options, so I construct
	// the required preference store keys from arrays.
	// Note: The DocHandle.getTitle() javadoc says that a document title in omnivore
	// may contain 80 chars.
	// To enable users to copy that in full, I allow for a max of 80 chars to be
	// specified as num_digits for *any* element.
	// Using all elements to that extent will return filename that's vastly too long,
	// but that will probably be handled elsewhere.
	public static final Integer nPreferences_cotf_element_digits_max = 80;
	public static final String PREFERENCE_COTF = "cotf_";
	public static final String[] PREFERENCE_cotf_elements = {
		"constant1", "PID", "fn", "gn", "dob", "dt", "dk", "dguid", "random", "constant2"
	};
	public static final String[] PREFERENCE_cotf_parameters = {
		"fill_leading_char", "num_digits", "add_trailing_char"
	};
	
	// The following unwanted characters, and all below codePoint=32 will be cleaned in advance.
	// Please see the getOmnivoreTemp_Filename_Element for details.
	// 20210327js: Slightly modified the string after comparison with the original 2.1.7js version.
	// Original Version in 2.1.7js:
	// static final String cotf_unwanted_chars="\\/:*?()+,;\"'¥`"; 
	// Last version found here:
	// public static final String cotf_unwanted_chars = "[\\:/:*?()+,\';\"\r\t\n¥`<>]";

	// TODO: 20210327js: Apparently, Niklaus replaced my cleanStringFromUnwantedCharsAndTrim
	// by a call to java.util.regex.Matcher in Utils.java - but not in all occasions.
	// So for now, I leave Niklaus' Variant here in addition to my original one plus <>
	// but we need to check whether his implementation does the same as mine.
	// Especially for all code points below 32, I'm afraid his will only find \r\t\n
	// CAVE: Whenever these lines are copy/pasted within Eclipse, Eclipse will duplicate EACH \ !!!
	// To ensure no call is missed, I renamed the two constants to ..._jsorig and ..._ngregex.
	// Maybe see (leftover in Niklaus' code in a comment below):
	// eclipse-javadoc:%E2%98%82=ch.elexis.core.data/%5C/usr%5C/lib%5C/jvm%5C/java-8-oracle%5C/jre%5C/lib%5C/rt.jar%3Cjava.util.regex(Matcher.class%E2%98%83Matcher~quoteReplacement~Ljava.lang.String;%E2%98%82java.lang.String

	public static final String cotf_unwanted_chars_jsorig = "\\/:*?()+,;\"'¥`<>";
	public static final String cotf_unwanted_chars_ngregex = "[\\:/:*?()+,\';\"\r\t\n¥`<>]";
	
	// Dank Eclipse's mglw. etwas √ºberm√§ssiger "Optimierung" werden externalisierte Strings nun als
	// Felder von Messges angesprochen - und nicht mehr wie zuvor √ºber einen als String √ºbergebenen key.
	// Insofern muss ich wohl zu den obigen Arrays korrespondierende Arrays vorab erstellen, welche
	// die jeweils zugeh√∂rigen Strings aus omnivore.Messages dann in eine definierte Reihenfolge bringen,
	// in der ich sie unten auch wieder gerne erhalten w√ºrde. Einfach per Programm at runtime die
	// keys generieren scheint nicht so leicht zu gehen.
	public static final String[] PREFERENCE_cotf_elements_messages = {
		Messages.Preferences_cotf_constant1,
		Messages.Preferences_cotf_pid,
		Messages.Preferences_cotf_fn,
		Messages.Preferences_cotf_gn,
		Messages.Preferences_cotf_dob,
		Messages.Preferences_cotf_dt,
		Messages.Preferences_cotf_dk,
		Messages.Preferences_cotf_dguid,
		Messages.Preferences_cotf_random,
		Messages.Preferences_cotf_constant2
	};
	public static final String[] PREFERENCE_cotf_parameters_messages = {
		Messages.Preferences_cotf_fill_lead_char,
		Messages.Preferences_cotf_num_digits,
		Messages.Preferences_cotf_add_trail_char
	};
}
