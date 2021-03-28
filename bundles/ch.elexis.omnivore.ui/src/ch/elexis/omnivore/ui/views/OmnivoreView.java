/*******************************************************************************
 * Copyright (c) 2006-2011, G. Weirich and Elexis; Portions Copyright (c) 2013-2021 Joerg Sigle
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    J. Sigle - Added drag support, so stored documents can be dragged into an e-mail
 *               program (MS Outlook is more difficult to serve than Thunderbird)
 *    G. Weirich - initial implementation
 * 
 *******************************************************************************/

//TODO: 20210327js - notiert in OmnivoreView.java + DocHandle.java.
//
//Ich hab jetzt nochmals in OmnivoreView.java + DocHandle.java
//die beiden Varianten der dragSourceAdapter / dragSourceListener
//von mir und in Niklaus' Version verglichen durch wechselweises Ein-/Aus-kommentieren. 
//
//Nachdem ich TextTransfer.getInstance() in Niklaus' Version herausgeworfen habe,
//sind die anscheinend jetzt gleichwertig - wenn TextTransfer() enthalten ist,
//dann kann man ein aus Omnivore herausgedraggtes File NICHT in den Text-Bereich
//einer e-Mail in Outlook legen, sonst erscheint dort die String-Version eines Handlers o.ä. für das gedroppte Objekt, statt das File als Attachment an die Mail anzuhängen.
//
//Beide gehen dann, wenn man das herausgedraggte File in Outlook oberhalb des Bereichs
//für die Adresse und unterhalb der Menüzeil fallen lässt - also rechts neben den
//Accelerators, oder später wenn die Attachments-Liste da ist, dorthin - NUR NICHT
//in den Menübereich oder in den Bereich für die Adressen hinein.
//
//Das gilt mit Niklaus' dragSourceAdapter in OmniVoreView.java,
//und mit meinem execute() in DocHandle.java oder mit der einfacheren zuletzt
//bei 3.x gesehenen execute() in DochHandle.java.
//
//Die einfachere execute() Function von Niklaus in DocHandle.java lässt
//beim Öffnen der .jnt Documente (wo ich ja eine batch-Datei als Default
//Application in Win7 verknüpft habe, damit es geht) eben das schwarze Fenster
//der Batch-Datei offen; meine etwas längere Version macht das nicht - das ist
//schöner und nützlicher. Darum lasse ich meine execute() Variante vorerst stehen.
//
//Die einfachere Variante von Niklaus ist allerdings mit dem nach Utils ausgelagerten
//createTemporaryFile() kompatibel bzw. verwendet das; dafür beginnt sie mit
//String ext = StringConstants.SPACE; //""; //$NON-NLS-1$
//und kümmert sich anscheinend erst mal nicht um die korrekte Dateiendung;
//d.h. das jetzt wieder restaurierte obtainExt() war nicht da und wurde nicht verwendet,
//in der jetzt wieder hereingeholten Fassung wurde die passende Endung hingegen an
//makeTempFile(String ext)  übergeben - auch in Utils.createNiceFilename(DocHandle dh)
//wird das in keiner Weise nachgeholt.
//
//Insofern ist das nochmal ein Aufräumen mit genauem Nachschauen der erreichten Ergebnisse nötig.
//Ich lasse jetzt beide Code-Teile drin (insbesondere auch meine ursprüngliche Fassung
//aus Elexis 2.1.7js / Omnivore_js für die mit drag&drop-Support zu Outlook gekennzeichneten 
//Teile aus 201305... und die dazugehörige ursprüngliche Erzeugung der sprechenden Dateinamen).
//
//Das muss schon deshalb reviewt werden, weil ja (schon damals notiertes) Ziel ist,
//dass Omnivore UND msword_js (bzw.: noatext_js) und wer das auch immer sonst noch tun will
//für das Erzeugen von aussagekräftigen temp-Dateinamen eben denselben Code in Utils.java
//verwenden würden. Dann muss der Code dort aber auch die passenden Parameter-Typen akzeptieren
//und möglicherweise eher mit der richtigen .ext als mit dem Titel (wie bei der von Niklaus
//ausgelagerten Fassung) beginnen können - Bitte auch schauen, ob ich damals zwei verschiedene
//Varianten hatte ebendafür, das ist aber NICHTS für jetzt und heute. /js
//
//Ich lasse jetzt MEINE Version des drag-supports und der execute() aktiv,
//ändere aber die System.out.println() in log.debug() Ausgaben.

package ch.elexis.omnivore.ui.views;

import static ch.elexis.omnivore.Constants.CATEGORY_MIMETYPE;

