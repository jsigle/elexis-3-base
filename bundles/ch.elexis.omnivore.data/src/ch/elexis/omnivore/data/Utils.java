/*******************************************************************************
 * Copyright (c) 2017-2021, J. Sigle, Niklaus Giger and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    J. Sigle - Initial implementation in a private branch of Elexis 2.1
 *    N. Giger - Reworked for Elexis 3.4 including unit tests
 *    J. Sigle - Minor reviews and additional debug messages for Elexis 3.7
 *    
 *******************************************************************************/

package ch.elexis.omnivore.data;

import static ch.elexis.omnivore.PreferenceConstants.PREFBASE;
import static java.nio.file.StandardCopyOption.REPLACE_EXISTING;

import java.io.File;
import java.math.BigInteger;
import java.nio.file.Files;
import java.security.SecureRandom;
import java.text.MessageFormat;
import org.eclipse.jface.preference.IPreferenceStore;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.ui.preferences.SettingsPreferenceStore;
import ch.elexis.core.ui.util.SWTHelper;
import ch.rgw.tools.ExHandler;

public class Utils {
	private static Logger log = LoggerFactory.getLogger(Utils.class);
	
	/**
	 * 201xxxxxjs: After a file has been imported to Omnivore
	 * either by drag&drop or through Omnivore's built in File-Import dialog,
	 * automatically perform rule-based archiving of this file:
	 * scan a set of auto-archiving rules for a pattern matching this file's absolute path,
	 * and if a match is found, obtain a corresponding target directory and move the file there.
	 * If the corresponding target directory is empty, stop scanning the rules for this file.
	 * <br>
	 * Rules can be configured via the setting document exchange..Omnivore.
	 * 
	 * @param file, dh
	 *            The file last imported into omnivore (containing it's name etc.)
	 *            and its docHandle (containing title, patient, etc)
	 *            
	 * @return the newFile after successfully moving, or null if an error happend
	 * 
	 * @author Joerg Sigle, reworked for Elexis 3.5 by Niklaus Giger, for 3.7 by js
	 */
	static public File archiveFile(File file, DocHandle dh){
		//Check if a valid file has been supplied
		if ( file == null ) {
			log.warn("archiveFile was called with file == null - nothing to archive, aborting and returning null");
			return null;
		} 

		String fileAbsolutePath = file.getAbsolutePath();
		log.debug("Rule based auto-archiving routine invoked for file: {}", file.getName() );
		log.debug("fileAbsolutePath is: {}", fileAbsolutePath );
				
		//Obtain number of defined auto-archiving rules
		int nRules = Preferences.getOmnivorenRulesForAutoArchiving();
		int nRulesMatched = 0;
		
		if ( nRules <= 0 ) {
			log.debug("getOmnivorenRulesForAutoArchiving is: {} - no rules available, aborting and returning null" , nRules );
			return null;
		} else {
			log.debug("Number of available rules = nRules is: {}" , nRules);			
		}

		String SrcPattern = null;
		String DestDir = null;
		
		File newFile = null;
		String newFileAbsolutePath = null;
				
		try {
			//Process auto-archiving rules starting at rule no. 0
			//until the first match is found between SrcPattern[i] and fileAbsolutePath.			
			for (int i= 0; i < nRules; i++) {
				SrcPattern = Preferences.getOmnivoreRuleForAutoArchivingSrcPattern(i);
				DestDir = Preferences.getOmnivoreRuleForAutoArchivingDestDir(i);

				log.debug("Processing rule no. {}" , i);
				log.debug("SrcPattern[{}] = "+SrcPattern);				
				log.debug("DestDir[{}]    = "+DestDir);

				if ((SrcPattern != null) && (DestDir != null)
					&& ((SrcPattern != "" || DestDir != ""))) {
					
					if (fileAbsolutePath.contains(SrcPattern)) {
						log.debug("Rule {} SrcPattern {} matched by fileAbsolutePath", i, SrcPattern );
						nRulesMatched++;
						
						if (DestDir == "") {
							log.debug(
								"DestDir for matching rule is empty. No more rules will be evaluated for this file. Returning null.");
							return null;
						}
				
						newFile = new File(DestDir);
						if (newFile.isDirectory()) {
							newFile = new File(DestDir + File.separatorChar + file.getName());
						}
						
						newFileAbsolutePath = newFile.getAbsolutePath(); 
						
						if (newFile.isDirectory()) {
							log.debug("new File {} is a directory ; archiveFile not attempted. Returning null.",
									newFileAbsolutePath);
							SWTHelper.showError(Messages.DocHandle_MoveErrorCaption,
								MessageFormat.format(Messages.DocHandle_MoveErrorDestIsDir, DestDir,
									file.getName()));
							return null;
						} else {
						
							if (newFile.isFile()) {
								log.debug("new File {} already exits ; archiveFile not attempted. Returning null.",
										newFileAbsolutePath);
								SWTHelper.showError(Messages.DocHandle_MoveErrorCaption,
									MessageFormat.format(Messages.DocHandle_MoveErrorDestIsFile,
										DestDir, file.getName()));
								return null;
							} else {
								
								log.debug("Will move file {} {} to: {} {}",
										fileAbsolutePath, file.exists(),
										newFileAbsolutePath, newFile.exists());
							
								if (Files.move(file.toPath(), newFile.toPath(),
									REPLACE_EXISTING) != null) {
									log.debug("Archived incoming file {} to: {}",
											fileAbsolutePath, newFileAbsolutePath);
									return newFile;
								
								} else {
									log.debug("Failed archiveFile incoming file {} to: {}",
										fileAbsolutePath, newFileAbsolutePath);
									//TODO: Enable error message if auto archiving failed
									// SWTHelper.showError(Messages.DocHandle_MoveErrorCaption,Messages.DocHandle_MoveError);
									return null;
								}
							}
						}
					}
				}
			}
		} catch (Throwable throwable) {
			ExHandler.handle(throwable);
			if (file != null && newFile != null) {
				log.debug("Exception while moving file {} {} to: {} {}",
						fileAbsolutePath, file.exists(),
						newFileAbsolutePath, newFile.exists());
			} else {
				log.debug("Exception while moving file [{}] {} src {} dest {}",
					(file != null) ? file.getAbsolutePath() : "null",
					(file != null) ? file.exists() : "invalid", SrcPattern, DestDir);
			}
			SWTHelper.showError(Messages.DocHandle_MoveErrorCaption, Messages.DocHandle_MoveError);
			return null;
		}
		log.debug("archiveFile: ends, nRulesMatched = {}, returning newFile.", nRulesMatched);				
		return newFile;
	}	
	
