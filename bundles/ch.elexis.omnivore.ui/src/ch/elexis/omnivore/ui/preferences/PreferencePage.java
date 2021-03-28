/*******************************************************************************
 * Copyright (c) 2013-2021 J. Sigle; Copyright (c) 2006-2010, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    J. Sigle - added a preference page to omnivore to control  a new automatic archiving functionality  
 *    G. Weirich and others - preference pages for other plugins, used as models for this one
 *    N. Giger - separated parts of PreferencePage.java into Preferences.java and PreferenceConstants.java
 *    J. Sigle - review re-including what was missed when adopting omnivore_js back into omnivore
 *    
 *******************************************************************************/

//TODO: js: I wonder why Constants, Variables and other functionality for one module have been
//split into three separate files - which are additionally located in different subtrees.
//This way, it is very difficult to do diffs, or to find out what has been included from
//the final version and what has been lost. It's also difficult to (re-)understand what's
//happening, because some comments went into the other files, some where removed.
//Moreover, an import... line needs exactly the same space as a definition of a constant -
//and not even all thematically related constants have made it into a single constants-file...

package ch.elexis.omnivore.ui.preferences;

import static ch.elexis.omnivore.PreferenceConstants.BASEPATH;
import static ch.elexis.omnivore.PreferenceConstants.DATE_MODIFIABLE;
import static ch.elexis.omnivore.PreferenceConstants.OmnivoreMax_Filename_Length_Default;
import static ch.elexis.omnivore.PreferenceConstants.OmnivoreMax_Filename_Length_Max;
import static ch.elexis.omnivore.PreferenceConstants.OmnivoreMax_Filename_Length_Min;
import static ch.elexis.omnivore.PreferenceConstants.PREFBASE;
import static ch.elexis.omnivore.PreferenceConstants.PREFERENCE_DEST_DIR;
import static ch.elexis.omnivore.PreferenceConstants.PREFERENCE_SRC_PATTERN;
import static ch.elexis.omnivore.PreferenceConstants.PREF_DEST_DIR;
import static ch.elexis.omnivore.PreferenceConstants.PREF_MAX_FILENAME_LENGTH;
import static ch.elexis.omnivore.PreferenceConstants.PREF_SRC_PATTERN;
import static ch.elexis.omnivore.PreferenceConstants.STOREFS;
import static ch.elexis.omnivore.PreferenceConstants.STOREFSGLOBAL;

import org.eclipse.jface.preference.BooleanFieldEditor;
import org.eclipse.jface.preference.DirectoryFieldEditor;
import org.eclipse.jface.preference.FieldEditorPreferencePage;
import org.eclipse.jface.preference.IPreferenceStore;
import org.eclipse.jface.preference.IntegerFieldEditor;
import org.eclipse.jface.preference.StringFieldEditor;
import org.eclipse.swt.SWT;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.FillLayout;
import org.eclipse.swt.layout.GridData;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.layout.RowData;
import org.eclipse.swt.layout.RowLayout;
import org.eclipse.swt.widgets.Button;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Group;
import org.eclipse.swt.widgets.Label;
import org.eclipse.swt.widgets.Text;
import org.eclipse.ui.IWorkbench;
import org.eclipse.ui.IWorkbenchPreferencePage;
import org.eclipse.ui.dialogs.SelectionDialog;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.ui.preferences.SettingsPreferenceStore;
import ch.elexis.core.ui.views.codesystems.CodeSelectorFactory;
import ch.elexis.data.Leistungsblock;
import ch.elexis.omnivore.PreferenceConstants;
import ch.elexis.omnivore.data.Preferences;
import ch.elexis.omnivore.ui.jobs.OutsourceUiJob;

//FIXME: Layout needs a thorough redesign. See: http://www.eclipse.org/articles/article.php?file=Article-Understanding-Layouts/index.html -- 20130411js: done to some extent.
//FIXME: We want a layout that will use all the available space, auto re-size input fields etc., have nested elements, and still NOT result in "dialog has invalid data" error messages.
//FIXME: Maybe we must add PREFERENCE_BRANCH to some editor element add etc. commands, to ensure the parameters are store.

public class PreferencePage extends FieldEditorPreferencePage implements IWorkbenchPreferencePage {
	public static Logger log = LoggerFactory.getLogger("ch.elexis.omnivore.PreferencePage"); //$NON-NLS-1$
	