import java.io.File;
import java.text.MessageFormat;
import java.util.LinkedList;
import java.util.List;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IMenuListener;
import org.eclipse.jface.action.IMenuManager;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.MenuManager;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.InputDialog;
import org.eclipse.jface.resource.ImageDescriptor;
import org.eclipse.jface.viewers.ArrayContentProvider;
import org.eclipse.jface.viewers.DoubleClickEvent;
import org.eclipse.jface.viewers.IDoubleClickListener;
import org.eclipse.jface.viewers.ISelection;
import org.eclipse.jface.viewers.ISelectionChangedListener;
import org.eclipse.jface.viewers.IStructuredSelection;
import org.eclipse.jface.viewers.ITableLabelProvider;
import org.eclipse.jface.viewers.ITreeContentProvider;
import org.eclipse.jface.viewers.LabelProvider;
import org.eclipse.jface.viewers.SelectionChangedEvent;
import org.eclipse.jface.viewers.TreeViewer;
import org.eclipse.jface.viewers.Viewer;
import org.eclipse.swt.SWT;
import org.eclipse.swt.dnd.DND;

//20210327js: Niklaus' adopted version 
import org.eclipse.swt.dnd.DragSourceAdapter;
//20210328js: TextTransfer must NOT be enabled, otherwise dropping an entry
//from omnivore into the e-Mail text window of MS Outlook will result in
//an identifier string put into the e-Mail text (not desired) instead of
//having the actual file added as an e-Mail attachment (desired).
//import org.eclipse.swt.dnd.TextTransfer;

//20210327js: js original Version
import org.eclipse.swt.dnd.DragSourceListener;

import org.eclipse.swt.dnd.DragSourceEvent;
import org.eclipse.swt.dnd.DropTargetAdapter;
import org.eclipse.swt.dnd.DropTargetEvent;
import org.eclipse.swt.dnd.FileTransfer;
import org.eclipse.swt.dnd.Transfer;
import org.eclipse.swt.events.ModifyEvent;
import org.eclipse.swt.events.ModifyListener;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.events.SelectionListener;
import org.eclipse.swt.graphics.Image;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Display;
import org.eclipse.swt.widgets.FileDialog;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.Text;
import org.eclipse.swt.widgets.Tree;
import org.eclipse.swt.widgets.TreeColumn;
import org.eclipse.ui.IActionBars;
import org.eclipse.ui.ISharedImages;
import org.eclipse.ui.IWorkbenchPartReference;
import org.eclipse.ui.PlatformUI;
import org.eclipse.ui.dialogs.ListDialog;
import org.eclipse.ui.part.ViewPart;
import org.eclipse.ui.plugin.AbstractUIPlugin;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.admin.AccessControlDefaults;
import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.data.events.ElexisEvent;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.ui.actions.RestrictedAction;
import ch.elexis.core.ui.events.ElexisUiEventListenerImpl;
import ch.elexis.core.ui.events.RefreshingPartListener;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.locks.AcquireLockBlockingUi;
import ch.elexis.core.ui.locks.ILockHandler;
import ch.elexis.core.ui.locks.LockRequestingRestrictedAction;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.core.ui.util.viewers.DefaultLabelProvider;
import ch.elexis.core.ui.views.IRefreshable;
import ch.elexis.data.Anwender;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Query;
import ch.elexis.omnivore.data.AutomaticBilling;
import ch.elexis.omnivore.data.DocHandle;
import ch.elexis.omnivore.ui.Messages;
import ch.elexis.omnivore.ui.preferences.PreferencePage;

/**
 * A class do receive documents by drag&drop. Documents are imported into the database and linked to
 * the selected patient. On double-click they are opened with their associated application.
 * 201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
 * With this respect, thunderbird is easy - outlook is difficult to serve.
 */

public class OmnivoreView extends ViewPart implements IRefreshable {
	private TreeViewer viewer;
	private Tree table;
	RestrictedAction editAction, deleteAction, importAction;
	public static String importAction_ID = "ch.elexis.omnivore.data.OmnivoreView.importAction";
	
	private Action exportAction;
	private Action doubleClickAction;
	private Action flatViewAction;
	private final String[] colLabels =
		{
			"", Messages.OmnivoreView_categoryColumn, Messages.OmnivoreView_dateColumn, Messages.OmnivoreView_titleColumn, //$NON-NLS-1$
			Messages.OmnivoreView_keywordsColumn
		};
	private final String colWidth = "20,80,80,150,500";
	private final String sortSettings = "0,1,-1,false";
	private boolean bFlat;
	private String searchTitle = "";
	private String searchKW = "";
	// ISource selectedSource = null;
	static Logger log = LoggerFactory.getLogger(OmnivoreView.class);
	
	private OmnivoreViewerComparator ovComparator;
	
	private Patient actPatient;
	
	private RefreshingPartListener udpateOnVisible = new RefreshingPartListener(this) {
		@Override
		public void partDeactivated(IWorkbenchPartReference partRef){
			if(isMatchingPart(partRef)) {
				saveColumnWidthSettings();
				saveSortSettings();
			}
		}
	};
	