	//----------------------------------------------------------------------------
	
	  /**
	   * Accepts some data to turn into a temporary filename element,
	   * and returns a formatted temporary filename element,
	   * observing current settings from the preference store,
	   * also observing default settings and min/max settings for that parameter
	   * 
	   * @param  Can be called with an already available preferenceStore.
	   * 		 If none is passed, one will be temporarily instantiated on the fly.
	   * 		 Also accepts <code>String element_key</code> to identify the requested filename element,
	   *         and the <code>String element_data</data> to be processed into a string constituting that filename element. 
	   *
	   * @return The requested filename element as a string.
	   * 
	   * @author Joerg Sigle, reworked for Elexis 3.5 by Niklaus Giger, for 3.7 by js
	   */

	// 20210327js: Niklaus renamed this from getOmnivore_js_Temp_Filename_Element
	// to getFileElement - which is misleading, because we get a FileNAME-Element.
	// As the use of this function shall not be limited to Omnivore, I left that out,
	// but restored the rest.
	// This method was originally private, but as Niklaus moved it from DocHandle.java
	// to Utils.java (as I suggested), and his equally moved out createNiceFilename
	// uses dh and is therefore not compatible with makeTempFile() in DocHandle.java,
	// this must become public now.
	static public String getTempFilenameElement(String element_key, String element_data){
		IPreferenceStore preferenceStore = new SettingsPreferenceStore(CoreHub.localCfg);
		return Utils.getTempFilenameElement(preferenceStore, element_key, element_data);
	}
	
