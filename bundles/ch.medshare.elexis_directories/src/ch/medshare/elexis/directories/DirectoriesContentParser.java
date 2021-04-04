/*******************************************************************************
 * Portions copyright (c) 2010, 2012-2021 Jörg Sigle www.jsigle.com and portions copyright (c) 2007, medshare and Elexis
 * 
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    J. Sigle - 20210402 www.jsigle.com - after years? of non-functionality
 *    			 Adopted to change from http://tel.local.ch to https://www.local.ch
 *               Adopted to (once again) different result format
 *				 Added ability to retrieve and process multiple consecutive result pages
 *    			 (e.g. Schmidt in Basel has 72 results delivered in 8 pages...)
 *    			 Added rather well working and robust extraction of Titles from
 *    			 the nameVornameText for List results.
 *               Reworked extraction of eMail addresses from for List results.
 *               Added display of title and eMail in newly added columns in search result.
 *               Added field to enter (academic) Title in KontaktErfassenDialog,
 *               Added passing of title and eMail address from search result to
 *               the KontaktErfassenDialog and onwards into the database - thereby
 *               removing the need to postpone entering a title to a seperate step
 *               through the KontaktDetails View.
 *               Added further documentation regarding the data gathering and extraction. 
 *    
 *    J. Sigle - 20120712-20120713 www.jsigle.com
 *    			 On about 2012-07-11 (or 2012-07-04?), tel.local.ch vastly changed their delivery formats again.
 *    			   This rendered this plugin completely dysfunctional.
 *    		     Changed http get implementation to use a connection and userAgent setting,
 *     			   otherwise, WAP content would be served that did not directly reveal the desired content.
 *     			 Reviewed, made functional again, and improved pre-processing and parsing steps.
 *               Added a few features regarding filling of the Zusatz field from the list format,
 *                 among others: interpretation of the Categories field unnumbered list entries
 *                 into the field Zusatz, as long as no better information is obtained from a Details entry.
 *               When a single entry is double clicked now in a result list,
 *                 additional / overwriting content is now truly filled in from the resulting served Details entry. 
 *               URIencoded e-mail addresses are now decoded and stripped from excess wrapping characters.
 *               
 *
 *    J. Sigle - 20101213-20101217 www.jsigle.com
 *    			 Hopefully, all of my changes are marked by a comment that has "js" in it.
 *    			 Adoption of processing of results in ADR_LIST format to changed html content,
 *                 as recently introduced by http://tel.local.ch.
 *               Filling of poBox (and some other fields) made conditional:
 *                 The target is now initialized as an empty string, and only filled from the
 *                 processed html when moveTo("SomeRespectiveTag") was successful.
 *                 Otherwise, some garbage could be read (or persist) in(to) the target
 *                 variable - and, in the case of poBox, if zusatz was empty, that garbage
 *                 would appear up there. Effectively, the user had to clean zusatz
 *                 more often than not e.g. when the address of a health insurance company
 *                 was obtained.
 *               Enhanced removeDirt() to also remove leading and trailing blanks/spaces.
 *                 I found addresses with trailing blanks in the street address field quite often.
 *               Some comments added with regard to program functionality.
 *               Some debug/monitoring output added in internal version only;
 *                 commented out again for published version: //logger.debug("...           
 *                 
 *    			 Suggestion regarding getVornameNachname():
 *                 Maybe the first-name/last-name split should be changed,
 *    			   or an interactive selection of the split point be provided.
 *                 See new comment before the function header.
 *                 
 *               Suggestion regarding zusatz:
 *                 If a title like Dr. med., PD Dr. med., Prof. is encountered,
 *                 split it off and put it directly into the title field.
 *                 (We use zusatz for a Facharztbezeichnung like Innere Medizin FMH,
 *                  and title for a Titel like Dr. med. etc.)
 *    
 *               Suggestion regarding second level search request:
 *                 Problem: When I first search for anne müller in bern,
 *                 and thereafter double click on the first entry, I get data
 *                 in the add-contact-dialog that does NOT include her title (role).
 *                 (with java code revised by myself, which works for other
 *                  müllers returned in the first result list, including their titles).
 *                  
 *                 Similar problem: Looking for Henzi in Bern finds a whole list of results.
 *                 Double clicking the last (Stefan Henzi) returns another list.
 *                 Some of the entries have the title Dr. med. and some don't -
 *                 Elexis does NOT show the second list for further selection,
 *                 and it apparently evaluates one that doesn't have the title.
 *                 
 *                 Not similar - but related problem: Looking for Hamacher in Bern
 *                 returns two entries. Double click on Jürg Hamacher in the tel.local.ch
 *                 page in the WWW browser returns a single entry. This detail entry
 *                 (but not the previously shown list entry) includes the title and
 *                 the e-mail address. Neither is transferred to Elexis when double
 *                 clicking on the second list entry.
 *                 But when I search for Jürg Hamacher immediately, the single detailed
 *                 entry is found immediately, and all information from there transferred.  
 *                     
 *                     
 *                 I'm somewhat unsure whether my assumption regarding what happens
 *                 are correct here (would need to further review the program).
 *                 I currently guess that:
 *                 
 *                 If a user clicks on one entry from the list returned by an initial search,
 *                 then feed the second level search (which will feed the new kontakt entry dialog)
 *                 with both the name AND the address returned from the first level search.
 *                 
 *                 (a) The results from the second level search, may be processed, but are
 *                     NOT really used by Elexis. I.e. I see debug output from inside
 *                     extractKontakt(), but I don't see any effects (i.e. changed variable
 *                     content) in the Elexis Kontakt dialog.
 *                     (The Jürg Hamacher example)
 *                     
 *                 (b) Supplying ONLY the name, will not suffice to get a single-entry result
 *                     e.g. for Anne Müller in Bern, so her title which is available only in the
 *                     detailed result output will be missed.
 *                     (The Anna Müller Stefan Henzi examples)
 *                
 *                 Moreover, it is (maybe) shere luck that in this case,
 *                 we have only ONE Anne Müller in Bern in the result list - 
 *                 and all others have some additional names. Otherwise, I'm unsure
 *                 whether a result list containing e.g. TWO entries for Max Muster,
 *                 would return the correct one for either case in the second level search...
 *                 (The Stefan Henzi examples, I guess, *might* illustrate just that.)
 *
 *                 I have followed this through to WeiseSeitenSearchForm.java
 *                 openKontaktDialog() where the information from the list_format entry
 *                 (i.e. with empty zusatz, exactly the entry at which we double clicked) is supplied.
 *                 
 *                 I've also reviewed open.dialog() one step further - but then it becomes too
 *                 much for me for today. From my debugging output, I can clearly see that
 *                 (for the Jürg Hamacher example):
 *                 
 *                 *after* openKontaktDialog() is called,
 *                 there occurs another call of extractKontakte() (from where?!)
 *                 which calls extractKontakt(),
 *                 which returns all information from the detailed information (),
 *                 which is added as part of a new kontakte.entry at the end of the extractKontakt() loop,
 *                 and apparently ignored thereafter (why that?)
 *                 
 *                 I'm unsure whether this lasts search/search result processing should not
 *                 better occur *before* the dialog is opened, and its information used to
 *                 feed the dialog. But take care; that multiple dialogs may be opened if
 *                 multiple contacts are selected on the first list, so they all must be
 *                 fed with individual new searches, and the original contact list may not
 *                 be forgotten until the last dialog window so generated has been closed...
 *                 
 *                 Please look at my extensive Anne Müller related comments below;
 *                 and please note, that Anne Müller's "Zusatz" is not lost because
 *                 I changed some zusatz related lines, but because the second level
 *                 search request apparently returns a list_format result (again),
 *                 which does NOT have a title entry for her and/or because when a
 *                 detailed_format result is returned in a second level search,
 *                 it is processed, but its results are not honoured.
 *                 
 *                 Please review the output of tel.local.ch for all entries on the
 *                 first anne müller bern search result, and what happens with the
 *                 various titles. Some work, some don't.
 *                 Please also review the Stefan Henzi example case.
 *                 
 *                 You can easily switch on my debug output:
 *                 look for: //Anne Müller case debug output
 *                 in DirectoriesContentParser.java (this file)
 *                 and WeisseSeitenSearchView.java
 *                 Or just uncomment all occurences of "//System.out.print("jsdebug:"
 *                 in these files (except the one in the line above) with find/replace.
 *                 
 *                 You can also set the variable zusatz to a fixed value in either
 *                 extractKontakt() or extractListKontakt() functions and see what is used,
 *                 and what is ignored.
 *
 *                 Sorry - for myself, I just don't have the time to review that
 *                 problem in more detail by now today; I also think it's of secondary
 *                 importance compared to the restoration of the first level search
 *                 function in general.
 *
 *    M. Imhof - initial implementation
 *    
 * $Id: DirectoriesContentParser.java 5277 2009-05-05 19:00:19Z tschaller $
 *******************************************************************************/