	private final ElexisUiEventListenerImpl eeli_pat = new ElexisUiEventListenerImpl(Patient.class,
		ElexisEvent.EVENT_SELECTED) {
		
		@Override
		public void runInUi(ElexisEvent ev){
			if(isActiveControl(table)) {
				if(actPatient != ev.getObject()) {
					viewer.refresh();
					actPatient = (Patient) ev.getObject();
				}
			}
		}
	};
	
	private final ElexisUiEventListenerImpl eeli_user = new ElexisUiEventListenerImpl(
		Anwender.class, ElexisEvent.EVENT_USER_CHANGED) {
		@Override
		public void runInUi(ElexisEvent ev){
			viewer.refresh();
			importAction.reflectRight();
			editAction.reflectRight();
			deleteAction.reflectRight();
			
		}
	};
	
	private final ElexisUiEventListenerImpl eeli_dochandle = new ElexisUiEventListenerImpl(
		DocHandle.class, ElexisEvent.EVENT_CREATE | ElexisEvent.EVENT_DELETE
			| ElexisEvent.EVENT_UPDATE) {
		@Override
		public void runInUi(ElexisEvent ev){
			if(isActiveControl(table)) {
				viewer.refresh();
			}
		}
	};
	
	class ViewContentProvider implements ITreeContentProvider {
		public void inputChanged(Viewer v, Object oldInput, Object newInput){}
		
		public void dispose(){}
		
		/** Add filters for search to query */
		private void addFilters(Query<DocHandle> qbe){
			qbe.add(DocHandle.FLD_TITLE, Query.LIKE, "%" + searchTitle + "%");
			// Add every keyword
			for (String kw : searchKW.split(" ")) {
				qbe.add(DocHandle.FLD_KEYWORDS, Query.LIKE, "%" + kw + "%");
			}
		}
		
		private boolean filterMatches(String[] kws, DocHandle h){
			if (!h.getTitle().toLowerCase().contains(searchTitle.toLowerCase()))
				return false;
			String dkw = h.getKeywords().toLowerCase();
			for (String kw : kws) {
				if (!dkw.contains(kw)) {
					return false;
				}
			}
			return true;
		}
		
		/** Filter a list of DocHandles */
		private List<DocHandle> filterList(List<DocHandle> list){
			List<DocHandle> result = new LinkedList<DocHandle>();
			String[] kws = searchKW.toLowerCase().split(" ");
			for (DocHandle dh : list) {
				if (filterMatches(kws, dh))
					result.add(dh);
			}
			return result;
		}
		
		public Object[] getElements(Object parent){
			List<DocHandle> ret = new LinkedList<DocHandle>();
			Patient pat = ElexisEventDispatcher.getSelectedPatient();
			if (!bFlat && pat != null) {
				List<DocHandle> cats = DocHandle.getMainCategories();
				for (DocHandle dh : cats) {
					if (filterList(dh.getMembers(pat)).size() > 0) {
						ret.add(dh);
					}
				}
				Query<DocHandle> qbe = new Query<DocHandle>(DocHandle.class);
				qbe.add(DocHandle.FLD_PATID, Query.EQUALS, pat.getId());
				qbe.add(DocHandle.FLD_CAT, "", null); //$NON-NLS-1$
				addFilters(qbe);
				List<DocHandle> root = qbe.execute();
				ret.addAll(root);
			} else if (pat != null) {
				// Flat view -> all documents that
				Query<DocHandle> qbe = new Query<DocHandle>(DocHandle.class);
				qbe.add(DocHandle.FLD_PATID, Query.EQUALS, pat.getId());
				addFilters(qbe);
				List<DocHandle> docs = qbe.execute();
				for (DocHandle dh : docs) {
					if (!dh.isCategory())
						ret.add(dh);
				}
			}
			return ret.toArray();
		}
		
		public Object[] getChildren(Object parentElement){
			Patient pat = ElexisEventDispatcher.getSelectedPatient();
			if (!bFlat && pat != null && (parentElement instanceof DocHandle)) {
				DocHandle dhParent = (DocHandle) parentElement;
				return filterList(dhParent.getMembers(pat)).toArray();
			} else {
				return new Object[0];
			}
		}
		
		public Object getParent(Object element){
			if (!bFlat && element instanceof DocHandle) {
				DocHandle dh = (DocHandle) element;
				return dh.getCategory();
			}
			return null;
		}
		
		public boolean hasChildren(Object element){
			if (element instanceof DocHandle) {
				DocHandle dh = (DocHandle) element;
				return dh.isCategory();
			}
			return false;
		}
	}
	
