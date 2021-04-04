/*******************************************************************************
 * Copyright (c) 2007, medshare and Elexis; Portions (c) 2021 Joerg M. Sigle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    M. Imhof - initial implementation
 *    J. Sigle - Added moveToNotPassing(key, barrier) - so that we can e.g.
 *               search for an e-Mail address belonging to a data set, and
 *               even when this address does NOT have an e-Mail, we won't
 *               accidentally pass into a later address while searching. 
 *    
 *******************************************************************************/

package ch.medshare.elexis.directories;

public class HtmlParser {
	private final StringBuffer htmlText;
	private int currentPos = 0;
	
	public HtmlParser(String htmlText){
		super();
		this.htmlText = new StringBuffer(htmlText);
	}
	
	public void reset(){
		this.currentPos = 0;
	}
	
	public boolean startsWith(String prefix){
		if (prefix == null) {
			return false;
		}
		return htmlText.substring(currentPos, currentPos + prefix.length()).startsWith(prefix);
	}
	
	/**
	 * Verschiebt Cursor bis zur Position nach dem gefundenen String
	 */
	public boolean moveTo(String keyString){
		int newPos = getNextPos(keyString);
		if (newPos >= 0) {
			currentPos = newPos + keyString.length();
			display(currentPos);
			return true;
		}
		return false;
	}
	
	/**
	 * 20210403js:
	 * Verschiebt Cursor bis zur Position nach dem gefundenen String,
	 * aber nur dann, wenn man dafür nicht über barrierString hinweggehen muss.
	 * Andernfalls wid die Position currentPos NICHT verändert und false zurückgegeben.
	 * 
	 * Wenn z.B. nach dem Feld Telefonnummer auch noch das Feld e-Mail
	 * für den aktuellen Eintrag gesucht werden soll, dann muss verhindert werden,
	 * dass die (not-XML-aware) Text-Suche dazu über das Ende des aktuellen Datensatzes
	 * hinausspringt, und die nächste gefundene e-Mail in Wirklichkeit zu irgendeinem
	 * späteren Datensatz gehört.  
	 */
	public boolean moveToNotPassing(String keyString, String barrierString){
		int newPosKey = getNextPos(keyString);
		int newPosBarrier = getNextPos(barrierString);
		
		if ( (newPosKey > -1)
		   && ( ( newPosBarrier < -1) || (newPosKey < newPosBarrier ) ) ) {
			currentPos = newPosKey + keyString.length();
			display(currentPos);
			return true;
		}
		return false;
	}
	
	public String getTail(){
		return htmlText.substring(currentPos, htmlText.length());
	}
	
	public String extractTo(String endKeyString){
		int newPos = getNextPos(endKeyString);
		String text = "";
		if (newPos >= 0) {
			text = htmlText.substring(currentPos, newPos);
			currentPos = newPos + endKeyString.length();
			display(currentPos);
		}
		
		return text;
	}
	
	private void display(int pos){
		if (true) {
			return;
		}
		int theEnd = pos + 1000;
		if (theEnd >= htmlText.length()) {
			theEnd = htmlText.length() - 1;
		}
		if (pos < theEnd) {
			System.out.println("Current: " + htmlText.substring(pos, theEnd));
		}
	}
	
	public String extract(String startKeyString, String endKeyString){
		if (moveTo(startKeyString)) {
			return extractTo(endKeyString);
		}
		return "";
	}
	
	public int getNextPos(String keyString, int pos){
		return htmlText.indexOf(keyString, pos);
	}
	
	public int getNextPos(String keyString){
		return getNextPos(keyString, currentPos);
	}
}
