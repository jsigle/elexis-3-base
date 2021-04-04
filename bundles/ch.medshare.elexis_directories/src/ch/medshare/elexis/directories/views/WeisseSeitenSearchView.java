/*******************************************************************************
 * Copyright (c) 2007, medshare and Elexis, Portions (c) 2021, Joerg M. Sigle (js, jsigle)
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    M. Imhof - initial implementation
 *    J. Sigle - added automatic extraction and handling of academic titles
 *    
 *******************************************************************************/

package ch.medshare.elexis.directories.views;

import java.util.Iterator;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.IStructuredContentProvider;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.StructuredSelection;
import org.eclipse.jface.viewers.TableViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.jface.viewers.ViewerSorter;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Event;
import org.eclipse.swt.widgets.Listener;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Table;
import org.eclipse.swt.widgets.TableColumn;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.part.ViewPart;

import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.util.SWTHelper;
import ch.medshare.elexis.directories.KontaktEntry;

/**
 * Weisse-Seiten View. Diese View besteht aus zwei Eingabefelder und einer Liste der gefundenen
 * Resultate.
 */

public class WeisseSeitenSearchView extends ViewPart {
	private TableViewer kontakteTableViewer;
	private Text searchInfoText;
	private Action newPatientAction;
	private Action newKontaktAction;
	WeisseSeitenSearchForm searchForm;
	