	class ViewLabelProvider extends LabelProvider implements ITableLabelProvider {
		public String getColumnText(Object obj, int index){
			DocHandle dh = (DocHandle) obj;
			switch (index) {
			case 0:
				return ""; //$NON-NLS-1$
			case 1:
				if (bFlat)
					return dh.getCategoryName();
				return dh.isCategory() ? dh.getTitle() : ""; //$NON-NLS-1$
			case 2:
				return dh.isCategory() ? "" : dh.getDate(); //$NON-NLS-1$
			case 3:
				return dh.isCategory() ? "" : dh.getTitle(); //$NON-NLS-1$
			case 4:
				return dh.isCategory() ? "" : dh.get(DocHandle.FLD_KEYWORDS); //$NON-NLS-1$
			default:
				return "?"; //$NON-NLS-1$
			}
		}
		
		public Image getColumnImage(Object obj, int index){
			return null; // getImage(obj);
		}
		
		public Image getImage(Object obj){
			return PlatformUI.getWorkbench().getSharedImages()
				.getImage(ISharedImages.IMG_OBJ_ELEMENT);
		}
	}
	
	/**
	 * The constructor.
	 */
	public OmnivoreView(){
		DocHandle.load(StringConstants.ONE); // make sure the table is created
	}
	
	/**
	 * This is a callback that will allow us to create the viewer and initialize it.
	 */
	public void createPartControl(Composite parent){
		parent.setLayout(new GridLayout(4, false));
		
		// Title search field
		Label lSearchTitle = new Label(parent, SWT.NONE);
		lSearchTitle.setText(Messages.OmnivoreView_searchTitleLabel);
		lSearchTitle.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		final Text tSearchTitle = new Text(parent, SWT.SINGLE);
		tSearchTitle.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		
		// Keyword search field
		Label lSearchKW = new Label(parent, SWT.NONE);
		lSearchKW.setText(Messages.OmnivoreView_searchKeywordsLabel);
		lSearchKW.setLayoutData(new GridData(SWT.LEFT, SWT.TOP, false, false));
		final Text tSearchKW = new Text(parent, SWT.SINGLE);
		tSearchKW.setLayoutData(new GridData(SWT.FILL, SWT.TOP, true, false));
		
		// Add search listener
		ModifyListener searchListener = new ModifyListener() {
			public void modifyText(ModifyEvent e){
				searchKW = tSearchKW.getText();
				searchTitle = tSearchTitle.getText();
				refresh();
			}
		};
		tSearchTitle.addModifyListener(searchListener);
		tSearchKW.addModifyListener(searchListener);
		
		// Table to display documents
		table = new Tree(parent, SWT.H_SCROLL | SWT.V_SCROLL | SWT.FULL_SELECTION | SWT.MULTI);
		TreeColumn[] cols = new TreeColumn[colLabels.length];
		for (int i = 0; i < colLabels.length; i++) {
			cols[i] = new TreeColumn(table, SWT.NONE);
			cols[i].setText(colLabels[i]);
			cols[i].setData(new Integer(i));
		}
		applyUsersColumnWidthSetting();
		
		table.setHeaderVisible(true);
		table.setLinesVisible(true);
		table.setLayoutData(new GridData(SWT.FILL, SWT.FILL, true, true, 4, 1));
		
		viewer = new TreeViewer(table);
		viewer.setContentProvider(new ViewContentProvider());
		viewer.setLabelProvider(new ViewLabelProvider());
		viewer.setUseHashlookup(true);
		makeActions();
		
		ovComparator = new OmnivoreViewerComparator();
		viewer.setComparator(ovComparator);
		TreeColumn[] treeCols = viewer.getTree().getColumns();
		for (int i = 0; i < treeCols.length; i++) {
			TreeColumn tc = treeCols[i];
			tc.addSelectionListener(getSelectionAdapter(tc, i));
		}
		applySortDirection();
		
		hookContextMenu();
		hookDoubleClickAction();
		contributeToActionBars();
		
		final Transfer[] dropTransferTypes = new Transfer[] {
			FileTransfer.getInstance()
		};
		
		viewer.addDropSupport(DND.DROP_COPY, dropTransferTypes, new DropTargetAdapter() {
			
			@Override
			public void dragEnter(DropTargetEvent event){
				event.detail = DND.DROP_COPY;
			}
			
			@Override
			public void drop(DropTargetEvent event){
				if (dropTransferTypes[0].isSupportedType(event.currentDataType)) {
					String[] files = (String[]) event.data;
					String category = null;
					if (event.item != null && event.item.getData() instanceof DocHandle) {
						DocHandle dh = (DocHandle) event.item.getData();
						category = dh.getCategory();
					}
					for (String file : files) {
						final DocHandle handle = DocHandle.assimilate(file, category);
						if (handle != null) {
							AcquireLockBlockingUi.aquireAndRun(handle, new ILockHandler() {
								@Override
								public void lockFailed(){
									handle.delete();
								}
								
								@Override
								public void lockAcquired(){
									// do automatic billing if configured
									if (AutomaticBilling.isEnabled()) {
										AutomaticBilling billing = new AutomaticBilling(handle);
										billing.bill();
									}
								}
							});
						}
						viewer.refresh();
					}
				}
			}
			
		});
			
		//TODO: 20210327js: Check whether this is really better:
		//Revert the drag source support to what I had in 2.1.7js/omnivore_js -
		//as this also works with MS Office targets like outlook.
		//MAYBE both versions work in quite the same way - and it only depends on where
		//you drop the file in Outlook 2003. If I drop it into the Area ABOVE the
		//header fields, right of some accelerator-buttons, below the menu-bar,
		//then it works fine with my original code.
		//If I drop it BELOW the header field or even in the main text area,
		//it won't work (not as expected, or not at all).
		//In thunderbird, it's easier to hit the right drop area.
		
		//It should be tested whether the version supplied with 3.7 by Niklaus
		//(which is very very similar apart from a few small changes) might
		//work just as well.
		
		//Niklaus' adopted version
		/*
		final Transfer[] dragTransferTypes = new Transfer[] {
			FileTransfer.getInstance()
			//20210328js: TextTransfer must NOT be enabled, otherwise dropping an entry
			//from omnivore into the e-Mail text window of MS Outlook will result in
			//an identifier string put into the e-Mail text (not desired) instead of
			//having the actual file added as an e-Mail attachment (desired).
			//, TextTransfer.getInstance()
		};

		viewer.addDragSupport(DND.DROP_COPY, dragTransferTypes, new DragSourceAdapter() {
			@Override
			public void dragStart(DragSourceEvent event){
				IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
				for (Object object : selection.toList()) {
					DocHandle dh = (DocHandle) object;
					if (dh.isCategory()) {
						event.doit = false;
					}
				}
			}
			
			@Override
			public void dragSetData(DragSourceEvent event){
				IStructuredSelection selection = (IStructuredSelection) viewer.getSelection();
				
				if (FileTransfer.getInstance().isSupportedType(event.dataType)) {
					String[] files = new String[selection.size()];
					for (int index = 0; index < selection.size(); index++) {
						DocHandle dh = (DocHandle) selection.toList().get(index);
						File file = dh.createTemporaryFile(dh.getTitle());
						files[index] = file.getAbsolutePath();
						log.debug("dragSetData; isSupportedType {} data {}", file.getAbsolutePath(), //$NON-NLS-1$
							event.data);
					}
					event.data = files;
				} else {
					StringBuilder sb = new StringBuilder();
					for (int index = 0; index < selection.size(); index++) {
						DocHandle dh = (DocHandle) selection.toList().get(index);
						sb.append(((PersistentObject) dh).storeToString()).append(","); //$NON-NLS-1$
						log.debug("dragSetData; unsupported dataType {} returning {}", //$NON-NLS-1$
							event.dataType, sb.toString().replace(",$", ""));
					}
					event.data = sb.toString().replace(",$", ""); //$NON-NLS-1$ //$NON-NLS-2$
				}
			}
		});
		*/		
		//20210327js: end of Niklaus' edited version for drag source support

		//20210327js: Joerg's original version - this definitely works even with MS Outlook;
		//The entry from omnivore must be dropped in outlook above the area
		//with the To/CC/Subject input fields, just right of the accelerator buttons.
		
		final Transfer[] dragTransferTypes = new Transfer[] {
				FileTransfer.getInstance()
			};
		//201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
		viewer.addDragSupport(DND.DROP_COPY, dragTransferTypes, new DragSourceListener() {
			@Override 
			public void dragStart(DragSourceEvent event){
				event.doit = true;
				event.detail = DND.DROP_MOVE;
				log.debug("js OmnivoreView.java: viewer.addDragSupport(): dragStart");
			}

			@Override
			public void dragSetData(DragSourceEvent event){
				
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();

				if (obj != null) {
					DocHandle dh = (DocHandle) obj;
					log.debug("js OmnivoreView.java: viewer.addDragSupport(): dh.getGUID() = <{}>", dh.getGUID());
					log.debug("js OmnivoreView.java: viewer.addDragSupport(): dh.getTitle() = <{}>", dh.getTitle());
					log.debug("js OmnivoreView.java: viewer.addDragSupport(): dh.getKeywords() = <{}>", dh.getKeywords());
					// Dragging out a file means that we should supply as event.data
					// an array of filenames. As of version 1.4.6, Omnivore supports selection of a single file only,
					// so we need an array of size 1.
					// TODO: Support selection of multiple files,
					// for both dragging them out via giveAway(), and for opening via execute().
					// selection has no element .length, but we can probably use:
					// IStructuredSelection s = (IStructuredSelection) selection;
					// Before that, however, Omnivore should first allow selection of multiple lines.
					// String[] sourceFilenames = new String[s.size()];
					// or even: selsize = ((IStructuredSelection) selection).size(); etc.
					String[] sourceFilenames = new String[1];
					sourceFilenames[0] = (String) dh.giveAway();
					
					log.debug("js OmnivoreView.java: viewer.addDragSupport(): sourceFilenames[0] = <{}>", sourceFilenames[0]);
					event.data = sourceFilenames;
				}
				
			}
			
			@Override
			public void dragFinished(DragSourceEvent event){
				log.debug("js OmnivoreView.java: viewer.addDragSupport(): dragFinished");
				//if (event.detail == 1) {
				//	cdaMessage.setAssignedToOmnivore();
				//}
			}
		
		});
		//20210327js: end of Joerg's original version for drag source support
		

		// WORKAROUND to make visibleWhen contributions work
		viewer.addSelectionChangedListener(new ISelectionChangedListener() {
			@Override
			public void selectionChanged(SelectionChangedEvent arg0) {
				Display.getDefault().asyncExec(() -> {
					getViewSite().getActionBars().updateActionBars();
				});
			}
		});

		eeli_user.catchElexisEvent(ElexisEvent.createUserEvent());
		viewer.setInput(getViewSite());
		ElexisEventDispatcher.getInstance().addListeners(eeli_pat, eeli_user, eeli_dochandle);
		getSite().getPage().addPartListener(udpateOnVisible);
		getSite().setSelectionProvider(viewer);
	}
	