package ch.medshare.elexis.directories;

import java.io.IOException;
import java.io.UnsupportedEncodingException; //20120713js
import java.net.MalformedURLException;
import java.net.URLDecoder; //20120713js
import java.util.List;
import java.util.Vector;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.ui.util.SWTHelper;

/**
 * 
 * @author jsigle (comment and 20101213, 20120712 update only, 20210402 update)
 * 
 *         The service http://tel.local.ch provides a user-interface for WWW browsers with a lot of
 *         additional content around the desired address/contact information. The address/contact
 *         information is extracted from this material and returned in variable fields suitable for
 *         further usage by Elexis.
 * 
 *         If a search request returns multiple results, these appear in (what we call) "ADR_LIST"
 *         format. If a search request returns exactly one result (or one entry from the list is
 *         clicked at in the WWW browser), the result appears in (what we call) "ADR_DETAIL" format.
 * 
 *         By 12/2010, a change in the output format of tel.local.ch required new marker strings for
 *         processing. The processing of a result in ADR_DETAIL format continued to work, But the
 *         processing of a result in ADR_LIST format would deliver an empty result.
 * 
 *         By 2012-07-11, the plugin stopped working again.
 * 
 *         Please note that there is relaunch related info on www.directoriesdata.ch www.local.ch
 *         shall apparently be split into a free consumer site, and a commercial counterpart.
 * 
 *         Also note that neither &mode=text nor &range=all do currently change the result which I
 *         observe.
 * 
 *         20210402js: Some time (years?) ago, the plugin stopped working again.
 *         I'm trying to revive it now.
 *         Search requests to http://tel.local.ch receive a redirect page.
 *         Search requests are redirected to https://www.local.ch
 *         Search requests to this address receive generally searchable results -
 *         but I just noted that search requests to https://mobile.local.ch get MUCH smaller responses -
 *         mainly usable content, and much less overhead.
 *         So I'm attempting to parse these instead.
 *         
 * 
 */
public class DirectoriesContentParser extends HtmlParser {
	
	//20210402js: old version:
	//private static final String ADR_LISTENTRY_TAG = "<div class='row local-listing'"; //$NON-NLS-1$
	//private static final String ADR_SINGLEDETAILENTRY_TAG = "<div class='eight columns details'";; //$NON-NLS-1$
	//private static String metaPLZTrunc = "";
	//private static String metaOrtTrunc = "";
	//private static String metaStrasseTrunc = "";
	//private final Logger logger = LoggerFactory.getLogger("ch.medshare.elexis_directories");
	//20210402js: new version:
	
	//20210402js: mobile.local.ch only:
	//private static final String ADR_LISTENTRY_TAG = "<div class='";
	//It's really:   <div class='busresult'>    for the first address 
	//and            <div class='resresult'>    for all following addresses.  
	//Luckily, there are no   <div class='   lines before the first address in the mobile.local.ch result.
	//private static final String ADR_SINGLEDETAILENTRY_TAG = "NOT YET LOOKED AT IN DETAIL";; //$NON-NLS-1$

	//20210402js: www.local.ch only: 
	//Die folgenden Strings werden verwendet, um zu identifizieren...
	
	//...den nächsten Eintrag in einem als Liste hereinkommenden Suchergebnis mit mehreren Treffern:
	//
	//Please note:
	//This string is used to identify a web page with HTML text received as search result
	//as a list of multiple search hits,
	//as well as identifying a point BEFORE all extractable data of EACH entry in this list,
	//and AFTER all extractable data of the preceding entry in this list.
	//(I.e. something like the beginning of an entry on the list).
	//
	//So this string should:
	//- occur in the HTML source code of a search result when this provides a list of multiple results,
	//- NOT occur in the HTML source code of a search result that provides a detailed entry from a single search hit,
	//- occur at the beginning or rather close to the beginning of each result entry in this list,
	//  before the first extractable information of that entry
	private static final String ADR_LISTENTRY_TAG = "<div class='js-entry-card-container row";
	
	//...den Beginn des Eintras in einem als Detail-Eintrag hereinkommenden Suchergebnis mit EINEM Treffer:
	//
	//Please note:
	//This string is used to identify a web page with HTML text received as search result
	//as a detailed entry for a single search hit, 
	//as well as identifying a point BEFORE all extractable data of THIS_SINGLE entry.
	//
	//So this string should:
	//- NOT occur in the HTML source code of a search result when this provides a list of multiple results,
	//- occur in the HTML source code of a search result that provides a detailed entry from a single search hit,
	//- occur at the beginning or rather close to the beginning of extractable information of that entry.
	private static final String ADR_SINGLEDETAILENTRY_TAG = "<div class='container' itemscope";

	//20210404js: These fields aren't available in this way any more:
	/*
	private static String metaPLZTrunc = "";
	private static String metaOrtTrunc = "";
	private static String metaStrasseTrunc = "";
	*/
	
	private final Logger logger = LoggerFactory.getLogger("ch.medshare.elexis_directories");
	
	public DirectoriesContentParser(String htmlText){
		super(htmlText);
	}
	
	/**
	 * Retourniert String in umgekehrter Reihenfolge
	 */
	private String reverseString(String text){
		if (text == null) {
			return "";
		}
		String reversed = "";
		for (char c : text.toCharArray()) {
			reversed = c + reversed;
		}
		return reversed;
	}
	
	/**
	 * This splits the provided string at the first contained space. This is not optimal for all
	 * cases: Persons may have multiple given names / christian names, and they will very often be
	 * separated just by spaces. I actually observed in real life usage that a second given name
	 * went to the "Name"="Nachname"="Family name" field together with the true family name. It
	 * might be better to split the name at the *last* contained space, because multiple family
	 * names are usually linked by a dash (-), rather than separated by a space (this is my personal
	 * impression). However, I haven't changed the code so far.
	 */
	private String[] getVornameNachname(String text){
		String vorname = ""; //$NON-NLS-1$
		String nachname = text;
		int nameEndIndex = text.trim().indexOf(" "); //$NON-NLS-1$
		if (nameEndIndex > 0) {
			vorname = text.trim().substring(nameEndIndex).trim();
			nachname = text.trim().substring(0, nameEndIndex).trim();
		}
		return new String[] {
			vorname, nachname
		};
	}
	
	/**
	 * remove leading and trailing whitespace characters
	 * 
	 * @param text
	 * @return
	 */
	private String removeDirt(String text){
		text = text.replaceAll("^+\\s", "");
		text = text.replaceAll("\\s+$", "");
		
		return text.replace("<span class=\"highlight\">", "").replace("</span>", "");
	}
	