	public static final String USR_COLUMN_WIDTH_SETTINGS = PREFBASE + "/columnwidths";
	public static final String SAVE_COLUMN_WIDTH = PREFBASE + "/savecolwidths";
	
	public static final String USR_SORT_DIRECTION_SETTINGS = PREFBASE + "/sortdirection";
	public static final String SAVE_SORT_DIRECTION = PREFBASE + "/savesortdirection";
		
	private BooleanFieldEditor bStoreFSGlobal;
	private BooleanFieldEditor bStoreFS;
	private BooleanFieldEditor bAutomaticBilling;
	private DirectoryFieldEditor dfStorePath;
	
	private Button btnSaveColumnWidths;
	private Button btnSaveSortDirection;
	private Button outsource;
	
	boolean storeFs = Preferences.storeInFilesystem();
	boolean basePathSet = false;
	
	private Text tAutomaticBillingBlock;
	
	public PreferencePage(){
		super(GRID);
		
		setPreferenceStore(new SettingsPreferenceStore(CoreHub.localCfg));
		setDescription(ch.elexis.omnivore.data.Messages.Preferences_omnivore);
		
		String basePath = Preferences.getBasepath();
		if (basePath != null) {
			if (basePath.length() > 0) {
				basePathSet = true;
			}
		}
	}
	