	private SelectionListener getSelectionAdapter(final TreeColumn column, final int index){
		SelectionAdapter selectionAdapter = new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e){
				ovComparator.setColumn(index);
				ovComparator.setFlat(bFlat);
				viewer.getTree().setSortDirection(ovComparator.getDirection());
				viewer.getTree().setSortColumn(column);
				viewer.refresh();
			}
		};
		return selectionAdapter;
	}
	
	private void applySortDirection(){
		String[] usrSortSettings = sortSettings.split(",");
		
		log.debug("OmnivoreView.java applySortDirection: CoreHub.userCfg.get(PreferencePage.SAVE_SORT_DIRECTION,false) = {}",CoreHub.userCfg.get(PreferencePage.SAVE_SORT_DIRECTION,false));

		if (CoreHub.userCfg.get(PreferencePage.SAVE_SORT_DIRECTION, false)) {
			String sortSet =
				CoreHub.userCfg.get(PreferencePage.USR_SORT_DIRECTION_SETTINGS, sortSettings);
			log.debug("OmnivoreView.java applySortDirection: sortSet = {}", sortSet);
				usrSortSettings = sortSet.split(",");
		}
		log.debug("OmnivoreView.java applySortDirection: usrSortSettings: {} {} {} {}", usrSortSettings[0], usrSortSettings[1], usrSortSettings[2], usrSortSettings[3]);
		
		int propertyIdx = Integer.parseInt(usrSortSettings[0]);
		int direction = Integer.parseInt(usrSortSettings[1]);
		int catDirection = Integer.parseInt(usrSortSettings[2]);
		bFlat = Boolean.valueOf(usrSortSettings[3]);
		
		log.debug("OmnivoreView.java applySortDirection: propertyIdx, direction, catDirection, bFlat {} {} {} {}", propertyIdx, direction, catDirection, bFlat);
		
		flatViewAction.setChecked(bFlat);
		if (!bFlat) {
			if (catDirection != -1) {
				sortViewer(1, catDirection);
				ovComparator.setCategoryDirection(catDirection);
			}
		}
		
		if (propertyIdx != 0) {
			sortViewer(propertyIdx, direction);
		}
		
	}
	
	private void sortViewer(int propertyIdx, int direction){
		TreeColumn column = viewer.getTree().getColumn(propertyIdx);
		ovComparator.setColumn(propertyIdx);
		ovComparator.setDirection(direction);
		ovComparator.setFlat(bFlat);
		viewer.getTree().setSortDirection(ovComparator.getDirection());
		viewer.getTree().setSortColumn(column);
		viewer.refresh();
	}
	
	private void applyUsersColumnWidthSetting(){
		TreeColumn[] treeColumns = table.getColumns();
		String[] userColWidth = colWidth.split(",");
		if (CoreHub.userCfg.get(PreferencePage.SAVE_COLUMN_WIDTH, false)) {
			String ucw = CoreHub.userCfg.get(PreferencePage.USR_COLUMN_WIDTH_SETTINGS, colWidth);
			userColWidth = ucw.split(",");
		}
		
		for (int i = 0; i < treeColumns.length && (i < userColWidth.length); i++) {
			treeColumns[i].setWidth(Integer.parseInt(userColWidth[i]));
		}
	}
	
	@Override
	public void dispose(){
		getSite().getPage().removePartListener(udpateOnVisible);
		ElexisEventDispatcher.getInstance().removeListeners(eeli_pat, eeli_user, eeli_dochandle);
		saveSortSettings();
		super.dispose();
	}
	
	private void hookContextMenu(){
		MenuManager menuMgr = new MenuManager("#PopupMenu"); //$NON-NLS-1$
		menuMgr.setRemoveAllWhenShown(true);
		menuMgr.addMenuListener(new IMenuListener() {
			public void menuAboutToShow(IMenuManager manager){
				OmnivoreView.this.fillContextMenu(manager);
			}
		});
		Menu menu = menuMgr.createContextMenu(viewer.getControl());
		viewer.getControl().setMenu(menu);
		getSite().registerContextMenu(menuMgr, viewer);
	}
	
	private void contributeToActionBars(){
		IActionBars bars = getViewSite().getActionBars();
		fillLocalPullDown(bars.getMenuManager());
		fillLocalToolBar(bars.getToolBarManager());
	}
	
	private void fillLocalPullDown(IMenuManager manager){
		MenuManager mnSources = new MenuManager(Messages.OmnivoreView_dataSources);
		manager.add(importAction);
		manager.add(editAction);
		manager.add(deleteAction);
		manager.add(exportAction);
		manager.add(flatViewAction);
	}
	
	private void fillContextMenu(IMenuManager manager){
		
		manager.add(editAction);
		manager.add(deleteAction);
		manager.add(exportAction);
		
		// manager.add(action2);
		// Other plug-ins can contribute there actions here
		// manager.add(new Separator(IWorkbenchActionConstants.MB_ADDITIONS));
	}
	
	private void fillLocalToolBar(IToolBarManager manager){
		manager.add(importAction);
		manager.add(exportAction);
		manager.add(flatViewAction);
	}
	
	private void makeActions(){
		importAction =
			new RestrictedAction(AccessControlDefaults.DOCUMENT_CREATE,
				Messages.OmnivoreView_importActionCaption) {
				{
					setToolTipText(Messages.OmnivoreView_importActionToolTip);
					setImageDescriptor(Images.IMG_IMPORT.getImageDescriptor());
				}
				
				public void doRun(){
					if (ElexisEventDispatcher.getSelectedPatient() == null)
						return;
					FileDialog fd = new FileDialog(getViewSite().getShell(), SWT.OPEN);
					String filename = fd.open();
					if (filename != null) {
						final DocHandle handle = DocHandle.assimilate(filename);
						if (handle != null) {
							AcquireLockBlockingUi.aquireAndRun(handle, new ILockHandler() {
								@Override
								public void lockFailed(){
									handle.delete();
								}
								
								@Override
								public void lockAcquired(){
									// do nothing
								}
							});
						}
						viewer.refresh();
					}
				}
			};
		
		deleteAction = new LockRequestingRestrictedAction<DocHandle>(AccessControlDefaults.DOCUMENT_DELETE,
				Messages.OmnivoreView_deleteActionCaption) {
			{
				setToolTipText(Messages.OmnivoreView_deleteActionToolTip);
				setImageDescriptor(Images.IMG_DELETE.getImageDescriptor());
			}

			@Override
			public DocHandle getTargetedObject() {
				ISelection selection = viewer.getSelection();
				return (DocHandle) ((IStructuredSelection) selection).getFirstElement();
			}

			@Override
				public void doRun(DocHandle dh){
					if (dh.isCategory()) {
						if (CoreHub.acl.request(AccessControlDefaults.DOCUMENT_CATDELETE)) {
							ListDialog ld = new ListDialog(getViewSite().getShell());
							
							Query<DocHandle> qbe = new Query<DocHandle>(DocHandle.class);
							qbe.add(DocHandle.FLD_MIMETYPE, Query.EQUALS, CATEGORY_MIMETYPE);
							qbe.add(PersistentObject.FLD_ID, Query.NOT_EQUAL, dh.getId());
							List<DocHandle> mainCategories = qbe.execute();

							ld.setInput(mainCategories);
							ld.setContentProvider(ArrayContentProvider.getInstance());
							ld.setLabelProvider(new DefaultLabelProvider());
							ld.setTitle(
								MessageFormat.format("Kategorie {0} lÃ¶schen", dh.getLabel()));
							ld.setMessage(
								"Geben Sie bitte an, in welche andere Kategorie die Dokumente dieser Kategorie verschoben werden sollen");
							int open = ld.open();
							if (open == Dialog.OK) {
								Object[] selection = ld.getResult();
								if (selection != null && selection.length > 0) {
									String label = ((DocHandle) selection[0]).getLabel();
									DocHandle.removeCategory(dh.getLabel(), label);
								}
								viewer.refresh();
							}
						} else {
							SWTHelper.showError("Insufficient Rights",
								"You have insufficient rights to delete document categories");
						}
					} else {
						if (SWTHelper.askYesNo(Messages.OmnivoreView_reallyDeleteCaption,
							MessageFormat.format(Messages.OmnivoreView_reallyDeleteContents,
								dh.getTitle()))) {
							dh.delete();
							viewer.refresh();
						}
					}
				};
		};
		
		editAction = new LockRequestingRestrictedAction<DocHandle>(AccessControlDefaults.DOCUMENT_DELETE,
				Messages.OmnivoreView_editActionCaption) {
			{
				setToolTipText(Messages.OmnivoreView_editActionTooltip);
				setImageDescriptor(Images.IMG_EDIT.getImageDescriptor());
			}

			@Override
			public DocHandle getTargetedObject() {
				ISelection selection = viewer.getSelection();
				return (DocHandle) ((IStructuredSelection) selection).getFirstElement();
			}

			@Override
			public void doRun(DocHandle dh) {
				if (dh.isCategory()) {
					if (CoreHub.acl.request(AccessControlDefaults.DOCUMENT_CATDELETE)) {

						InputDialog id = new InputDialog(getViewSite().getShell(),
								MessageFormat.format("Kategorie {0} umbenennen.", dh.getLabel()),
								"Geben Sie bitte einen neuen Namen fÃ¼r die Kategorie ein", dh.getLabel(), null);
						if (id.open() == Dialog.OK) {
							String nn = id.getValue();
							DocHandle.renameCategory(dh.getTitle(), nn);
							viewer.refresh();
						}
					} else {
						SWTHelper.showError("Insufficient Rights",
								"You have insufficient rights to delete document categories");

					}
				} else {
					FileImportDialog fid = new FileImportDialog(dh);
					if (fid.open() == Dialog.OK) {
						viewer.refresh(true);
					}
				}
			}
		};
		
		doubleClickAction = new Action() {
			public void run(){
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				DocHandle dh = (DocHandle) obj;
				if (dh.isCategory()) {
					if (viewer.getExpandedState(dh)) {
						viewer.collapseToLevel(dh, TreeViewer.ALL_LEVELS);
					} else {
						viewer.expandToLevel(dh, TreeViewer.ALL_LEVELS);
					}
				} else {
					dh.execute();
				}
				
			}
		};
		
		exportAction = new Action(Messages.OmnivoreView_exportActionCaption) {
			{
				setImageDescriptor(Images.IMG_EXPORT.getImageDescriptor());
				setToolTipText(Messages.OmnivoreView_exportActionTooltip);
			}
			
			public void run(){
				ISelection selection = viewer.getSelection();
				Object obj = ((IStructuredSelection) selection).getFirstElement();
				if (obj == null)
					return;
				DocHandle dh = (DocHandle) obj;
				String mime = dh.get(DocHandle.FLD_MIMETYPE);
				FileDialog fd = new FileDialog(getSite().getShell(), SWT.SAVE);
				fd.setFileName(mime);
				String fname = fd.open();
				if (fname != null) {
					if (!dh.storeExternal(fname)) {
						SWTHelper.showError(Messages.OmnivoreView_configErrorCaption,
							Messages.OmnivoreView_configErrorText);
					}
				}
			}
		};
		
		flatViewAction = new Action(Messages.OmnivoreView_flatActionCaption, Action.AS_CHECK_BOX) {
			{
				setImageDescriptor(Images.IMG_FILTER.getImageDescriptor());
				setToolTipText(Messages.OmnivoreView_flatActionTooltip);
			}
			
			public void run(){
				bFlat = isChecked();
				viewer.refresh();
			}
		};
	};
	
	private void hookDoubleClickAction(){
		viewer.addDoubleClickListener(new IDoubleClickListener() {
			public void doubleClick(DoubleClickEvent event){
				doubleClickAction.run();
			}
		});
	}
	
	/**
	 * Passing the focus request to the viewer's control.
	 */
	public void setFocus(){
		viewer.getControl().setFocus();
		refresh();
	}
	
	private void saveColumnWidthSettings() {
		TreeColumn[] treeColumns = viewer.getTree().getColumns();
		StringBuilder sb = new StringBuilder();
		for (TreeColumn tc : treeColumns) {
			sb.append(tc.getWidth());
			sb.append(",");
		}
		CoreHub.userCfg.set(PreferencePage.USR_COLUMN_WIDTH_SETTINGS, sb.toString());
	}
	
	private void saveSortSettings(){
		int propertyIdx = ovComparator.getPropertyIndex();
		int direction = ovComparator.getDirectionDigit();
		int catDirection = ovComparator.getCategoryDirection();
		log.debug("OmnivoreView.java saveSortSettings(): propertyIdx, direction, catDirection, bFlat {} {} {} {}", propertyIdx, direction, catDirection, bFlat);
		CoreHub.userCfg.set(PreferencePage.USR_SORT_DIRECTION_SETTINGS, propertyIdx + "," + direction
			+ "," + catDirection + "," + bFlat);
	}
	
	public void refresh(){
		eeli_pat.catchElexisEvent(ElexisEvent.createPatientEvent());
	}
	
	/**
	 * Returns an image descriptor for the image file at the given plug-in relative path.
	 * 
	 * @param path
	 *            the path
	 * @return the image descriptor
	 */
	public ImageDescriptor getImageDescriptor(String path){
		return AbstractUIPlugin.imageDescriptorFromPlugin("ch.elexis.omnivoredirect", path); //$NON-NLS-1$
	}
}