	/**
	 * Informationen zur Suche werden extrahiert.
	 * 
	 */
	public String getSearchInfo(){
		reset();
		
		logger.debug("DirectoriesContentParser.java: getSearchInfo() running...");
		logger.debug("Beginning of substrate: <" + extract("<", ">") + "...");
		
		//20210402js: NICHT das <title> tag hierfür auswerten, da kommt nämlich aktuell:
		//<title>Schmidt in Basel im Telefonbuch &gt;&gt; Jetzt finden! - local.ch
		//</title>
		//String searchInfoText = extract("<title>", "</title>");

		//20210402js: SONDERN folgendes tag auswerten:
		//<h1 class='search-header-results-title'>72 Ergebnisse für Schmidt in basel</h1>
		String searchInfoText = extract("<h1 class='search-header-results-title'>", "</h1>").trim();
		
		
		if (searchInfoText == null)	{
			logger.debug("DirectoriesContentParser.java: getSearchInfo(): searchInfoText IS NULL!");
			return "";//$NON-NLS-1$}
		} else {
			logger.debug("DirectoriesContentParser.java: getSearchInfo(): searchInfoText != null");
			logger.debug("DirectoriesContentParser.java: getSearchInfo(): \"" + searchInfoText + "\"\n");
			
			return searchInfoText
			//20210402js: alte Version des nachträglichen Putzens, das braucht man aber nicht, ich lasse nur das .trim():
			//.replace("<strong class=\"what\">", "")
			//.replace("<strong class=\"where\">", "").replace("<strong>", "").replace("</strong>", "").trim(); //$NON-NLS-1$ //$NON-NLS-2$
			.trim();
		}
	}
	
	/**
	 * Check whether the page contains a valid link indicating there are more pages with additional search results
	 * 
	 */
	public boolean checkForMorePages(){
		reset();
		
		logger.debug("DirectoriesContentParser.java: checkForMorePages() running...");
		logger.debug("Beginning of substrate: <" + extract("<", ">") + "...");
		
		//20210402js: If there is any next page,
		//there may be one or more numbered links to various page numbers -
		//but quite certainly, at there will be one link to the "next" page:
		//<a rel="next" class="pagination-link" href="/de/q/basel/Schmidt?mode=text&amp;page=2"><span>&raquo;</span>
		String searchLinkToNextPage = extract("<a rel=\"next\" class=\"pagination-link\" href=", "\"><span>&raquo;</span>").trim();

		if ( (searchLinkToNextPage == null) || (searchLinkToNextPage.length()<5) ) {
			logger.debug("DirectoriesContentParser.java: checkForMorePages(): searchLinkToNextPage IS NULL or too short!");	
			return false;			//there is no next page
		}	else {
			logger.debug("DirectoriesContentParser.java: checkForMorePages(): searchLinkToNextPage != null");
			logger.debug("DirectoriesContentParser.java: checkForMorePages(): \"" + searchLinkToNextPage + "\"\n");	
			return true;		//there is a next page, so get it as well...
		}
	}
	
