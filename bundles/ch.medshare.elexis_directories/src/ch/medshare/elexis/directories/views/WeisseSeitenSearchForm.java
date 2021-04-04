/*******************************************************************************
 * Copyright (c) 2007, medshare and Elexis, Portions (c) 2012-2021, Joerg M. Sigle (js, jsigle)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    M. Imhof - initial implementation
 *    J. Sigle - Various revivals after local.ch output format changes,
 *               added automatic extraction and handling of academic titles
 *    
 *******************************************************************************/

package ch.medshare.elexis.directories.views;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.util.List;
import java.util.Vector;

import org.eclipse.core.runtime.ListenerList;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.KeyAdapter;
import org.eclipse.swt.events.KeyEvent;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.graphics.Cursor;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Text;

import ch.elexis.core.ui.contacts.dialogs.PatientErfassenDialog;
import ch.elexis.core.ui.dialogs.KontaktErfassenDialog;
import ch.elexis.core.ui.util.SWTHelper;
import ch.medshare.elexis.directories.DirectoriesContentParser;
import ch.medshare.elexis.directories.DirectoriesHelper;
import ch.medshare.elexis.directories.KontaktEntry;
import ch.rgw.tools.ExHandler;

public class WeisseSeitenSearchForm extends Composite {
	
	private final ListenerList listeners = new ListenerList();
	
	private List<KontaktEntry> kontakte = new Vector<KontaktEntry>();
	
	private String searchInfoText = "";
	
	public WeisseSeitenSearchForm(Composite parent, int style){
		super(parent, style);
		createPartControl(parent);
	}
	