	// 20210327js: original function was ALSO called getOmnivore_js_Temp_Filename_Element()
	// to allow for alternative calling the same function with or without ther preferenceStore.
	// Niklaus renamed this to processFileElement - which is misleading, because first,
	// this handles an element of a filename, not of a file, and second, it does not
	// only *process* it, but *get* it first, and processing is only a part of getting it.
	// This shall keep the same name as the preceeding wrapper, so that a function with
	// the same name can be called with a different number of parameters.
	static public String getTempFilenameElement(IPreferenceStore preferenceStore, String element_key,
		String element_data){
		
		log.debug("get/processFileElement: element_key=<{}>, element_data=<{}>", element_key, element_data);
		
		StringBuffer element_data_processed = new StringBuffer();
		int nCotfRules = Preferences.PREFERENCE_cotf_elements.length;
		for (int i = 0; i < nCotfRules; i++) {
		
			log.debug("get/processFileElement: PREFERENCE_cotf_elements[{}]=<{}>", Preferences.PREFERENCE_cotf_elements[i], i);
			
			if (Preferences.PREFERENCE_cotf_elements[i].equals(element_key)) {

				log.debug("get/processFileElement: Match with element_key {}!", element_key);				
				
				if (element_key.contains("constant")) {
					//Since omnivore_js 1.4.5 / noatext_jsl 1.4.12:
					//Mask all characters that shall not appear in the generated filename from add_trail_char.
					//The masking is implemented here, and not merely after the input dialog, so that unwanted characters are caught
					//even if they were introduced through manipulated configuration files or outdated settings.
					
					//Do NOT trim leadig and trailing space however -
					//some user might want a single space as separation character.
					//(Which I would certainly NOT recommend, but still it's technically ok.)

					String search = PREFBASE + Preferences.PREFERENCE_COTF
						+ Preferences.PREFERENCE_cotf_elements[i] + "_"
						+ Preferences.PREFERENCE_cotf_parameters[1];				
					//20210327js: restored cleaning functionality from last 2.1.7js / omnivore_js version.
					String constant = cleanStringFromUnwantedChars( 
							preferenceStore.getString(search), Preferences.cotf_unwanted_chars_jsorig);
					
					log.debug("get/processFileElement: {} returning constant=<{}>", search, constant);
					
					return constant;
				} else {
					// Shall we return ANY digits at all for this element, and later on: shall we
					// cut down or extend the processed string to some defined number of digits?
					String snumId = PREFBASE + Preferences.PREFERENCE_COTF
						+ Preferences.PREFERENCE_cotf_elements[i] + "_"
						+ Preferences.PREFERENCE_cotf_parameters[1];					
					String snum_digits = preferenceStore.getString(snumId).trim();

					log.debug("get/processFileElement: snum_digits=<{}>", snum_digits);

					// If the num_digits for this element is empty,
					// then return an empty result - the element is disabled.
					if (snum_digits.isEmpty()) {
						return "";
					}
					
					int num_digits = -1;
					if (snum_digits != null) {
						try {
							num_digits = Integer.parseInt(snum_digits);
						} catch (Throwable throwable) {
							// do not consume
						}
					}
					
					// if num_digits for this element is <= 0,
					// then return an empty result - the element is disabled.
					if (num_digits <= 0) {
						return "";
					}
					
					if (num_digits > Preferences.nPreferences_cotf_element_digits_max) {
						num_digits = Preferences.nPreferences_cotf_element_digits_max;
					}
					
					log.debug("get/processFileElement: num_digits=<{}>", num_digits);
					
					//Start with the passed element_data string
					String element_data_incoming=element_data.trim();
					
					log.debug("get/processFileElement: element_data_incoming=<{}>", element_data_incoming);

					// Remove all characters that shall not appear in the generated filename
					// and trim leading and trailing whitespace
					
					// TODO: 20210327js: Here, Niklaus had already included the cleaning,
					// but he replaced my original starting, cleaning and trimming
					// by a call to regex.Matcher...trim() (stripping debug output, too).
					// See more comments next to the constant definitions in PreferencePage.java
					// For now, I'm putting my original hand made version back in place.
					
					// String element_data_processed5 = (element_data.incoming
					//	.replaceAll(java.util.regex.Matcher
					//		.quoteReplacement(Preferences.cotf_unwanted_chars_ngregex), "")
					//	.toString().trim());
					// Maybe see (leftover in Niklaus' code in a comment below):
					// eclipse-javadoc:%E2%98%82=ch.elexis.core.data/%5C/usr%5C/lib%5C/jvm%5C/java-8-oracle%5C/jre%5C/lib%5C/rt.jar%3Cjava.util.regex(Matcher.class%E2%98%83Matcher~quoteReplacement~Ljava.lang.String;%E2%98%82java.lang.String
					
					String element_data_processed5 = cleanStringFromUnwantedChars(
							element_data_incoming, Preferences.cotf_unwanted_chars_jsorig);
					
					// filter out some special unwanted strings from the title that
					// may have entered while importing and partially renaming files
					
					 // remove filename remainders like _noa635253160443574060.doc
					String element_data_processed4 = element_data_processed5
						.replaceAll("_noa[0-9]+\056[a-zA-Z0-9]{0,3}", "");

					// remove filename remainders like noa635253160443574060.doc
					String element_data_processed3 = element_data_processed4
						.replaceAll("noa[0-9]+\056[a-zA-Z0-9]{0,3}", "");
					
					// remove filename remainders like _omni_635253160443574060_vore.pdf
					String element_data_processed2 = element_data_processed3
						.replaceAll("_omni_[0-9]+_vore\056[a-zA-Z0-9]{0,3}", ""); 

					// remove filename remainders like omni_635253160443574060_vore.pdf
					String element_data_processed1 = element_data_processed2
						.replaceAll("omni_[0-9]+_vore\056[a-zA-Z0-9]{0,3}", "");
					
					log.debug("get/processFileElement: element_data_processed1=<{}>", element_data_processed1);
					
					// Limit the length of the result if it exceeds the specified or predefined max
					// number of digits
					if (element_data_processed1.length() > num_digits) {
						element_data_processed1 = element_data_processed1.substring(0, num_digits);
					}

					log.debug("get/processFileElement: num_digits=<>", num_digits);
				
					// If a leading fill character is given, and the length of the result
					// is below the specified max_number of digits, then fill it up.
					// Note: We could also check whether the num_digits has been given.

					//Do NOT trim leadig and trailing space however -
					//some user might want a single space as filling character.
					//(Which I would certainly NOT recommend, but still it's technically ok.)

					// Instead, I use the default max num of digits if not.
					//20210327js: restored cleaning functionality from last 2.1.7js / omnivore_js version.
					
					String leadId = PREFBASE + Preferences.PREFERENCE_COTF
						+ Preferences.PREFERENCE_cotf_elements[i] + "_"
						+ Preferences.PREFERENCE_cotf_parameters[0];
					String lead_fill_char = cleanStringFromUnwantedChars(
							preferenceStore.getString(leadId), Preferences.cotf_unwanted_chars_jsorig);
					
					log.debug("get/processFileElement: lead_fill_char=<{}>", lead_fill_char);
					
					if ((lead_fill_char != null)
						&& (lead_fill_char.length() > 0)
						&& (element_data_processed1.length() < num_digits)) {
						
						lead_fill_char = lead_fill_char.substring(0, 1);
						
						log.debug("get/processFileElement: lead_fill_char=<{}>", lead_fill_char);
						log.debug("get/processFileElement: num_digits=<{}>", num_digits);
						log.debug("get/processFileElement: element_data_processed1.length()=<{}>", element_data_processed1.length());
						log.debug("get/processFileElement: element_data_processed1=<{}>", element_data_processed1);
												
						for (int n = element_data_processed1.length(); n <= num_digits; n++) {
							element_data_processed.append(lead_fill_char);
							log.debug("get/processFileElement: n, element_data_processed={}, <{}>", n, element_data_processed);
						}
					}
					element_data_processed.append(element_data_processed1);

					log.debug("get/processFileElement: element_data_processed=<{}>", element_data_processed);
					
					
					// If an add trailing character is given, add one
					// (typically, this would be a space or an underscore)
					// Even if a string is entered in the configuration dialog,
					// only the first valid character is used.
					
				    //Since omnivore_js 1.4.5 / noatext_jsl 1.4.12:
					//Mask all characters that shall not appear in the generated filename from add_trail_char.
					//The masking is implemented here, and not merely after the input dialog,
					//so that unwanted characters are caught even if they were introduced
					//through manipulated configuration files or outdated settings.
					
					//Do NOT trim leading and trailing space however -
					//some user might want a single space as separation character.
					//(Which I would certainly NOT recommend, but still it's technically ok.)
										
					String trailId = PREFBASE + Preferences.PREFERENCE_COTF
						+ Preferences.PREFERENCE_cotf_elements[i] + "_"
						+ Preferences.PREFERENCE_cotf_parameters[2];
					String add_trail_char = cleanStringFromUnwantedChars( 
							preferenceStore.getString(trailId), Preferences.cotf_unwanted_chars_jsorig);				
					
					log.debug("get/processFileElement: add_trail_char=<{}>", add_trail_char);
					
					if ((add_trail_char != null) && (add_trail_char.length() > 0)) {
						add_trail_char = add_trail_char.substring(0, 1);

						log.debug("get/processFileElement: add_trail_char=<{}>", add_trail_char);
						
						element_data_processed.append(add_trail_char);

						log.debug("get/processFileElement: element_data_processed=<{}>",
								element_data_processed);
					}
					log.debug("{} {} {} {} <{}> {} <{>}", i, snumId, snum_digits, leadId,
						lead_fill_char, trailId, add_trail_char);
				}
				
				return element_data_processed.toString(); // This also breaks the for loop
			} // if ... equals(element_key)
		} // for int i...
		return ""; // default return value, if nothing is defined.
	}
	