	/**
	 * 
	 * Extrahiert einen akademischen oder professionellen Titel
	 * aus einem String mit Titel. Name Vorname.
	 * 
	 * @parameter
	 *  nameVornameText	- the String containing Titel. Name Vorname
	 *  A common Titel. like Dr. or med. or Dipl. or biol.
	 *  is separated from the following text by a dot (+-space)
	 *  and the first space in title. name usually occurs after Titel.
	 *  
	 *  Multiple Titel. items are supported, like Dr. med.
	 *  
	 *  The algorithm also recognizes initials (by the fact
	 *  that a title. needs at least 2 chars before the dot, and
	 *  a middle initial has the first dot after the first space).
	 *  
	 *  At the beginning, there is special recognition and handling
	 *  handling for the title "PD" which has no dot but only a space
	 *  after it.
	 *  
	 *  !!! The function returns a string with the title (trimmed)
	 *  The supplied parameter nameVornameText is left unchanged,
	 *  so the calling code must later on remove the number of chars
	 *  found in the returned title from its own variable nameVornameText,
	 *  afterwards trimming it to remove any remaining spaces as well. !!!
	 *  
	 * @return
	 *  Everything that's considered "title." or "titles..." and was
	 *  removed from the beginning of the string. The string supplied
	 *  as parameter is stripped from that information.
	 *  
	 * @author jsigle
	 * 
	 * Z.B. aus PD Dr. med. Hamacher Jürg
	 */
	public String extractAkTitelFromNameVornameText(String nameVornameText) {
		String akTitel = "";
		if (nameVornameText != null && nameVornameText.length() > 0)
			{
			nameVornameText = nameVornameText.trim();

			//20210404js: Moved here from extractKontakt()...
			//20131127js: Replace something like "Dr. med. PD" by "PD Dr. med."
			nameVornameText = nameVornameText.replace("Dr. med. PD", "PD Dr. med.");
			nameVornameText = nameVornameText.replace("Dr. med. Prof.", "Prof. Dr. med.");
			
			//Special Handling for a leading title PD, which has no dot after it.
			//Test this by searching for Wer, Was, Tel. = "PD", Ort = "Bern" :-)
			if ( nameVornameText.substring(0,3).equals("PD ") ) {
				akTitel = "PD ";
				nameVornameText = nameVornameText.substring(3);
			}
			
			//Find the last dot in nameVornameText which can be reached from the beginning
			//by advancing up to 10 chars from the beginning or the previous
			int posRightmostDot = -1;
			
			int posNextDot = nameVornameText.indexOf(".",posRightmostDot+1);
			//The first dot must appear before the first space -
			//so we're not fooled by (looking for Schmidt in Basel) 
			//Wyss H.+M. (-Schmidt)
			//or
			//Zinng G., D., D., und B. (-Schmidt)
			if ( posNextDot < nameVornameText.indexOf(" ") ) { 
				while ( (posNextDot > posRightmostDot + 2)	//if a dot was found in the last pass
															//at least 3 chars right of posRightMostDot
															//(which includes at least 3 chars into the String)
					  && (posNextDot < posRightmostDot+10 ) ) {		//and not more than 10 chars further into the String,
						//The distance limits should ensure that we really find and extract
						//even complex professional or academic titles, like Prof. Dr. med. Dr. hon. Max Muster,
						//but are not fooled by Initials like Erwin A. Doolittle or E.T.A. Hoffmann,
						//and leave alone the more extravagant things like Theo Archibald Luminous, III. Lord of Canterbury :-) 
					posRightmostDot = posNextDot;			//update the position of the last found dot
					posNextDot = nameVornameText.indexOf(".",posRightmostDot+1);//check for another one
				}
				//We also want to leave at least 2 characters after our supposedly identified "title" for name and given name
				//- and yes, some Asian names like Li Hu or names without first name like Dr. Wu don't need very much more.
				if ( ( posRightmostDot > 1 ) && ( posRightmostDot < nameVornameText.length() - 3 ) ) {
					//use an additive term because akTitel might already contain "PD "
					akTitel = akTitel + nameVornameText.substring(0,posRightmostDot+1).trim();
				}
			}
		}

		return akTitel;
	}
	
	
	/**
	 * 
	 * Extrahiert Informationen aus dem retournierten Html.
	 * 
	 * Schritt 1: Entscheiden, ob es sich um eine Liste
	 * oder einen Detaileintrag (mit Telefon handelt).
	 * 
	 * 20210404js: Aktuell sehe ich folgende frühe Unterscheidungsmöglichkeit:
	 * 
	 * Suchergebnis mit 1 Hit, kommt sofort als Detaileintrag:
	 * -------------------------------------------------------
	 * 
	 *  Enthält ziemlich früh eine Ziele in folgendem Format:
	 *  
	 *  <meta content='Dr. med. Hamacher Jürg in Bern ✉ Adresse ☎ Telefonnummer ✅ auf local.ch' name='description'>
	 *  (d.h. OHNE die Gruppe " - n Treffer - ".
	 *   Fehlinterpretationen könnten nur auftreten, wenn man nach "Treffer -" in "xyz" sucht, und grade 1 Hit mit diesem Namen bekommen würde...)
	 *   
	 *  Der eigentliche Eintrag enthält/ist erkennbar an/folgt dem String:
	 *  ADR_SINGLEDETAILENTRY_TAG
	 * 
	 *  Hier sind Name, Zusatzinformation, Adresse, Telefonnummer, e-Mail und Fax enthalten -
	 *  aber eben nur für 1 Treffer pro Suchanfrage.
	 * 
	 * Suchergebnis mit mehreren Hits, kommt als Liste mit orientierenden Einträgen
	 * ----------------------------------------------------------------------------
	 * 
	 *  Enthält ziemlich früh eine Ziele in folgendem Format:
	 *  
	 *  <meta content='Hamacher in Bern - 2 Treffer -  - Adresse ✓ Telefonnummer ✓ Ihre Nr.1 für Adressen und Telefonnummer' name='description'>
	 *  Nebenbei: Sucht man nach "Treffer" in "Bern" kommt:
	 *  <meta content='Treffer in Bern - 40 Treffer - (Restaurant, Beratung, Brockenhaus, Entsorgung Recycling, Freikirche) - Adresse ✓ Telefonnummer ✓ Ihre Nr.1 für Adressen und Telefonnummer' name='description'>
	 *  
	 *  Jeder Listeneintrag enthält ein Hyperlink zu einem Detaileintrag.
	 *  
	 *  Jeder eigentliche Eintrag enthält/ist erkennbar an/folgt dem String:
	 *  ADR_LISTENTRY_TAG

	 *  Die Ergebnisliste liefert dabei folgendes:
	 * 
	 *  In der www.local.ch Version mit Name, Adresse, Telefonnummer,
	 *  zum Ansehen der letzteren muss im Browser ein Feld angeklickt werden,
	 *  im Sourcecode ist die Telefonnummer direkt lesbar enthalten
	 *  sowie wenn vorhanden auch eine e-Mail.
	 *  Fehlen tut leider die "Zusatzinformation".
	 *  Dazwischen und drumherum ist unendlich viel wenig-nützlicher Code...
	 *  
	 *  In der mobile.local.ch Version ist der HTML Code VIEL kompakter,
	 *  fast nur nützlicher Content - ABER leider nicht mal die Telefonnummer
	 *  enthalten, d.h. schon dazu müsste für jeden Eintrag erst einmal ein
	 *  Hyperlink geöffnet werden, was dann einen Detaileintrag mit der
	 *  fehlenden Nummer liefert.
	 *  
	 *  Die Ergebnisliste zeigt auch nochmal die verwendeten Suchbegriffe,
	 *  die Anzahl der Treffer - und wenn es mehr als 10 Treffer sind,
	 *  wird sie in mehrere Seiten aufgespalten - untendran kommen
	 *  Hyperlinks für einige der umliegenden Seiten im gesamten Ergebnis,
	 *  davon verwende ich das Link auf "Next" um zu erkennen, dass weitere
	 *  Seiten vorhanden sind, und diese sofort automatisiert abzurufen
	 *  und ebenfalls auszuwerten, bis zu einer einstellbaren Maximalzahl
	 *  von Seiten (das plugin hat damit keine Mühe, ich möchte aber den
	 *  Anbieter nicht verärgern, in dem von seinem für manuelle Bedienung
	 *  gemachten Interface immer mal wieder mehr als 20 Seiten ganz
	 *  offensichtlich durch ein anderes Client-Programm als einen
	 *  konventionellen Web-Browser abgerufen werden...) 
	 * 
	 * 20210404js: the following is possibly outdated information:
	 * 
	 * Bisher ging die Unterscheidung anhand der <div class="xxx"> Einträge: 
	 * 
	 * Detaileinträge: "adrNameDetLev0", "adrNameDetLev1", "adrNameDetLev3"
	 * Nur Detaileintrag "adrNameDetLev2" darf nicht extrahiert werden
	 * 
	 * Listeinträge: "adrListLev0", "adrListLev1", "adrListLev3"
	 * Nur Listeintrag "adrListLev0Cat" darf nicht extrahiert werden
	 * 
	 */
	public List<KontaktEntry> extractKontakte() throws IOException{
		reset();
		
		//TODO: 20210404js: Well, here for once we get results for our search query in HTML - or XML.
		//TODO: 20210404js: So theoretically, we could very well use XML tools to extract the information we're interested in,
		//TODO: 20210404js: instead of using more or less coarsely implemented analysis handling it as plain flat text...
		
		logger.debug("DirectoriesContentParser.java: extractKontakte() running...");
		logger.debug("Beginning of substrate: <" + extract("<", ">") + "...");
		
		//20210404js: The <meta content ... > Field is not available in this probably single field any more at this stage any more.
		//However, the Single Detailed Result file has various <meta content... > fields,
		//and others, allowing for a reliable extraction of the desired content - 
		//see documentation in method extractKontakt().
		//<meta content='Dr. med. Hamacher Jürg in Bern ✉ Adresse ☎ Telefonnummer ✅ auf local.ch' name='description'>
		//<meta content='Dr. med. Hamacher Jürg' property='og:title`> 
		//...
		//<span itemprop='name'>Dr. med. Hamacher Jürg</span>
		//<div class='col-xs-12 title-card-subtitle'>PD Spezialarzt FMH Innere Medizin und Pneumologie (inkl. Schlafmedizin)</div
		//<meta content='031 300 35 00' itemprop='telephone'>
		//<meta content='031 300 35 01' itemprop='faxNumber'>
		//<meta content='lungen-schlaf-praxis.hamacher@hin.ch' itemprop='email'>
		
		
		//20210404js: These fields aren't available in this way any more.
		/*
		if (getNextPos("<meta content='Adresse von ") > 0) {
			logger
				.debug("Processing a <meta> field to help processing the 'details' field later on which is very unstructured after 20131124...\n");
			moveTo("<meta content='Adresse von ");
			metaStrasseTrunc = removeDirt(extract("Strasse: ", ",")).replaceAll("[^A-Za-z0-9]", ""); //$NON-NLS-1$ //$NON-NLS-2$	//20131127js
			metaPLZTrunc = removeDirt(extract("PLZ: ", ",")).replaceAll("[^A-Za-z0-9]", ""); //$NON-NLS-1$ //$NON-NLS-2$	//20131127js
			metaOrtTrunc = removeDirt(extract("Ort: ", ",")).replaceAll("[^A-Za-z0-9]", ""); //$NON-NLS-1$ //$NON-NLS-2$	//20131127js
			if (metaStrasseTrunc == null)
				logger.debug("WARNING: metaStrasseTrunc == null");
			else
				logger.debug("metaStrasseTrunc == " + metaStrasseTrunc);
			if (metaPLZTrunc == null)
				logger.debug("WARNING: metaPLZTrunc == null");
			else
				logger.debug("metaPLZTrunc == " + metaPLZTrunc);
			if (metaOrtTrunc == null)
				logger.debug("WARNING: metaOrtTrunc == null");
			else
				logger.debug("metaOrtTrunc == " + metaOrtTrunc");
		}
		*/
		
		List<KontaktEntry> kontakte = new Vector<KontaktEntry>();
		
		int listIndex = getNextPos(ADR_LISTENTRY_TAG);
		int detailIndex = getNextPos(ADR_SINGLEDETAILENTRY_TAG);
		
		logger.debug("DirectoriesContentParser.java: extractKontakte() initial values of...");
		logger.debug("DirectoriesContentParser.java: extractKontakte().listIndex: "+listIndex);
		logger.debug("DirectoriesContentParser.java: extractKontakte().detailIndex: "+detailIndex);
		
		while (listIndex > 0 || detailIndex > 0) {
			KontaktEntry entry = null;
			
			logger.debug("DirectoriesContentParser.java: extractKontakte() intraloop values of...");
			logger.debug("DirectoriesContentParser.java: extractKontakte().listIndex: "+ listIndex);
			logger.debug("DirectoriesContentParser.java: extractKontakte().detailIndex: "+ detailIndex);
			
			if (detailIndex < 0 || (listIndex >= 0 && listIndex < detailIndex)) {
				// Parsing Liste
				logger.debug("DirectoriesContentParser.java: Parsing Liste:");
				entry = extractListKontakt();
			} else if (listIndex < 0 || (detailIndex >= 0 && detailIndex < listIndex)) {
				// Parsing Einzeladresse
				logger.debug("DirectoriesContentParser.java: Parsing Einzeladresse:");
				entry = extractKontakt();
			}
			
			if (entry != null) {
				logger.debug("DirectoriesContentParser.java: entry: "+entry.toString());
			} else {
				logger.debug("DirectoriesContentParser.java: entry: NULL");
			}
			
			if (entry != null) {
				kontakte.add(entry);
			}
			listIndex = getNextPos(ADR_LISTENTRY_TAG);
			detailIndex = getNextPos(ADR_SINGLEDETAILENTRY_TAG);
		}
		
		return kontakte;
	}
	