	class WhitePageLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object element, int columnIndex){
			KontaktEntry entry = (KontaktEntry) element;
			switch (columnIndex) {
			//20210403js: Bring the title back into the display,
			//which is now separately delivered from the extractor 
			case 0:
				return entry.getTitel();
			case 1:
				return entry.getName() + " " + entry.getVorname(); //$NON-NLS-1$
			//20210404js: Bring zusatz into the display (e.g. Facharztbezeichnung etc.)
			//which is now also delivered from the extractor 
			case 2:
				return entry.getZusatz();
			case 3:
				return entry.getAdresse();
			case 4:
				return entry.getPlz();
			case 5:
				return entry.getOrt();
			case 6:
				return entry.getTelefon();
			//20210404js: Bring fax into the display
			//which is now also delivered from the extractor 
			case 7:
				return entry.getFax();
			//20210403js: Bring e-Mail into the display
			//which is now also delivered from the extractor 
			case 8:
				return entry.getEmail();
			default:
				return "-"; //$NON-NLS-1$
			}
		}
		
		public Image getColumnImage(Object element, int columnIndex){
			return null;
		}
	}
	
	class WhitePageContentProvider implements IStructuredContentProvider {
		public Object[] getElements(Object inputElement){
			return ((List<?>) inputElement).toArray();
		}
		
		public void dispose(){}
		
		public void inputChanged(Viewer viewer, Object oldInput, Object newInput){}
	}
	
	class KontaktSorter extends ViewerSorter {
		public int compare(final Viewer viewer, final Object e1, final Object e2){
			String s1 = ((KontaktEntry) e1).getName() + ((KontaktEntry) e1).getVorname();
			//20210403js: Probably wrong reference to e1 in the original line:
			//String s2 = ((KontaktEntry) e2).getName() + ((KontaktEntry) e1).getVorname();
			String s2 = ((KontaktEntry) e2).getName() + ((KontaktEntry) e2).getVorname();
			return s1.compareTo(s2);
		}
	}
	
	/**
	 * The constructor.
	 */
	public WeisseSeitenSearchView(){}
	
	/**
	 * Inhalt der View aufbauen
	 */
	public void createPartControl(Composite parent){
		parent.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		parent.setLayout(new GridLayout(1, false));
		
		// SuchForm
		searchForm = new WeisseSeitenSearchForm(parent, SWT.NONE);
		searchForm.addResultChangeListener(new Listener() {
			public void handleEvent(Event event){
				showResult();
			}
		});
		
		// Liste
		Composite listArea = new Composite(parent, SWT.NONE);
		listArea.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		listArea.setLayout(new GridLayout(1, false));
		
		searchInfoText = new Text(listArea, SWT.NONE);
		searchInfoText.setEnabled(false);
		searchInfoText.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		
		Table table =
			new Table(listArea, SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.MULTI | SWT.BORDER);
		//20210403js: Bring the title into the display.
		//20210404js: Bring the fields Zusatz and Fax into the display.
		//All of these are now delivered (separately) from the extractor 
		//All allignments changed to SWT.LEFT
		//All widths are multiples of 40 now, except for Fon/Fax
		TableColumn akTitelTc = new TableColumn(table, SWT.LEFT);
		akTitelTc.setText(Messages.WeisseSeitenSearchView_header_akTitel); //$NON-NLS-1$
		akTitelTc.setWidth(80);

		TableColumn nameTc = new TableColumn(table, SWT.LEFT);
		nameTc.setText(Messages.WeisseSeitenSearchView_header_Name); //$NON-NLS-1$
		nameTc.setWidth(160);
		
		TableColumn zusatzTc = new TableColumn(table, SWT.LEFT);
		zusatzTc.setText(Messages.WeisseSeitenSearchView_header_Zusatz); //$NON-NLS-1$
		zusatzTc.setWidth(160);
		
		TableColumn adrTc = new TableColumn(table, SWT.LEFT);
		adrTc.setText(Messages.WeisseSeitenSearchView_header_Adresse); //$NON-NLS-1$
		adrTc.setWidth(160);
		
		TableColumn plzTc = new TableColumn(table, SWT.LEFT);
		plzTc.setText(Messages.WeisseSeitenSearchView_header_Plz); //$NON-NLS-1$
		plzTc.setWidth(40);
		
		TableColumn ortTc = new TableColumn(table, SWT.LEFT);
		ortTc.setText(Messages.WeisseSeitenSearchView_header_Ort); //$NON-NLS-1$
		ortTc.setWidth(160);
		
		TableColumn telTc = new TableColumn(table, SWT.LEFT);
		telTc.setText(Messages.WeisseSeitenSearchView_header_Tel); //$NON-NLS-1$
		telTc.setWidth(90);
		
		TableColumn faxTc = new TableColumn(table, SWT.LEFT);
		faxTc.setText(Messages.WeisseSeitenSearchView_header_Fax); //$NON-NLS-1$
		faxTc.setWidth(90);
		
		TableColumn emailTc = new TableColumn(table, SWT.LEFT);
		emailTc.setText(Messages.WeisseSeitenSearchView_header_eMail); //$NON-NLS-1$
		emailTc.setWidth(200);
		
		table.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		
		kontakteTableViewer = new TableViewer(table);
		kontakteTableViewer.setContentProvider(new WhitePageContentProvider());
		kontakteTableViewer.setLabelProvider(new WhitePageLabelProvider());
		kontakteTableViewer.setSorter(new KontaktSorter());
		getSite().setSelectionProvider(kontakteTableViewer);
		
		makeActions();
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
	}
	
	/**
	 * Falls Suche geändert hat, werden die neuen Resultate angezeigt. Dies ist ein Callback der
	 * SearchForm
	 */
	private void showResult(){
		try {
			kontakteTableViewer.setInput(searchForm.getKontakte());
			searchInfoText.setText(searchForm.getSearchInfoText());
			if (kontakteTableViewer.getTable().getItems().length > 0) {
				kontakteTableViewer.getTable().select(0);
			}
			kontakteTableViewer.getTable().setFocus();
		} catch (Exception e) {
			showMessage(e.getMessage());
		}
	}
	
	private void hookContextMenu(){
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager){
				fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(kontakteTableViewer.getControl());
		kontakteTableViewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, kontakteTableViewer);
	}
	
	private void contributeToActionBars(){
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}
	
	private void fillLocalPullDown(IMenuManager manager){
		manager.add(newKontaktAction);
		manager.add(new Separator());
		manager.add(newPatientAction);
	}
	
	private void fillContextMenu(IMenuManager manager){
		manager.add(newKontaktAction);
		manager.add(new Separator());
		manager.add(newPatientAction);
	}
	
	private void fillLocalToolBar(IToolBarManager manager){
		manager.add(newKontaktAction);
		manager.add(new Separator());
		manager.add(newPatientAction);
	}
	
	@SuppressWarnings("unchecked")
	private void openPatientenDialog(){
		final StructuredSelection selection =
			(StructuredSelection) kontakteTableViewer.getSelection();
		if (!selection.isEmpty()) {
			Iterator<KontaktEntry> iterator = selection.iterator();
			while (iterator.hasNext()) {
				final KontaktEntry selectedKontakt = iterator.next();
				searchForm.openPatientenDialog(selectedKontakt);
			}
		}
	}
	
	@SuppressWarnings("unchecked")
	private void openKontaktDialog(){
		final StructuredSelection selection =
			(StructuredSelection) kontakteTableViewer.getSelection();
		if (!selection.isEmpty()) {
			Iterator<KontaktEntry> iterator = selection.iterator();
			while (iterator.hasNext()) {
				final KontaktEntry selectedKontakt = iterator.next();
				searchForm.openKontaktDialog(selectedKontakt);
			}
		}
	}
	
	private void makeActions(){
		newPatientAction = new Action() {
			public void run(){
				openPatientenDialog();
			}
		};
		newPatientAction.setText(Messages.WeisseSeitenSearchView_popup_newPatient); //$NON-NLS-1$
		newPatientAction.setToolTipText(Messages.WeisseSeitenSearchView_tooltip_newPatient); //$NON-NLS-1$
		newPatientAction.setImageDescriptor(Images.IMG_PERSON_ADD.getImageDescriptor());
		
		newKontaktAction = new Action() {
			public void run(){
				openKontaktDialog();
			}
		};
		newKontaktAction.setText(Messages.WeisseSeitenSearchView_popup_newKontakt); //$NON-NLS-1$
		newKontaktAction.setToolTipText(Messages
			.WeisseSeitenSearchView_tooltip_newKontakt); //$NON-NLS-1$
		newKontaktAction.setImageDescriptor(Images.IMG_NEW.getImageDescriptor());
	}
	
	private void hookDoubleClickAction(){
		kontakteTableViewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event){
				newKontaktAction.run();
			}
		});
	}
	
	private void showMessage(String message){
		MessageDialog.openInformation(kontakteTableViewer.getControl().getShell(),
			"ch.elexis.WeiSeitSearch", message); //$NON-NLS-1$
	}
	
	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus(){
		kontakteTableViewer.getControl().setFocus();
	}
}