	/**
	 * Generate a nice file name for the docHandle in the temp directory Used when double-clicking
	 * or dragging the file <br>
	 * <br>
	 * Can be configured via the setting document exchange..Omnivore <br>
	 * Returns a formatted temporary filename element, observing current settings from the
	 * preference store, also observing default settings and min/max settings for that parameter
	 * 
	 * 
	 * @param dh
	 *            The docHandle (containing title, patient, etc for which we want to generate the
	 *            name
	 * @return The requested filename element as a string.
	 * 
	 * @author Joerg Sigle, reworked for Elexis 3.5 by Niklaus Giger
	 */
	
	public static String createNiceFilename(DocHandle dh){
		StringBuffer tmp = new StringBuffer();
		tmp.append(getTempFilenameElement("constant1", ""));
		tmp.append(getTempFilenameElement("PID", dh.getPatient().getKuerzel())); //getPatient() liefert in etwa: ch.elexis.com@1234567; getPatient().getId() eine DB-ID; getPatient().getKuerzel() die Patientennummer.
		tmp.append(getTempFilenameElement("fn", dh.getPatient().getName()));
		tmp.append(getTempFilenameElement("gn", dh.getPatient().getVorname()));
		tmp.append(getTempFilenameElement("dob", dh.getPatient().getGeburtsdatum()));
		
		tmp.append(getTempFilenameElement("dt", dh.getTitle())); //not more than 80 characters, laut javadoc
		tmp.append(getTempFilenameElement("dk", dh.getKeywords()));
		//Da könnten auch noch Felder wie die Document Create Time etc. rein - siehe auch unten, die Methoden getPatient() etc.
		
		tmp.append(getTempFilenameElement("dguid", dh.getGUID()));
		
		//N.B.: We may NOT REALLY assume for sure that another filename, derived from a createTempFile() result, where the random portion would be moved forward in the name, may also be guaranteed unique!
		//So *if* we should use createTempFile() to obtain such a filename, we should put constant2 away from configured_temp_filename and put it in the portion provided with "ext", if a unique_temp_id was requested.
		//And, we should probably not cut down the size of that portion, so it would be best to do nothing for that but offer a checkbox.
		
		//Es muss aber auch gar nicht mal unique sein - wenn die Datei schon existiert UND von einem anderen Prozess, z.B. Word, mit r/w geöffnet ist, erscheint ein sauberer Dialog mit einer Fehlermeldung. Wenn sie nicht benutzt wird, kann sie überschrieben werden.
		
		//Der Fall, dass hier auf einem Rechner / von einem User bei dem aus Daten erzeugten Filenamen zwei unterschiedliche Inhalte mit gleichem Namen im gleichen Tempdir gleichzeitig nur r/o geöffnet werden und einander in die Quere kommen, dürfte unwahrscheinlich sein.
		//Wie wohl... vielleicht doch nicht. Wenn da jemand beim selben Patienten den Titel 2x einstellt nach: "Bericht Dr. Müller", und das dann den Filenamen liefert, ist wirklich alles gleich.
		//So we should ... possibly really add some random portion; or use any other property of the file in that filename (recommendation: e.g. like in AnyQuest Server :-)  )
		
		//Ganz notfalls naoch ein Feld mit der Uhrzeit machen... oder die Temp-ID je nach eingestellten num_digits aus den clockticks speisen. Und das File mit try createn, notfalls wiederholen mit anderem clocktick - dann ist das so gut wie ein createTempFile().
		//For now, I compute my own random portion - by creating a random BigInteger with a sufficient number of bits to represent  PreferencePage.nOmnivore_jsPREF_cotf_element_digits_max decimal digits.
		//And I accept the low chance of getting an existing random part, i.e. I don't check the file is already there.
		
		SecureRandom random = new SecureRandom();
		int needed_bits = (int) Math.round(
			Math.ceil(Math.log(Preferences.nPreferences_cotf_element_digits_max) / Math.log(2)));
		tmp.append(getTempFilenameElement("random", new BigInteger(needed_bits, random).toString()));
		
		tmp.append(getTempFilenameElement("constant2", ""));
		return tmp.toString();
	}