	/**
	 * Extrahiert einen Kontakt aus einem Eintrag einer Liste mit Suchergebnissen
	 * 
	 * 20210404js: this is possibly outdated information:
	 * 
	 * Please note (!) that the <li class="detail">tag is a part of the ADR_LIST display type now,
	 * which could be confusing to other people reviewing this code... And please note that one
	 * entry does NOT begin with the <li class "detail">
	 * tag, but (quite probably, I've not perfectly reviewed it) with the <div data-slot="... tag.
	 * 
	 * Please also note that the address/contact details seem to be included
	 * in either of the data carrying lines.
	 *
	 * A result of this type should be obtainable by searching for:
	 * 
	 * Wer, Was: Schmidt			Wo: Basel
	 * Wer, Was: Mueller			Wo: Bern
	 * Wer, Was: Mueller B			Wo: Bern
	 * Wer, Was: Hamacher			Wo: bern
	 *
	 * TODO: As of 20210404js, the results (at least the detailed single results)
	 * apparently contain clean data in meta info tags. Maybe use them instead of
	 * grassing through the longer HTML polluted by advertising, graphics,
	 * GUI for clicking-before-revealing-numbers etc. with all its uncertainties...
	 * 
	 * Instead of simply updateing target strings,
	 * we might give the whole extraction logic a thorough review
	 * based on a current sighting of result HTML dumps of both types beforehand.
	 */
	private KontaktEntry extractListKontakt() throws IOException, MalformedURLException{
		
		logger.debug("DirectoriesContentParser.java: extractListKontakt() running...");
		logger.debug("Beginning of substrate: <" + extract("<", ">") + "...");
		
		if (!moveTo(ADR_LISTENTRY_TAG)) { // Kein neuer Eintrag
			return null;
		}
		
		logger.debug("DirectoriesContentParser.java: extractListKontakt() extracting next entry...");
		
		int nextEntryPoxIndex = getNextPos(ADR_LISTENTRY_TAG); // 20120712js
		
		logger.debug("DirectoriesContentParser.java: extractListKontakt() nextEntryPoxIndex: "+nextEntryPoxIndex);
		
		logger.debug("DirectoriesContentParser.java: Shouldn't the \\\" in the following line and similar ones throughout this file be changed to a simple ' ???");
		
		//moveTo("<h2><a href=\"http://tel.local.ch/"); // 20131127js
		//moveTo("<a href=\"/de/d/"); 					// 20210402js mobile.local.ch version only
		moveTo("<h2 class='lui-margin-vertical-zero ");
		String nameVornameText = extract("card-info-title'>", "</h2>"); //$NON-NLS-1$ //$NON-NLS-2$	//20120712js
		
		nameVornameText = removeDirt(nameVornameText);
		logger.debug("DirectoriesContentParser.java: extractListKontakt().nameVornameText: <"+nameVornameText+">");
		
		// 20210404js: Leftover from previous implementations.
		// I don't know whether this is really needed. Probably not. But it shouldn't hurt either.
		// Do not return any results from empty entries!
		if (nameVornameText == null || nameVornameText.length() == 0) return null;
		
		//20210403js: Try to find and extract one or more leading academic or professional titles,
		//by looking for dots in certain distances from the beginning of the name and each other.
		//All that only if nameVornameText is not currently == null.
		String akTitel = extractAkTitelFromNameVornameText(nameVornameText);
		nameVornameText = nameVornameText.substring(akTitel.length()).trim();  
				
		logger.debug("DirectoriesContentParser.java: extractListKontakt().akTitel: <"+akTitel+">");
		logger.debug("DirectoriesContentParser.java: extractListKontakt().nameVornameText: <"+nameVornameText+">");

		String[] vornameNachname = getVornameNachname(nameVornameText);
		String vorname = vornameNachname[0];
		String nachname = vornameNachname[1];
		
		logger.debug("DirectoriesContentParser.java: extractListKontakt().vorname: <"+vorname+">");
		logger.debug("DirectoriesContentParser.java: extractListKontakt().nachname: <"+nachname+">");
				
		// Anne Müller in Interlaken - Craniosacral Therapie, e-Mail, website --- debug output:
		logger.debug("DirectoriesContentParser.java: extractListKontakt() Possibly add better processing of a successor to role/categories/profession fields here as well, see comments above.");
		
		String zusatz = ""; // 20120712js
		//20210402js: Zusatzinfos just found with a test searching for Dr. Mueller in Basel. Example:
		//
		//</div><div class='card-info-category'><img alt="Frauenkrankheiten und Geburtshilfe (Gynäkologie und Geburtshilfe)" class="card-info-title-icon lui-margin-right-xxs" src="https://images.services.local.ch/bp/localplace-icon/ae/ae0824a1ea4a6d345277f087b87046f90f8d4737/icon-medical.svg?v=1&amp;sig=c77cc4e5dea818cf84ade29430621dc75acc7ac6591102545c0933b6cfbc066f" /><span>Frauenkrankheiten und Geburtshilfe (Gynäkologie und Geburtshilfe)<span class="card-info-category-item-separator"> • </span>Ärzte<span class="card-info-category-item-separator"> • </span>Schwangerschaft<span class="card-info-category-item-separator"> • </span>Praxis</span></div><div class='card-info-open hidden-print'>
		//
		//This is obviously much worse structured XML in a technical sense.
		//It's all direct layout control, rather than providing logically structured content and letting the browser do the formatting etc. 
		//Anyway - we would extract the single or multiple entries from a single line (!) into a single line for Elexis field "Zusatz" like this:
		int catIndex = getNextPos("<span class='categories'>"); // 20131127js:
		if (catIndex > 0 && ((catIndex < nextEntryPoxIndex) || nextEntryPoxIndex == -1)) { // 20120712js
			moveTo("<span class='categories'>"); // 20131127js:
			zusatz = extractTo("</span>"); // 20131127js:
			zusatz = zusatz.replaceAll("&nbsp;&bull;&nbsp;", ", "); // 20131127js:
		} // 20120712js
			//I don't want to use a bullet instead of the comma, because I this may be much more error prone, as it depends on suitable character sets/encodings etc.
			//One drawback (but this was there before) is that the "Gemeinschaftspraxis, ..." from the address field returned for anne müller and separated further below
			//will be separated by a dash (done by code below), so this is an inconsistency. On the other hand, this was there before, and it's also information retrieved from another source.
			//So: may that remain like that for now. 
			//This update gets us the categories information into the Zusatz field in the single result that appears after dblclick on one entry from the tabulated results.
		
		// Anne Müller case debug output:
		logger.debug("DirectoriesContentParser.java: extractListKontakt() catIndex: "+catIndex+"");
		logger.debug("DirectoriesContentParser.java: extractListKontakt() zusatz: <"+zusatz+">\n");

		
		//String adressTxt = extract("<span class='address'>", "</span>"); // 20131127js
		//String adressTxt = extract("<span class='address'>", "</span>"); // 20210402js mobile.local.ch
		
		moveTo("<div class='card-info-address'>");					// 20210402js mobile.local.ch
		String adressTxt = extract("<span>", "</span>").trim();;	// 20210402js mobile.local.ch
		
		//This update gets us address (street, number, zip, city) into both the tabulated results, and the single result that appears after dblclick on one entry from the tabulated results.
		//(But still not the Fax number, that should be extracted from what appears if we click on "Details" in the local.ch tabulated results page.
		// The Fax number is *not* contained in the tabulated result for multiple hits on local.ch, so that will be extracted later on.)  

		logger.debug("DirectoriesContentParser.java: extractListKontakt().addressTxt:\n"+adressTxt+"\n");

		// As of 20120712js, the format of adressTxt is now simply like:
		// "Musterstrasse 12, 3047 Bremgarten b. Bern" (test case: search for: hamacher, bern)
		// "Gemeinschaftspraxis, Monbijoustrasse 124, 3007 Bern" (test case: search for: anne
		// müller, bern)
		String strasse = "";
		String plz = "";
		String ort = "";
		if (adressTxt.contains(", ")) {
			// Use lastIndexOf() to separate only PLZ Ort, no matter how many comma separted entries
			// precede it.
			int CommaPos = adressTxt.lastIndexOf(", ");
			if (CommaPos > -1) {
				strasse = removeDirt(adressTxt.substring(0, CommaPos));
				int SpacePos = adressTxt.indexOf(" ", CommaPos + 2);
				if (SpacePos > -1) {
					plz = removeDirt(adressTxt.substring(CommaPos + 2, SpacePos));
					ort = removeDirt(adressTxt.substring(SpacePos + 1));
				} else {
					ort = removeDirt(adressTxt.substring(CommaPos + 2));
				}
			} else {
				ort = removeDirt(adressTxt);
			}
		}
		if (strasse != "") {
			int CommaPos = strasse.lastIndexOf(", ");
			if (CommaPos > -1) {
				if (zusatz == "") {
					zusatz = removeDirt(strasse.substring(0, CommaPos));
				} else {
					// Puts the new one to the end of the old one:
					// zusatz = zusatz.concat(" - "+removeDirt(strasse.substring(0,CommaPos)));
					// Puts the new one to the beginning of the old one:
					zusatz = removeDirt(strasse.substring(0, CommaPos)) + " - " + zusatz;
				}
				strasse = removeDirt(strasse.substring(CommaPos + 2));
			}
		}
		
		// 20120712js We want to parse the phone number also for the last entry in the list,
		// where nextEntryPoxIndex will already be -1 (!).
		// You can test that with meier, bern, or hamacher, bern.
		String telNr = ""; // 20120712js
		
		//before 20210402js:
		/*
		int phonePos = (getNextPos("<span class='phone'")); // 20120712js
		
		if (phonePos >= 0 && ((phonePos < nextEntryPoxIndex) || nextEntryPoxIndex == -1)) { // 20120712js
			moveTo("<span class='phone'"); // 20120712js
			moveTo("<label>Telefon"); // 20120712js
			// 20120713js Don't use "refuse number" but only "number" - the "refuse " is probably
			// only there
			// for people who don't want to get called for advertising or a similar thing; there is
			// a matching
			// note on the individual Details entries; and probably an asterisk displayed left of
			// the phone number
			// in the List format output.
			moveTo("number\""); // 20120712js
			telNr = extract(">", "</").replace("&nbsp;", "").replace("*", "").trim(); // 20120712js
		} // 20120712js
		*/

		//20210402js:
		//Die Telefonnummer steht da mehrfach drin, hinter verschiedenen Tags.
		//Beim brauchbarsten gibt es zwei Formate:
		//
		//</a><span class='visible-print action-button text-center'>
		//061 274 03 29
		//</span>
		//
		//oder
		//
		//</a><span class='visible-print action-button text-center'>
		//<span class='action-button-refuse-advertising'></span>
		//061 123 45 67
		//</span>
		//
		//Wenn also nach dem ersten Versuch der refuse-advertising string herauskommt, dann direkt die nächste Zeile lesen.		
		moveTo("<span class='visible-print action-button text-'");
		telNr = extract("center'>", "</").replace("&nbsp;", "").replace("*", "").trim(); // 20120712js
		if (telNr.contains("refuse-advertising"))
			telNr = extract("span>", "</").replace("&nbsp;", "").replace("*", "").trim(); // 20120712js
		
		//20210403js:
		String fax ="";
		
		
		//pre-20210403js: e-mail parsing only included in single detailed entry processing.
		//20210403js: e-Mail was seen in the following format after the phone number:
		//<span class='lui-margin-left-xxs hidden-xs hidden-sm hidden-md hidden-print'>E-Mail</span>
		//<span class='visible-print'>lungen-schlaf-praxis.hamacher@hin.ch</span>
		String email = "";
		//if (moveTo("<span class='lui-margin-left-xxs hidden-xs hidden-sm hidden-md hidden-print'>E-Mail</span>")) { // 20210403js
		//System.out.println("CAVE: ********************************************************************************"); 
		//System.out.println("CAVE: NOT EVERYONE HAS AN E-MAIL IN THIS DIRECTORY.");
		//System.out.println("CAVE: This might accidentally bring us to the NEXT entry, thereby causing data mixing."); 
		//System.out.println("CAVE: ********************************************************************************");
		//20210403js: I added moveToNotPassing() to avoid this problem.
		if (moveToNotPassing("hidden-print'>E-Mail</span>","</div>") ) { // 20210403js
			//email = extract("<span class='visible-print'>", "</span>").trim();
			email = extract("visible-print'>", "</span>").replace("&nbsp;", "").replace("*", "").trim();
			}
		logger.debug("DirectoriesContentParser.java: extractListKontakt().email: "+email);

		// 20210404js: Please note: Fax is not available in the List format result, but e-Mail IS.
		// outdated: 20120713js: Please note: Fax and E-mail are NOT available in the List format result
		// outdated: 20131127js: And this is still the case in the next revision after 20131124js...
		return new KontaktEntry(vorname, nachname, akTitel, zusatz, //$NON-NLS-1$
			strasse, plz, ort, telNr, fax, email, false); //$NON-NLS-1$
	}
	