	private void createPartControl(Composite parent){
		setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		setLayout(new GridLayout(3, false));
		
		Label nameLabel = new Label(this, SWT.NONE);
		nameLabel.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		nameLabel.setText(Messages.WeisseSeitenSearchForm_label_werWasWo); //$NON-NLS-1$
		
		Label geoLabel = new Label(this, SWT.NONE);
		geoLabel.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		geoLabel.setText(Messages.WeisseSeitenSearchForm_label_Ort); //$NON-NLS-1$
		
		new Label(this, SWT.NONE); // Platzhalter
		
		final Text nameText = new Text(this, SWT.BORDER);
		nameText.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		
		final Text geoText = new Text(this, SWT.BORDER);
		geoText.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		
		Button searchBtn = new Button(this, SWT.NONE);
		searchBtn.setText(Messages.WeisseSeitenSearchForm_btn_Suchen); //$NON-NLS-1$
		
		nameText.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e){
				if (e.character == SWT.CR) {
					searchAction(nameText.getText(), geoText.getText());
				}
			}
		});
		
		geoText.addKeyListener(new KeyAdapter() {
			public void keyPressed(KeyEvent e){
				if (e.character == SWT.CR) {
					searchAction(nameText.getText(), geoText.getText());
				}
			}
		});
		
		searchBtn.addSelectionListener(new SelectionAdapter() {
			public void widgetSelected(SelectionEvent e){
				searchAction(nameText.getText(), geoText.getText());
			}
		});
	}
	
	/**
	 * Liest Kontaktinformationen anhand der Kriterien name & geo.
	 * Bei der Suche wird die Kontakteliste und der InfoText abgefüllt.
	 */
	private void readKontakte(String name, String geo){
		final Cursor backupCursor = getShell().getCursor();
		final Cursor waitCursor = new Cursor(getShell().getDisplay(), SWT.CURSOR_WAIT);
		
		getShell().setCursor(waitCursor);
		
		//20210404js: Ensure that umlauts and accented characters etc. are properly handled.
		//Before, they went into the search URL directly - I don't know whether this ever used to work
		//but currently, www.local.ch doesn't execute a search with Umlauts in the URL parameters.
		//On the other hand, www.local.ch would currently extend a search to related targets;
		//i.e. searching for "Mueller" also returns hits for "Müller" etc.
		//Anyway. The following code ensures that searches for "Müller" will also be handled:
		//System.out.println("readKontakte name: "+name); 
		try {
			name=java.net.URLEncoder.encode(name, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			// e1.printStackTrace();
		}
		//System.out.println("readKontakte name: "+name); 

		//System.out.println("readKontakte geo: "+geo); 
		try {
			geo=java.net.URLEncoder.encode(geo, "UTF-8");
		} catch (UnsupportedEncodingException e1) {
			// TODO Auto-generated catch block
			// e1.printStackTrace();
		}
		//System.out.println("readKontakte geo: "+geo); 

		kontakte.clear();	//20210402js: With multipage processing, we can't simply replace any more... 
		int getPage = 0;
		int maxPageLimit=20;
		boolean thereAreMorePages = true;
		while ( thereAreMorePages && (getPage<maxPageLimit) ){	//20210402js: Don't allow complete runaway searches...
			getPage = getPage + 1;
			System.out.println("WeisseSeitenSearchForm.java readKontakte getPage="+getPage);
			try {
				String content = DirectoriesHelper.readContent(name, geo, getPage);
				DirectoriesContentParser parser = new DirectoriesContentParser(content);
				
				kontakte.addAll(parser.extractKontakte());
				
				searchInfoText = parser.getSearchInfo();
				
				if (getPage == 1) searchInfoText = searchInfoText+" - "+String.valueOf(getPage)+" Seite abgerufen";
				else searchInfoText = searchInfoText+" - "+String.valueOf(getPage)+" Seiten abgerufen";
				
				thereAreMorePages = parser.checkForMorePages();
				
				if ( thereAreMorePages && (getPage == maxPageLimit) )
					searchInfoText = searchInfoText+" (Abruf begrenzt - js)"; 

			} catch (IOException e) {
				ExHandler.handle(e);
			} finally {
				getShell().setCursor(backupCursor);
			}
		}
	}
	
	/**
	 * Aktion wenn Such-Button klicked oder Default-Action (Return).
	 */
	private void searchAction(String name, String geo){
		readKontakte(name, geo);
		resultChanged();
	}
	
	private void resultChanged(){
		for (Object listener : listeners.getListeners()) {
			if (listener != null) {
				((Listener) listener).handleEvent(null);
			}
		}
	}
	
	/**
	 * Retourniert String array für Dialoge
	 */
	private String[] getFields(KontaktEntry entry){
		final String name = entry.getName() + " " //$NON-NLS-1$
			+ entry.getVorname();
		final String geo = entry.getPlz() + " " //$NON-NLS-1$
			+ entry.getOrt();
		if (!entry.isDetail()) { // Sind Detailinformationen vorhanden
			readKontakte(name, geo); // Detail infos lesen
			KontaktEntry detailEntry = null;
			if (getKontakte().size() == 1) {
				detailEntry = getKontakte().get(0);
			} else if (getKontakte().size() > 1) {
				String strasse = entry.getAdresse().trim();
				for (KontaktEntry tempEntry : getKontakte()) {
					if (strasse.contains(tempEntry.getAdresse())) {
						detailEntry = tempEntry;
					}
				}
			}
			if (detailEntry != null) {
				// Falls bei Detailsuche Fehler passiert, dann sind weniger Infos vorhanden
				if (detailEntry.countNotEmptyFields() > entry.countNotEmptyFields()) {
					entry = detailEntry;
				}
			}
		}
		return new String[] {
			entry.getName(), entry.getVorname(), "", entry.getAdresse(), entry.getPlz(), //$NON-NLS-1$
			entry.getOrt(), entry.getTelefon(), entry.getZusatz(), entry.getFax(), entry.getEmail(),
			"", "", entry.getTitel()	//20210403js
		};
	}
	
	/**
	 * Öffnet Dialog zum Erfassen eines Patienten
	 */
	public void openPatientenDialog(KontaktEntry entry){
		if (entry != null) {
			final PatientErfassenDialog dialog =
				new PatientErfassenDialog(getShell(), entry.toHashmap());
			dialog.open();
		}
	}
	
	/**
	 * Öffnet Dialog zum Erfassen eines Kontaktes
	 */
	public void openKontaktDialog(KontaktEntry entry){
		if (entry != null) {
			final KontaktErfassenDialog dialog =
				new KontaktErfassenDialog(getShell(), getFields(entry));
			dialog.open();
		}
	}
	
	/**
	 * Kontakt Liste
	 */
	public List<KontaktEntry> getKontakte(){
		return this.kontakte;
	}
	
	/**
	 * Infotext zum Suchresultat: z.B. "123 Treffer"
	 */
	public String getSearchInfoText(){
		return this.searchInfoText;
	}
	
	public void addResultChangeListener(Listener listener){
		listeners.add(listener);
	}
	
	public void removeResultChangeListener(Listener listener){
		listeners.add(listener);
	}
	
}