	@Override
	protected void createFieldEditors(){
		// I'd like to place ALL groups in this preference dialog one under another,
		// so that each group completely occupies the available horizontal space.
		// But the default behaviour is to put the groups next to each other :-(
		
		// For instructions, see:
		// http://www.eclipse.org/articles/article.php?file=Article-Understanding-Layouts/index.html
		
		// I can't use any other layout but GridLayout.
		// Otherwise some adjustGridLayout() somewhere else will invariably throw:
		// "The currently displayed page contains invalid values." at runtime. 201304110439js
		// Besides, RowLayout() wouldn't make sense here.
		
		// ---
		
		// Nachdem ich aussenrum einmal eine globale Gruppe installiere,
		// bekomme ich wenigstens die nachfolgend tieferen Gruppen untereinander in einer Spalte,
		// und nicht mehr nebeneinander.
		// Offenbar hat die Zuweisung eines Layouts zu getFieldEditorParent() keinen Effekt gehabt.
		// Warum auch immer...
		
		Group gAllOmnivorePrefs = new Group(getFieldEditorParent(), SWT.NONE);
		
		//TODO: 20210327js: There are major differences / missing lines from the
		//latest version of Elexis 2.1.7js / omnivore_js to the version left over here.
		//It is yet unclear whether this happened intentionally - as part of an
		//integration of configurable settings related to my omnivore_js extensions
		//into what had become of the mainstream omnivore by then - or whether things
		//were unintentionally missed. PLEASE REVIEW ASAP.
		//Sources:
		//jsigle@blackbox  Sa M�r 27  15:39:44  /mnt/sdb3/Elexis-workspace/elexis-2.1.7-20130523/elexis-bootstrap-js-201712191036-last-20130605based-with-MSWord_js-as-used-by-JH-since-201701-before-gitpush  
		//$ kate ./elexis-base/ch.elexis.omnivore/src/ch/elexis/omnivore/preferences/PreferencePage.java
		//jsigle@blackbox  Sa M�r 27  15:28:44  /mnt/sdb3/Elexis-workspace/elexis-2.1.7-20130523/elexis-bootstrap-js-201712191036-last-20130605based-with-MSWord_js-as-used-by-JH-since-201701-before-gitpush  
		//$ kompare ./elexis-base/ch.elexis.omnivore/src/ch/elexis/omnivore/preferences/PreferencePage.java /mnt/think3/c/Users/jsigle/git/elexis/3.7/git/./elexis-3-base/bundles/ch.elexis.omnivore.ui/src/ch/elexis/omnivore/ui/preferences/PreferencePage.java
		//and also: Preferences.java, PreferenceContsants.java, Utils.java
		
		// getFieldEditorParent().setLayoutData(SWTHelper.getFillGridData(1,false,0,false));
		
		GridLayout gOmnivorePrefsGridLayout = new GridLayout();
		gOmnivorePrefsGridLayout.numColumns = 1; // this is sadly and apparently ignored.
		gOmnivorePrefsGridLayout.makeColumnsEqualWidth = true;
		
		gAllOmnivorePrefs.setLayout(gOmnivorePrefsGridLayout);
		// getFieldEditorParent().setLayout(gOmnivorePrefsGridLayout);
		
		GridData gOmnivorePrefsGridLayoutData = new GridData();
		gOmnivorePrefsGridLayoutData.grabExcessHorizontalSpace = true;
		gOmnivorePrefsGridLayoutData.horizontalAlignment = GridData.FILL;
		
		gAllOmnivorePrefs.setLayoutData(gOmnivorePrefsGridLayoutData);
		Group gGeneralOptions = new Group(gAllOmnivorePrefs, SWT.NONE);
		GridData gGeneralOptionsGridLayoutData = new GridData();
		gGeneralOptionsGridLayoutData.grabExcessHorizontalSpace = true;
		gGeneralOptionsGridLayoutData.horizontalAlignment = GridData.FILL;
		gGeneralOptions.setLayoutData(gGeneralOptionsGridLayoutData);
		
		addField(new BooleanFieldEditor(DATE_MODIFIABLE, ch.elexis.omnivore.data.Messages.Preferences_dateModifiable,
			gGeneralOptions));
		
		Group gPathForDocs = new Group(gGeneralOptions, SWT.NONE);
		gPathForDocs.setLayout(new FillLayout());
		
		bStoreFSGlobal = new BooleanFieldEditor(STOREFSGLOBAL,
			"Dateisystem Einstellungen global speichern", gPathForDocs) {
			@Override
			protected void fireValueChanged(String property, Object oldValue, Object newValue){
				super.fireValueChanged(property, oldValue, newValue);
				if ((Boolean) newValue) {
					Preferences.setFsSettingStore(new SettingsPreferenceStore(CoreHub.globalCfg));
					updateFSSettingsStore();
				} else {
					Preferences.setFsSettingStore(new SettingsPreferenceStore(CoreHub.localCfg));
					updateFSSettingsStore();
				}
			}
		};
		addField(bStoreFSGlobal);
		
		bStoreFS = new BooleanFieldEditor(STOREFS, ch.elexis.omnivore.data.Messages.Preferences_storeInFS, gPathForDocs);
		addField(bStoreFS);
		Preferences.storeInFilesystem();
		
		dfStorePath =
			new DirectoryFieldEditor(BASEPATH, ch.elexis.omnivore.data.Messages.Preferences_pathForDocs, gPathForDocs);
		Preferences.getBasepath();
		dfStorePath.setEmptyStringAllowed(true);
		addField(dfStorePath);
		
		Label label = new Label(gAllOmnivorePrefs, SWT.NONE);
		label.setText("Datenbankeinträge auf Filesystem auslagern");
		outsource = new Button(gAllOmnivorePrefs, SWT.PUSH);
		outsource.setText("Auslagern");
		outsource.setEnabled(false);
		outsource.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e){
				OutsourceUiJob job = new OutsourceUiJob();
				job.execute(getShell());
			}
		});
		
		Group gPathForMaxChars = new Group(gGeneralOptions, SWT.NONE);
		gPathForMaxChars.setLayout(new FillLayout());
		IPreferenceStore preferenceStore = new SettingsPreferenceStore(CoreHub.localCfg);
		preferenceStore.setDefault(PREF_MAX_FILENAME_LENGTH, OmnivoreMax_Filename_Length_Default);
		IntegerFieldEditor maxCharsEditor =
			new IntegerFieldEditor(PREF_MAX_FILENAME_LENGTH,
				ch.elexis.omnivore.data.Messages.Preferences_MAX_FILENAME_LENGTH, gPathForMaxChars);
		maxCharsEditor.setValidRange(OmnivoreMax_Filename_Length_Min,
			OmnivoreMax_Filename_Length_Max);
		addField(maxCharsEditor);
		
		// ---
		
		// 20130325js: For automatic archiving of incoming files:
		// add field groups for display or editing of rule sets.
		// First, we define a new group (that will visually appear as an outlined box)
		// and give it a header like setText("Regel i");
		// Then, within this group, we add one StringFieldEditor for the search pattern to
		// be matched, and a DirectoryFieldEditor for the auto archiving target to be used.
		
		Integer nAutoArchiveRules = Preferences.getOmnivorenRulesForAutoArchiving();
		
		Group gAutoArchiveRules = new Group(gAllOmnivorePrefs, SWT.NONE);
		// Group gAutoArchiveRules = new Group(getFieldEditorParent(), SWT.NONE);
		
		// gAutoArchiveRules.setLayoutData(SWTHelper.getFillGridData(1,true,nAutoArchiveRules,false));
		
		GridLayout gAutoArchiveRulesGridLayout = new GridLayout();
		gAutoArchiveRulesGridLayout.numColumns = 1; // bestimmt die Anzahl der Spalten,
		// in denen die Regeln innerhab des AutoArchiveRules containers angeordnet werden.
		gAutoArchiveRules.setLayout(gAutoArchiveRulesGridLayout);
		
		GridData gAutoArchiveRulesGridLayoutData = new GridData();
		gAutoArchiveRulesGridLayoutData.grabExcessHorizontalSpace = true;
		gAutoArchiveRulesGridLayoutData.horizontalAlignment = GridData.FILL;
		gAutoArchiveRules.setLayoutData(gAutoArchiveRulesGridLayoutData);
		
		gAutoArchiveRules.setText(ch.elexis.omnivore.data.Messages.Preferences_automatic_archiving_of_processed_files);
		
		for (int i = 0; i < nAutoArchiveRules; i++) {
			
			// Just to check whether the loop is actually used,
			// even if nothing appears in the preference dialog:
			log.debug(PREF_SRC_PATTERN[i] + " : " + ch.elexis.omnivore.data.Messages.Preferences_SRC_PATTERN);
			log.debug(PREF_DEST_DIR[i] + " : " + ch.elexis.omnivore.data.Messages.Preferences_DEST_DIR);
			
			//Simplified version: All auto Archive rules are directly located in the AutoArchiveRules group.
			//addField(new StringFieldEditor(PREF_SRC_PATTERN[i], Messages.Omnivore_jsPREF_SRC_PATTERN, gAutoArchiveRules));
			//addField(new DirectoryFieldEditor(PREF_DEST_DIR[i], Messages.Omnivore_jsPREF_DEST_DIR, gAutoArchiveRules));

			//Correct version: Each AutoArchiveRule-Set is located in a group for that Rule.
			//This only works when I use the GridLayout/GridData approach, but not when I use the SWTHelper.getFillGridData() approach.
			
			Group gAutoArchiveRule = new Group(gAutoArchiveRules, SWT.NONE);

			////gAutoArchiveRule.setLayoutData(SWTHelper.getFillGridData(2,false,1,false));	//This shorthand makes groups-within-a-group completely disappear.
			
			GridLayout gAutoArchiveRuleGridLayout = new GridLayout();
			gAutoArchiveRuleGridLayout.numColumns = 1; // bestimmt die Anzahl der Spalten für
			// jede Regel: links label, rechts eingabefeld (ggf. mit Knopf), but: 1, 2, 3: no change.
			gAutoArchiveRule.setLayout(gAutoArchiveRuleGridLayout);
			
			GridData gAutoArchiveRuleGridLayoutData = new GridData();
			gAutoArchiveRuleGridLayoutData.grabExcessHorizontalSpace = true; // damit die Gruppe
			// der Rules so breit ist, wie oben Label und Max_Filename_Length Eingabefeld zusammen.
			gAutoArchiveRuleGridLayoutData.horizontalAlignment = GridData.FILL;
			gAutoArchiveRule.setLayoutData(gAutoArchiveRuleGridLayoutData);
			
			// Cave: The labels show 1-based rule numbers,
			// although the actual array indizes are 0 based.
			gAutoArchiveRule.setText(ch.elexis.omnivore.data.Messages.Preferences_Rule + " " + (i + 1));
			// The brackets are needed, or the string representations of i and 1 will both be added...
			
			log.debug("i {} val {}", i, PREF_SRC_PATTERN[i]);
			addField(new StringFieldEditor(PREF_SRC_PATTERN[i],
					ch.elexis.omnivore.data.Messages.Preferences_SRC_PATTERN, gAutoArchiveRule));
			addField(new DirectoryFieldEditor(PREF_DEST_DIR[i],
					ch.elexis.omnivore.data.Messages.Preferences_DEST_DIR, gAutoArchiveRule));
		}
		// ---
		
		// 20130411js: Make the temporary filename configurable
		// which is generated to extract the document from the database for viewing.
		// Thereby, simplify tasks like adding a document to an e-mail.
		// For most elements noted below, we can set the maximum number of digits
		// to be used (taken from the source from left); which character to add thereafter;
		// and whether to fill leading digits by a given character.
		// This makes a large number of options, so I construct the required preference store keys
		// from arrays.
		
		// Originally, I would have preferred a simple tabular matrix:
		// columns: element name, fill_lead_char, num_digits, add_trail_char
		// lines: each of the configurable elements of the prospective temporary filename.
		// But such a simple thing is apparently not so simple to make using the PreferencePage
		// class.
		// So instead, I add a new group for each configurable element,
		// including each of the 3 parameters.
		
		Integer nCotfRules = Preferences.PREFERENCE_cotf_elements.length;
		
		Group gCotfRules = new Group(gAllOmnivorePrefs, SWT.NONE);
		// Group gCotfRules = new Group(getFieldEditorParent(), SWT.NONE);
		
		// gCotfRules.setLayoutData(SWTHelper.getFillGridData(6,false,nCotfRules,false)); //This
		// would probably make groups-within-group completely disappear.
		
		GridLayout gCotfRulesGridLayout = new GridLayout();
		gCotfRulesGridLayout.numColumns = nCotfRules; // at least this one is finally honoured...
		gCotfRules.setLayout(gCotfRulesGridLayout);
		
		GridData gCotfRulesGridLayoutData = new GridData();
		gCotfRulesGridLayoutData.grabExcessHorizontalSpace = true;
		gCotfRulesGridLayoutData.horizontalAlignment = GridData.FILL;
		gCotfRules.setLayoutData(gCotfRulesGridLayoutData);
		
		gCotfRules.setText(ch.elexis.omnivore.data.Messages.Preferences_construction_of_temporary_filename);
		
		for (int i = 0; i < nCotfRules; i++) {
			
			Group gCotfRule = new Group(gCotfRules, SWT.NONE);
			
			//gCotfRule.setLayoutData(SWTHelper.getFillGridData(2,false,2,false));	//This would probably make groups-within-group completely disappear.
				
			gCotfRule.setLayout(new FillLayout());
			GridLayout gCotfRuleGridLayout = new GridLayout();
			gCotfRuleGridLayout.numColumns = 6;
			gCotfRule.setLayout(gCotfRuleGridLayout);
			
			GridData gCotfRuleGridLayoutData = new GridData();
			gCotfRuleGridLayoutData.grabExcessHorizontalSpace = true;
			gCotfRuleGridLayoutData.horizontalAlignment = GridData.FILL;
			gCotfRule.setLayoutData(gCotfRuleGridLayoutData);
			
			//System.out.println("Messages.Omnivore_jsPREF_cotf_"+PREFERENCE_cotf_elements[i]);
			
			//gCotfRule.setText(PREFERENCE_cotf_elements[i]);	//The brackets are needed, or the string representations of i and 1 will both be added...
			
			//addField(new StringFieldEditor(PREFERENCE_BRANCH+PREFERENCE_COTF+PREFERENCE_cotf_elements[i]+"_"+PREFERENCE_cotf_parameters[0],PREFERENCE_cotf_parameters[0],gCotfRule));
			//addField(new StringFieldEditor(PREFERENCE_BRANCH+PREFERENCE_COTF+PREFERENCE_cotf_elements[i]+"_"+PREFERENCE_cotf_parameters[1],PREFERENCE_cotf_parameters[1],gCotfRule));
			//addField(new StringFieldEditor(PREFERENCE_BRANCH+PREFERENCE_COTF+PREFERENCE_cotf_elements[i]+"_"+PREFERENCE_cotf_parameters[2],PREFERENCE_cotf_parameters[2],gCotfRule));
			
			//Das hier geht leider nicht so einfach:
			//gCotfRule.setText(getObject("Messages.Omnivore_jsPREF_cotf_"+PREFERENCE_cotf_elements[i]));
			gCotfRule.setText(Preferences.PREFERENCE_cotf_elements_messages[i]);
			
			//addField(new StringFieldEditor(PREFERENCE_BRANCH+PREFERENCE_COTF+PREFERENCE_cotf_elements[i]+"_"+PREFERENCE_cotf_parameters[0],Messages.Omnivore_jsPREF_cotf_fill_leading_char,gCotfRule));
			//addField(new StringFieldEditor(PREFERENCE_BRANCH+PREFERENCE_COTF+PREFERENCE_cotf_elements[i]+"_"+PREFERENCE_cotf_parameters[1],Messages.Omnivore_jsPREF_cotf_num_digits,gCotfRule));
			//addField(new StringFieldEditor(PREFERENCE_BRANCH+PREFERENCE_COTF+PREFERENCE_cotf_elements[i]+"_"+PREFERENCE_cotf_parameters[2],Messages.Omnivore_jsPREF_cotf_add_trail_char,gCotfRule));
			
			//TODO: 20210327js: Once again, significant changes from the original 2.1.7js based code follow.
			//Moreover, readability and comparability of code is completely crippled by the ca. 80-char line length limit.
			//Sorry, need to review this some other time.
			
			String prefName = PREFBASE + Preferences.PREFERENCE_COTF + Preferences.PREFERENCE_cotf_elements[i] + "_" + Preferences.PREFERENCE_cotf_parameters[1];
			
			log.debug("Add  {} val {}", i, prefName);
			
			if (Preferences.PREFERENCE_cotf_elements[i].contains("constant")) {
				gCotfRuleGridLayoutData.horizontalAlignment = GridData.BEGINNING;
				gCotfRuleGridLayoutData.verticalAlignment = GridData.BEGINNING;
				addField(new StringFieldEditor(prefName, "", 10, gCotfRule));
			} else {
				String str0 = PREFBASE + Preferences.PREFERENCE_COTF + Preferences.PREFERENCE_cotf_elements[i] + "_" + Preferences.PREFERENCE_cotf_parameters[0];
				String str2 = PREFBASE + Preferences.PREFERENCE_COTF + Preferences.PREFERENCE_cotf_elements[i] + "_" + Preferences.PREFERENCE_cotf_parameters[2];
				log.debug("{}: keyl {} {} {} {}", i, str0, prefName, str2);
				log.debug("val {} {} {} {}", Preferences.PREFERENCE_cotf_parameters_messages[0],
					Preferences.PREFERENCE_cotf_parameters_messages[1],
					Preferences.PREFERENCE_cotf_parameters_messages[2]);
				addField(new StringFieldEditor(str0, Preferences.PREFERENCE_cotf_parameters_messages[0], 10, gCotfRule));
				addField(new StringFieldEditor(prefName, Preferences.PREFERENCE_cotf_parameters_messages[1], 10, gCotfRule));
				addField(new StringFieldEditor(str2, Preferences.PREFERENCE_cotf_parameters_messages[2], 10, gCotfRule));
			}
		}
		/*
		public static final Integer nOmnivore_jsPREF_cotf_element_digits_min=0;
			public static final Integer nOmnivore_jsPREF_cotf_element_digits_max=255;
			public static final String PREFERENCE_cotf_elements={"PID", "given_name", "family_name", "date_of_birth", "document_title", "constant", "dguid", "random"};
			public static final String PREFERENCE_cotf_parameters={"fill_lead_char", "num_digits", "add_trail_char"};
			 */
		
		//Doesn't help here.
		//adjustGridLayout();
	
		//20210327js: The remainder of this method is unrelated to omnivore_js:
		
		enableOutsourceButton();
		
		bAutomaticBilling = new BooleanFieldEditor(PreferenceConstants.AUTO_BILLING,
			"Automatische Verrechnung (bei Drag and Drop)", gAllOmnivorePrefs);
		addField(bAutomaticBilling);
		
		Composite billingBlockComposite = new Composite(gAllOmnivorePrefs, SWT.NONE);
		billingBlockComposite.setLayout(new RowLayout());
		tAutomaticBillingBlock = new Text(billingBlockComposite, SWT.BORDER | SWT.READ_ONLY);
		tAutomaticBillingBlock.setLayoutData(new RowData(250, SWT.DEFAULT));
		tAutomaticBillingBlock.setTextLimit(80);
		Button blockCodeSelection = new Button(billingBlockComposite, SWT.PUSH);
		blockCodeSelection.setText("..."); //$NON-NLS-1$
		blockCodeSelection.addSelectionListener(new SelectionAdapter() {
			@Override
			public void widgetSelected(SelectionEvent e){
				SelectionDialog dialog = CodeSelectorFactory.getSelectionDialog("Block", getShell(), //$NON-NLS-1$
					"ignoreErrors");
				if (dialog.open() == SelectionDialog.OK) {
					if (dialog.getResult() != null && dialog.getResult().length > 0) {
						Leistungsblock block = (Leistungsblock) dialog.getResult()[0];
						selectBlock(block);
						CoreHub.localCfg.set(PreferenceConstants.AUTO_BILLING_BLOCK, block.getId());
					} else {
						CoreHub.localCfg.set(PreferenceConstants.AUTO_BILLING_BLOCK, "");
						selectBlock(null);
					}
				}
			}
		});
		if(!CoreHub.localCfg.get(PreferenceConstants.AUTO_BILLING_BLOCK, "").isEmpty()) {
			selectBlock(Leistungsblock
				.load(CoreHub.localCfg.get(PreferenceConstants.AUTO_BILLING_BLOCK, "")));
		}
	}
	
	private void selectBlock(Leistungsblock block){
		if (block != null && block.exists()) {
			tAutomaticBillingBlock.setText(block.getLabel());
		} else {
			tAutomaticBillingBlock.setText("");
		}
	}
	
	private void enableOutsourceButton(){
		if (storeFs && basePathSet)
			outsource.setEnabled(true);
		else
			outsource.setEnabled(false);
	}
	
	private void updateFSSettingsStore(){
		// update fs settings store accoring to global cfg
		bStoreFS.setPreferenceStore(Preferences.getFsSettingsStore());
		bStoreFS.load();
		dfStorePath.setPreferenceStore(Preferences.getFsSettingsStore());
		dfStorePath.load();
	}
	
	@Override
	protected Control createContents(Composite parent){
		Control c = super.createContents(parent);
		// save global setting to global cfg
		bStoreFSGlobal.setPreferenceStore(new SettingsPreferenceStore(CoreHub.globalCfg));
		bStoreFSGlobal.load();
		// must be called after createFieldEditors / super.createContents
		updateFSSettingsStore();
		
		addSeparator();
		
		btnSaveColumnWidths = new Button(getFieldEditorParent(), SWT.CHECK);
		btnSaveColumnWidths.setText("Spaltenbreite speichern (für Ihren Benutzer)");
		btnSaveColumnWidths.setSelection(CoreHub.userCfg.get(PreferencePage.SAVE_COLUMN_WIDTH, false));
		
		btnSaveSortDirection = new Button(getFieldEditorParent(), SWT.CHECK);
		btnSaveSortDirection.setText("Sortierung speichern (für Ihren Benutzer)");
		btnSaveSortDirection.setSelection(CoreHub.userCfg.get(PreferencePage.SAVE_SORT_DIRECTION,
			false));
		
		return c;
	}
	
	private void addSeparator(){
		Label separator = new Label(getFieldEditorParent(), SWT.HORIZONTAL | SWT.SEPARATOR);
		GridData separatorGridData = new GridData();
		separatorGridData.horizontalSpan = 3;
		separatorGridData.grabExcessHorizontalSpace = true;
		separatorGridData.horizontalAlignment = GridData.FILL;
		separatorGridData.verticalIndent = 0;
		separator.setLayoutData(separatorGridData);
	}
	
	@Override
	public void init(IWorkbench workbench){
		// 20130325js: For automatic archiving of incoming files:
		// construct the keys to the elexis preference store from a fixed header plus rule number:
		for (Integer i = 0; i < Preferences.getOmnivorenRulesForAutoArchiving(); i++) {
			PREF_SRC_PATTERN[i] = PREFERENCE_SRC_PATTERN + i.toString().trim(); //If this source pattern is found in the filename...
			PREF_DEST_DIR[i] = PREFERENCE_DEST_DIR + i.toString().trim(); //the incoming file will be archived here after having been read
		}
		
	}
	
	@Override
	protected void performApply(){
		CoreHub.userCfg.set(PreferencePage.SAVE_COLUMN_WIDTH, btnSaveColumnWidths.getSelection());
		CoreHub.userCfg.set(PreferencePage.SAVE_SORT_DIRECTION, btnSaveSortDirection.getSelection());
		CoreHub.userCfg.flush();
		CoreHub.globalCfg.flush();
		CoreHub.localCfg.flush();
		super.performApply();
	}
	
}