	//----------------------------------------------------------------------------
	
	// 20210327js: Brought over from Elexis 2.1.7js / Omnivore_js
	// Original 2.1.7js / omnivore_js code is in:
	// jsigle@blackbox  Sa M�r 27  15:28:44  /mnt/sdb3/Elexis-workspace/elexis-2.1.7-20130523/elexis-bootstrap-js-201712191036-last-20130605based-with-MSWord_js-as-used-by-JH-since-201701-before-gitpush  
	// $ kate ./elexis-base/ch.elexis.omnivore/src/ch/elexis/omnivore/preferences/PreferencePage.java
	// Current 3.7js / omnivore code is in:
	// /mnt/think3/c/Users/jsigle/git/elexis/3.7/git/./elexis-3-base/bundles/ch.elexis.omnivore.ui/src/ch/elexis/omnivore/ui/preferences/
	// Preferences.java, PreferenceConstants.java, PreferencePage.java	
	
	// TODO: (kept from 2.1.7js) Refactoring: Similar code is used around the omnivore and noatext preferences. Maybe put it all into some jsigle.utils code file. Be careful not to break plugin independence by that.
	// TODO: Right now, my code for meaningful filenames is already found by Search-File-in the noatext_js portions, but NOT in the msword_js portions yet.

	// TODO: See/Workup comments regarding this function above and in
	// PreferencePage.java next to unwanted_chars_jsorig / unwanted_chars_ngregex
	