	/**
	 * Decodes the passed UTF-8 String using an algorithm that's compatible with JavaScript's
	 * <code>decodeURIComponent</code> function. Returns <code>null</code> if the String is
	 * <code>null</code>.
	 * 
	 * From: Utility class for JavaScript compatible UTF-8 encoding and decoding.
	 * 
	 * @see http 
	 *      ://stackoverflow.com/questions/607176/java-equivalent-to-javascripts-encodeuricomponent
	 *      -that-produces-identical-output
	 * @author John Topley
	 * 
	 * @param s
	 *            The UTF-8 encoded String to be decoded
	 * @return the decoded String
	 */
	public static String decodeURIComponent(String s){
		if (s == null) {
			return null;
		}
		String result = null;
		try {
			result = URLDecoder.decode(s, "UTF-8");
		}
		// This exception should never occur.
		catch (UnsupportedEncodingException e) {
			result = s;
		}
		return result;
	}
	
	/**
	 * Extrahiert einen Kontakt aus einem einzeln angelieferten Detaileintrag
	 * 
	 * A result of this type should be obtainable by searching for:
	 * 
	 * Wer, Was: eggimann meier		Wo: bern
	 * Wer, Was: Schoop				Wo: Bettingen
	 * Wer, Was: Dr. Hamacher		Wo: bern		(including ak. Titel, Zusatzinfo=Facharzt, Fon, Fax, e-Mail, Adresse)
	 * 
	 * TODO: 20210404js: Now, the results (at least the detailed single results)
	 * apparently contain clean data in meta info tags. Maybe use them instead of
	 * grassing through the longer HTML polluted by advertising, graphics,
	 * GUI for clicking-before-revealing-numbers etc. with all its uncertainties...
	 * 
	 * Instead of simply updateing target strings,
	 * we might give the whole extraction logic a thorough review
	 * based on a current sighting of result HTML dumps of both types beforehand.
	 *
	 * For a listing classified as a "local business":
	 *
	 * <meta content='Dr. med. Hamacher Jürg in Bern ✉ Adresse ☎ Telefonnummer ✅ auf local.ch' name='description'>
	 * <meta content='Dr. med. Hamacher Jürg' property='og:title`>
	 * <div class='container' itemscope itemtype='http://schema.org/LocalBusiness'> 
	 *	<span itemprop='name'>Dr. med. Hamacher Jürg</span>
	 *  <div class='col-xs-12 title-card-subtitle'>PD Spezialarzt FMH Innere Medizin und Pneumologie (inkl. Schlafmedizin)</div
	 *  <meta content='031 300 35 00' itemprop='telephone'>
	 *  <meta content='031 300 35 01' itemprop='faxNumber'>
	 *  <meta content='lungen-schlaf-praxis.hamacher@hin.ch' itemprop='email'>
	 *  
	 * For a listing classified as an individual person:
	 * 
	 * <meta content='S..... D..... in Bettingen ➩ Adresse &amp; Telefonnummer ☏ Das Telefonbuch von local.ch ✅ Ihre Nr. 1 für Adressen und Telefonnummern' name='description'>
	 * <div class='container' itemscope itemtype='http://schema.org/Place'>
	 */
	private KontaktEntry extractKontakt(){
		
		logger.debug("DirectoriesContentParser.java: extractKontakt() running...");
		logger.debug("Beginning of substrate: <" + extract("<", ">") + "...");
		
		if (!moveTo(ADR_SINGLEDETAILENTRY_TAG)) { // Kein neuer Eintrag
			return null;
		}
		
		logger.debug("DirectoriesContentParser.java: extractKontakt() extracting next entry...");
		
		// 20120712js: Title: This field appears before fn; it is not being
		// processed so far.
		logger.debug("DirectoriesContentParser.java: extractKontakt() Add processing of the class='title', class='urls', and optionally class='region'.");
		
		//Wegen des hinzugefügten loops für ggf. mehrere Adressen auch im Detailergebnis: Variablen hier vorab definiert,
		//damit sie später bei return ausserhalb des loops noch sichtbar sind.
		String vorname = "";
		String nachname = "";
		String akTitel = ""; 	

		String streetAddress = "";
		String poBox = "";
		String plzCode = ""; 	// 20120712js
		String ort = ""; 		// 20120712js

		String zusatz = "";
		String tel = ""; 		// 20120712js
		String fax = ""; 		// 20120712js
		String email = ""; 		// 20120712js
		
		String website = ""; 	// 20210404js

		//20210404js Previous extraction approach commented out en bloc for now. 
		//20210404js NO detection NOR support for multiple addresses per single hit result provided now.
		//20210404js See comments below (re. Helsana, Assura as examples)
		/*
		Boolean doItOnceMore = true;	//20210404js: Erlaubt mehrere Passes, um z.B. mehrere e-Mail-Adressen zu sammeln.
		while (doItOnceMore) { // 20120712js
			/*	
			//pre 20210404js
			//moveTo("<h4 class='name fn'");
			//String nameVornameText = extract(">", "</h4>"); // 20120712js
			
			if (moveTo("<div class='profession'>")) { // 20120712js
				zusatz = extractTo("</div>"); // 20120712js
			}
						
			// Anne Müller case debug output:
			logger.debug("DirectoriesContentParser.java: extractKontakt() zusatz: \""
				+ zusatz + "\"\n\n");
			
			String adressTxt = extract("<p class='address'>", "</p>").trim(); // 20120712js
			
			// Anne Müller, Bern or Eggimann Meier, Bern case debug output:
			logger.debug("DirectoriesContentParser.java: adressTxt: " + adressTxt
				+ "\n");
			
			//HtmlParser parser = new HtmlParser(adressTxt);
			
			String[] addressLines = adressTxt.split("<br/>");
			
			// String streetAddress = removeDirt(parser.extract("<span class=\"street-address\">",
			// "</span>")); // 20120712js:
			logger
				.debug("Trying to use Meta-Info collected above to parse the address content...\n");
			if (metaStrasseTrunc == null)
				logger.debug("WARNING: metaStrasseTrunc == null\n");
			else
				logger.debug("metaStrasseTrunc == " + metaStrasseTrunc + "\n");
			if (metaPLZTrunc == null)
				logger.debug("WARNING: metaPLZTrunc == null\n");
			else
				logger.debug("metaPLZTrunc == " + metaPLZTrunc + "\n");
			if (metaOrtTrunc == null)
				logger.debug("WARNING: metaOrtTrunc == null\n");
			else
				logger.debug("metaOrtTrunc == " + metaOrtTrunc + "\n");
			
			for (String thisLine : addressLines) {
				if (thisLine != null) {
					thisLine = thisLine.trim();
				}
				
				; //especially remove leading and trailing newlines. 
				if (thisLine == null)
					logger.debug("WARNING: thisLine == null\n");
				else {
					logger.debug("thisLine == " + thisLine + "\n");
					if (thisLine.startsWith(metaStrasseTrunc)) {
						streetAddress = removeDirt(thisLine);
					}
					if (thisLine.startsWith(metaPLZTrunc)) {
						int i = thisLine.indexOf(" ");
						plzCode = removeDirt(thisLine.substring(0, i));
						ort = removeDirt(thisLine.substring(i + 1));
					}
				}
			}

			//20131127js:
			//Jetzt ggf. noch die Zeilen auf poBox auswerten - dazu gibt's keinen Hint aus der MetaInfo:
			//Falls eine Zeile "Postfach" oder "Postfach..." gefunden wird, diese nach poBoxA tun.
			String poBoxA = "";
			String poBoxB = "";
			for (String thisLine : addressLines) {
				if (thisLine != null) {
					thisLine = thisLine.trim();
				}
				; //especially remove leading and trailing newlines. 
				if (thisLine == null)
					logger.debug("WARNING: thisLine == null\n");
				else {
					logger.debug("thisLine == " + thisLine + "\n");
					if (thisLine.startsWith("Postfach")) {
						poBoxA = removeDirt(thisLine);
					}
				}
			}
			//dürfte das wohl der (vom schon verarbeiteten PLZ Ort der Strassenadresse abweichende) PLZ Ort von PoBox sein.
			//Diesen dann bitte mit Komma Leerzeichen getrennt an den Eintrag der poBox anhängen.
			if (poBoxA != "") {
				for (String thisLine : addressLines) {
					if (thisLine != null) {
						thisLine = thisLine.trim();
					}
					; //especially remove leading and trailing newlines. 
					if (thisLine == null)
						logger.debug("WARNING: thisLine == null\n");
					else {
						logger.debug("thisLine == " + thisLine + "\n");
						if (thisLine.contains(metaOrtTrunc) && (!thisLine.startsWith(metaPLZTrunc))) {
							poBoxB = thisLine;
						}
					}
				}
			}
			if (poBoxB.equals("")) {
				poBox = poBoxA;
			} else {
				poBox = poBoxA + ", " + poBoxB;
			}
			;

			//20131127js:
			//Debug output zeigt, was herausgekommen ist:
			if (streetAddress == null)
				logger.debug("WARNING: streetAddress == null\n");
			else
				logger.debug("streetAddress == " + streetAddress + "\n");
			if (poBox == null)
				logger.debug("WARNING: poBox == null\n");
			else
				logger.debug("poBox == " + poBox + "\n");
			if (plzCode == null)
				logger.debug("WARNING: plzCode == null\n");
			else
				logger.debug("plzCode == " + plzCode + "\n");
			if (ort == null)
				logger.debug("WARNING: ort == null\n");
			else
				logger.debug("ort == " + ort + "\n");
			
			// If zusatz is empty, then we copy the content of poBox into zusatz.
			if (zusatz == null || zusatz.length() == 0) {
				zusatz = poBox;
			}
			
			// Tel/Fax & Email
			
			if (moveTo("<tr class='phone'>")) { // 20120712js
				if (moveTo("<span>\nTelefon:")) { // 20131127js
					if (moveTo("href=\"tel:")) { // 20131127js
						// 20120713js Don't use "refuse number" but only "number" - the "refuse " is
						// probably only there
						// for people who don't want to get called for advertising or a similar thing; there
						// is a matching
						// note on the individual Details entries; and probably an asterisk displayed left
						// of the phone number
						// in the List format output.
						moveTo("number\""); // 20120712js
						tel = extract(">", "</").replace("&nbsp;", "").replace("*", "").trim(); // 20120712js
					}
				}
			}
			if (moveTo("<tr class='fax'>")) { // 20120712js
			
				//if (moveTo("<th class='label'>\nFax:")) { // 20120712js
				if (moveTo("<span>\nFax:")) { // 20131127js
				
					moveTo("number'"); // 20120712js
					fax = extract(">", "</").replace("&nbsp;", "").replace("*", "").trim(); // 20120712js
				}
			}

			logger.debug("jsdebug: Trying to parse e-mail...\n");
			if (moveTo("<div class='email'")) { // 20131127js
				//Here we also accumulate results from multiple address entries per single result, if available; This time, separated by ;
				//If desired (or a user who does not know better uses that), it can be entered directly into several mail clients and will cause a message to be sent to each of the contained addresses. 
				if (email.equals("")) {
					email = extract("href=\"mailto:", "\">").trim();
				} else {
					email = email + "; " + extract("href=\"mailto:", "\">").trim();
				}
			}
			logger.debug("jsdebug: Trying to parse e-mail...\n");
			
			doItOnceMore = (getNextPos("<h4 class='name fn'") > 0);
			if (doItOnceMore) {
				SWTHelper
					.showInfo(
						"Warnung",
						"Dieser eine Eintrag liefert gleich mehrere Adressen.\n\nBitte führen Sie selbst eine Suche im WWW auf tel.local.ch durch,\num alle Angaben zu sehen.\n\nIch versuche, für die Namen die Informationen sinnvoll zusammenzufügen;\nfür die Adressdaten bleibt von mehreren Einträgen der letzte bestehen.\n\nFalls Sie eine Verbesserung benötigen, fragen Sie bitte\njoerg.sigle@jsigle.com - Danke!");
			}

		}
		*/
		
			//20210404js
			moveTo("<span itemprop='name'");
			String nameVornameText = extract(">", "</span>"); // 20120712js
			nameVornameText = removeDirt(nameVornameText);
			logger.debug("DirectoriesContentParser.java: extractKontakt().nameVornameText: <"+nameVornameText+">");

			// 20210404js: Leftover from previous implementations.
			// I don't know whether this is really needed. Probably not. But it shouldn't hurt either.
			// Do not return any results from empty entries!
			if (nameVornameText == null || nameVornameText.length() == 0) return null;
			
			//20210403js: Try to find and extract one or more leading academic or professional titles,
			//by looking for dots in certain distances from the beginning of the name and each other.
			//All that only if nameVornameText is not currently == null.
			akTitel = extractAkTitelFromNameVornameText(nameVornameText);
			nameVornameText = nameVornameText.substring(akTitel.length()).trim();  
					
			logger.debug("DirectoriesContentParser.java: extractKontakt().akTitel: <"+akTitel+">");
			logger.debug("DirectoriesContentParser.java: extractKontakt().nameVornameText: <"+nameVornameText+">");

			String[] vornameNachname = getVornameNachname(nameVornameText);
			
			if (vorname.equals("")) {
				vorname = vornameNachname[0];
				nachname = vornameNachname[1];
			} else {
				//Das hier ist vielleicht besser, wenn's geht:
				nachname = nachname + " " + vorname;
				vorname = vornameNachname[1] + " " + vornameNachname[0];
			}

			logger.debug("DirectoriesContentParser.java: extractKontakt(): Looking for zusatz...");

			if (moveTo("<div class='js-sticky-hidden'>")) 
				zusatz = extract("<div class='col-xs-12 title-card-subtitle'>","</div>");
			
			// You may use e.g. Anne Müller in Interlaken, Craniosacraltherapie
			// as a debug case with Zusatzinfos, phone, e-mail, URL.
			// Or Dr. Hamacher Bern with akTitel, Zusatzinfo, phone, fax, e-Mail.
			//
			// 20210404js: While updating to handle the current search result format,
			//I removed any pre20210404js approaches here to keep the code more legible.
			//Before that, there was multi line address splitting and processing etc.

			logger.debug("DirectoriesContentParser.java: extractKontakt(): Looking for address, phone, email, url...");

			//Eine Postfach-Adresse erscheint z.B. bei der Assura Pully
			//nicht im span itemprop, sondern einfach danach im Klartext
			//als weiteres Zeilenpaar mit Postfach und 1009 Pully (hier gleiche PLZ)
			if (moveTo("<span class='icon-listing'></span>")) {
				streetAddress = extract("<span itemprop=\"streetAddress\">","</span>");
				plzCode = extract("<span itemprop=\"postalCode\">","</span>");
				ort = extract("<span itemprop=\"addressLocality\">","</span>");
			}	
			
			//20210404js: Suche nach helsana, lausanne hat liefert 2 Hits,
			//die Details des ersten liefern zwei mal Telefonnummer + e-Mail. Hmpf.
			//Ich mache jetzt heute aber KEINEN support für solche mehrfachdaten.
			//Das müsste nämlich korrekterweise mehrere Einträge in der
			//medshare directories Suchtabelle liefern, wo der gleiche Teil
			//jeweils beibehalten, der variable Teil der Reihe nach verwendet wird.
			//OOODER man verteilt die verschiedenen Telefonnummern auf die mehreren
			//verfügbaren Felder. Beides ist heute jenseits meines Focus und Zeitbudgets.
			if (moveTo("<span class='icon-phone'></span>")) 
				tel = extract("<meta content='","' itemprop='telephone'>");

			if (moveTo("<span class='icon-phone'></span>")) 
				fax = extract("<meta content='","' itemprop='faxNumber'>");
			
			if (moveTo("<span class='icon-envelope'></span>")) 
				email = extract("<meta content='","' itemprop='email'>");
			
			if (moveTo("<span class='icon-website'></span>")) 
				website = extract("<meta content='","' itemprop='url'>");

		return new KontaktEntry(vorname, nachname, akTitel, zusatz, streetAddress, plzCode, ort, tel, fax,
			email, true);
	}
}