	 /**
	   * 201305271847js:
	   * Clear unwanted_chars from s_incoming and return a String with all remaining chars whose codePoint is >= 32.
	   * Even if s_incoming is null or empty, an allocated but empty String is returned.
	   * Warning: This method relies on Java properly handling memory allocation by itself.
	   *  
	   * minus all characters that occur in 
	   * @param s_incoming		- The string that shall be cleaned from unwanted characters.
	   * @param unwanted_chars	- A string of characters >= 32 that must not appear in the returned StringBuffer. 
	   * @return				- A StringBuffer containing s_incoming cleaned from all chars whose codePoint is <32 or who are contained in unwanted_chars.
	   */
		
	//Ich verwende kein replaceAll, weil dessen Implementation diverse erforderliche Escapes offenbar nicht erlaubt.
	//Especially, \. is not available to specify a plain dot. (Na ja: \0x2e ginge dann doch - oder sollte gehen.
	//Findet aber nichts. Im interaktiven Suchen/Ersetzen in Eclipse ist \0x2e illegal; \x2e geht eher.
	//In Java Code geht ggf. \056 (octal) . Siehe unten beim automatischen Entfernen von Dateinamen-Resten besonders aus dem docTitle.))

	//A final trim() is NOT included, as the function may also be used on content that might consist of a single space -
	//namely, the configurable separation charactes between various elements of an auto-generated filename in Omnivore or TextView.

	//20210327js: In the course of restauration of functionality from the last 2.1.7js / omnivore_js
	//version to 3.7, I rename this function, removing the (old) misleading "...AndTrim" at its end.
	
	public static String cleanStringFromUnwantedChars(String s_incoming, String unwanted_chars) {
		StringBuffer s_clean=new StringBuffer();
		if (s_incoming != null) { 
			if ((unwanted_chars == null) || (unwanted_chars.length()==0)) {
				s_clean.append(s_incoming);
			} else {
				for (int n=0;n<s_incoming.length();n++) {
					String c=s_incoming.substring(n,n+1);
					if ((c.codePointAt(0)>=32) && (!unwanted_chars.contains(c))) {
						s_clean.append(c);
					}
				}
			}
		}
		return s_clean.toString();
	}												


}
