/*******************************************************************************
 * Copyright (c) 2006-2016, G. Weirich and Elexis
 * Portions (c) 2012-2021, Joerg M. Sigle (js, jsigle)
 *
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich  - initial implementation
 *    J. Sigle - Added configurable rule-based automatic archiving of imported files into multiple target directories    
 *    J. Sigle - Added trim() to meta information fields after import of files
 *    J. Sigle - Added more reasonable workflow and informative warning if configurable max filename length exceeded upon import
 *    N. Giger - Reworked for adoption from 2.1.7js to 3.5
 *    J. Sigle - Restored missed and lost functionality from 2.1.7js / omnivore_js to 3.7
 *    <office@medevit.at> - Share a common base
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


package ch.elexis.omnivore.data;

import static ch.elexis.omnivore.Constants.CATEGORY_MIMETYPE;
import static ch.elexis.omnivore.Constants.DEFAULT_CATEGORY;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.math.BigInteger;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.security.SecureRandom;
import java.text.MessageFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;

import org.apache.commons.lang3.StringUtils;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.jface.dialogs.MessageDialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.graphics.ImageData;
import org.eclipse.swt.graphics.ImageLoader;
import org.eclipse.swt.program.Program;
import org.eclipse.swt.widgets.Shell;
import org.eclipse.ui.PlatformUI;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.lowagie.text.Document;
import com.lowagie.text.Image;
import com.lowagie.text.PageSize;
import com.lowagie.text.pdf.PdfWriter;

import ch.elexis.core.constants.StringConstants;
import ch.elexis.core.data.events.ElexisEventDispatcher;
import ch.elexis.core.data.interfaces.text.IOpaqueDocument;
import ch.elexis.core.exceptions.ElexisException;
import ch.elexis.core.exceptions.PersistenceException;
import ch.elexis.core.ui.util.Log;
import ch.elexis.core.ui.util.SWTHelper;
import ch.elexis.data.Patient;
import ch.elexis.data.PersistentObject;
import ch.elexis.data.Query;
import ch.elexis.omnivore.dialog.FileImportDialog;
import ch.rgw.io.FileTool;
import ch.rgw.tools.ExHandler;
import ch.rgw.tools.JdbcLink;
import ch.rgw.tools.MimeTool;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;
import ch.rgw.tools.VersionInfo;

public class DocHandle extends PersistentObject implements IOpaqueDocument {
	
	private static Logger log = LoggerFactory.getLogger(DocHandle.class);
	
	private TimeTool toStringTool = new TimeTool();
	
	public static final String FLD_CAT = "Cat"; //$NON-NLS-1$
	public static final String FLD_TITLE = "Titel"; //$NON-NLS-1$
	public static final String FLD_MIMETYPE = "Mimetype"; //$NON-NLS-1$
	public static final String FLD_DOC = "Doc"; //$NON-NLS-1$
	public static final String FLD_PATH = "Path"; //$NON-NLS-1$
	public static final String FLD_KEYWORDS = "Keywords"; //$NON-NLS-1$
	public static final String FLD_PATID = "PatID"; //$NON-NLS-1$
	public static final String FLD_CREATION_DATE = "CreationDate"; //$NON-NLS-1$ 
	
	public static final String TABLENAME = "CH_ELEXIS_OMNIVORE_DATA"; //$NON-NLS-1$
	public static final String DBVERSION = "2.0.4"; //$NON-NLS-1$
	
	protected static final String VERSION = "1";
	
	//@formatter:off
	public static final String createDB = 
		"CREATE TABLE " +	TABLENAME + " ("
		+ "ID		    VARCHAR(25) primary key,"
		+ "lastupdate   BIGINT,"
		+ "deleted      CHAR(1) default '0',"  
		+ "PatID	    VARCHAR(25),"
		+ "Datum		CHAR(8),"
		+ "CreationDate CHAR(8),"
		+ "Category		VARCHAR(80) default null,"
		+ "Title 		VARCHAR(255)," 
		+ "Mimetype		VARCHAR(255),"
		+ "Keywords		VARCHAR(255)," 
		+ "Path			VARCHAR(255),"
		+ "Doc			BLOB);" 
		+ "CREATE INDEX OMN1 ON " + TABLENAME + " (PatID);"
		+ "CREATE INDEX OMN2 ON " + TABLENAME + " (Keywords);" 
		+ "CREATE INDEX OMN3 ON " + TABLENAME + " (Category);" 
		+ "CREATE INDEX OMN4 ON " + TABLENAME + " (Mimetype);" 
		+ "CREATE INDEX OMN5 ON " + TABLENAME + " (deleted);"
		+ "CREATE INDEX OMN6 ON " + TABLENAME + " (Title);" 
		+ "INSERT INTO " + TABLENAME + " (ID, TITLE) VALUES ('1','"+ DBVERSION + "');";
	//@formatter:on
	
	public static final String upd120 = "ALTER TABLE " + TABLENAME //$NON-NLS-1$
		+ " MODIFY Mimetype VARCHAR(255);" + "ALTER TABLE " + TABLENAME //$NON-NLS-1$ //$NON-NLS-2$
		+ " MODIFY Keywords VARCHAR(255);" + "ALTER TABLE " + TABLENAME //$NON-NLS-1$ //$NON-NLS-2$
		+ " Modify Path VARCHAR(255);"; //$NON-NLS-1$
	public static final String upd200 = "ALTER TABLE " + TABLENAME //$NON-NLS-1$
		+ " ADD Category VARCHAR(80) default null;" //$NON-NLS-1$
		+ "CREATE INDEX OMN3 ON " + TABLENAME + " (Category);" //$NON-NLS-1$ //$NON-NLS-2$
		+ "ALTER TABLE " + TABLENAME + " MODIFY Title VARCHAR(255);"; //$NON-NLS-1$ //$NON-NLS-2$	
	public static final String upd201 = "ALTER TABLE " + TABLENAME //$NON-NLS-1$
		+ " ADD lastupdate BIGINT default 0;"; //$NON-NLS-1$	
	public static final String upd202 = "CREATE INDEX OMN4 ON " + TABLENAME //$NON-NLS-1$
		+ " (Mimetype);"; //$NON-NLS-1$
	public static final String upd203 = "CREATE INDEX OMN5 ON " + TABLENAME //$NON-NLS-1$
		+ " (deleted);" + "CREATE INDEX OMN6 ON " + TABLENAME + " (Title);"; //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$
	public static final String upd204 = "ALTER TABLE " + TABLENAME + " ADD CreationDate CHAR(8);";//$NON-NLS-1$ 
	
	private static List<DocHandle> main_categories = null;
	
	static {
		addMapping(TABLENAME, FLD_PATID, FLD_CAT + "=Category", DATE_COMPOUND, //$NON-NLS-1$
			FLD_CREATION_DATE + "=S:D:" + FLD_CREATION_DATE, FLD_TITLE + "=Title", FLD_KEYWORDS,
			FLD_PATH, FLD_DOC, FLD_MIMETYPE);
		DocHandle start = load(StringConstants.ONE);
		if (!tableExists(TABLENAME)) {
			init();
		} else {
			VersionInfo vi = new VersionInfo(start.get(FLD_TITLE));
			if (vi.isOlder(DBVERSION)) {
				if (vi.isOlder("1.1.0")) { //$NON-NLS-1$
					getConnection().exec("ALTER TABLE " + TABLENAME //$NON-NLS-1$
						+ " ADD if not exists deleted CHAR(1) default '0';"); //$NON-NLS-1$
				}
				if (vi.isOlder("1.2.0")) { //$NON-NLS-1$
					createOrModifyTable(upd120);
				}
				if (vi.isOlder("2.0.0")) { //$NON-NLS-1$
					createOrModifyTable(upd200);
				}
				if (vi.isOlder("2.0.1")) { //$NON-NLS-1$
					createOrModifyTable(upd201);
				}
				if (vi.isOlder("2.0.2")) { //$NON-NLS-1$
					createOrModifyTable(upd202);
				}
				if (vi.isOlder("2.0.3")) { //$NON-NLS-1$
					createOrModifyTable(upd203);
				}
				if (vi.isOlder("2.0.4")) {
					createOrModifyTable(upd204);
				}
				start.set(FLD_TITLE, DBVERSION);
			}
		}
	}
	
	public DocHandle(IOpaqueDocument doc) throws ElexisException{
		create(doc.getGUID());
		
		String category = doc.getCategory();
		if (category == null || category.length() < 1) {
			category = DocHandle.getDefaultCategory().getCategoryName();
		} else {
			DocHandle.ensureCategoryAvailability(category);
		}
		set(new String[] {
			FLD_CAT, FLD_PATID, FLD_DATE, FLD_CREATION_DATE, FLD_TITLE, FLD_KEYWORDS, FLD_MIMETYPE
		}, category, doc.getPatient().getId(), doc.getCreationDate(), doc.getCreationDate(),
			doc.getTitle(), doc.getKeywords(), doc.getMimeType());
		store(doc.getContentsAsBytes());
	}
	
	public DocHandle(String category, byte[] doc, Patient pat, String title, String mime,
		String keyw){
		this(category, doc, pat, new Date(), title, mime, keyw);
	}
	
	public DocHandle(String category, byte[] doc, Patient pat, Date creationDate, String title,
		String mime, String keyw){
		if ((doc == null) || (doc.length == 0)) {
			SWTHelper.showError(Messages.DocHandle_readErrorCaption,
				Messages.DocHandle_readErrorText);
			return;
		}
		create(null);
		
		if (category == null || category.length() < 1) {
			category = DocHandle.getDefaultCategory().getCategoryName();
		} else {
			DocHandle.ensureCategoryAvailability(category);
		}
		
		if (creationDate == null) {
			creationDate = new Date();
		}
		
		if (category == null || category.length() == 0) {
			set(new String[] {
				FLD_PATID, FLD_DATE, FLD_CREATION_DATE, FLD_TITLE, FLD_KEYWORDS, FLD_MIMETYPE
			}, pat.getId(), new TimeTool().toString(TimeTool.DATE_GER),
				new TimeTool(creationDate).toString(TimeTool.DATE_COMPACT), title, keyw, mime);
		} else {
			set(new String[] {
				FLD_CAT, FLD_PATID, FLD_DATE, FLD_CREATION_DATE, FLD_TITLE, FLD_KEYWORDS,
				FLD_MIMETYPE
			}, category, pat.getId(), new TimeTool().toString(TimeTool.DATE_GER),
				new TimeTool(creationDate).toString(TimeTool.DATE_COMPACT), title, keyw, mime);
			
		}
		store(doc);
	}
	
	@Override
	protected String getTableName(){
		return TABLENAME;
	}
	
	protected DocHandle(String id){
		super(id);
	}
	
	protected DocHandle(){}
	
	/**
	 * We need a default category as a fallback for invalid or not defined categories.
	 * 
	 * @return the default category in case no other category is defined
	 */
	public static DocHandle getDefaultCategory(){
		Query<DocHandle> qbe = new Query<DocHandle>(DocHandle.class);
		qbe.add(FLD_MIMETYPE, Query.EQUALS, CATEGORY_MIMETYPE);
		qbe.add(FLD_TITLE, Query.EQUALS, DEFAULT_CATEGORY);
		List<DocHandle> qre = qbe.execute();
		if (qre.size() < 1) {
			addMainCategory(DEFAULT_CATEGORY);
			return DocHandle.getDefaultCategory();
		}
		return qre.get(0);
	}
	
	/**
	 * Ensure that a certain requested category is available in the system. If the category is
	 * already here, nothing happens, else it is created
	 * 
	 * @param categoryName
	 *            the respective category name
	 */
	public static void ensureCategoryAvailability(String categoryName){
		List<DocHandle> ldh = getMainCategories();
		boolean found = false;
		for (DocHandle dh : ldh) {
			if (dh.get(FLD_TITLE).equalsIgnoreCase(categoryName)
				|| dh.get(FLD_CAT).equalsIgnoreCase(categoryName)) {
				found = true;
				continue;
			}
		}
		if (found) {
			return;
		} else {
			DocHandle.addMainCategory(categoryName);
		}
	}
	
	public static List<String> getMainCategoryNames(){
		List<DocHandle> dox = getMainCategories();
		ArrayList<String> ret = new ArrayList<String>(dox.size());
		for (DocHandle doch : dox) {
			ret.add(doch.get(FLD_TITLE));
		}
		return ret;
		
	}
	
	public static List<DocHandle> getMainCategories(){
		if (main_categories == null) {
			Query<DocHandle> qbe = new Query<DocHandle>(DocHandle.class);
			qbe.add(FLD_MIMETYPE, Query.EQUALS, CATEGORY_MIMETYPE);
			main_categories = qbe.execute();
		}
		return main_categories;
	}
	
	public static void addMainCategory(String name){
		if (findCategory(name) == null) {
			DocHandle dh = new DocHandle();
			dh.create(null);
			dh.set(new String[] {
				FLD_TITLE, FLD_CAT, FLD_MIMETYPE
			}, name, name, CATEGORY_MIMETYPE);
			main_categories = null;
		}
	}
	
	private static String findCategory(String name){
		List<DocHandle> categories = getMainCategories();
		for (DocHandle docHandle : categories) {
			String catName = docHandle.getCategoryName().toLowerCase();
			if (catName.equals(name.toLowerCase())) {
				return docHandle.getCategory();
			}
		}
		return null;
	}
	
	public static void renameCategory(String old, String newn){
		String oldname = old.trim();
		String newName = newn.trim();
		
		if (findCategory(newName) == null) {
			getConnection().exec("update CH_ELEXIS_OMNIVORE_DATA set Category="
				+ JdbcLink.wrap(newName) + " where Category= " + JdbcLink.wrap(oldname));
			getConnection().exec("update CH_ELEXIS_OMNIVORE_DATA set Title="
				+ JdbcLink.wrap(newName) + " where Title=" + JdbcLink.wrap(oldname)
				+ " and mimetype=" + JdbcLink.wrap("text/category"));
			main_categories = null;
			log.info("Renaming category [{}], moving entries to category [{}]", old, newn);
		} else {
			Shell shell = PlatformUI.getWorkbench().getActiveWorkbenchWindow().getShell();
			MessageDialog.openWarning(shell, Messages.Dochandle_errorCatNameAlreadyTaken,
				MessageFormat.format(Messages.DocHandle_errorCatNameAlreadyTakenMsg, newName));
			
		}
		clearCache();
	}
	
	public static void removeCategory(String name, String destName){
		getConnection().exec("update CH_ELEXIS_OMNIVORE_DATA set Category="
			+ JdbcLink.wrap(destName) + " where Category= " + JdbcLink.wrap(name));
		getConnection().exec("update CH_ELEXIS_OMNIVORE_DATA set deleted='1' where Title="
			+ JdbcLink.wrap(name) + " AND mimetype=" + JdbcLink.wrap("text/category"));
		main_categories = null;
		log.info("Removing category [{}], moving entries to category [{}]", name, destName);
	}
	
	/**
	 * Tabelle neu erstellen
	 */
	public static void init(){
		createOrModifyTable(createDB);
	}
	
	public static DocHandle load(String id){
		//TODO: 20210327js: Review this. Replaced by content from 2.1.7js/omnivore_js.
		DocHandle ret = new DocHandle(id);
		if (ret.exists()) {
			return ret;
		}
		return null;
	}
	
	private void store(byte[] doc){
		try {
			storeContent(doc);
		} catch (PersistenceException e) {
			SWTHelper.showError(Messages.DocHandle_writeErrorCaption,
				Messages.DocHandle_writeErrorText + "; " + e.getMessage());
			delete();
		} catch (ElexisException e) {
			ExHandler.handle(e);
			SWTHelper.showError(Messages.DocHandle_73, Messages.DocHandle_writeErrorHeading,
				MessageFormat.format(Messages.DocHandle_writeErrorText2 + e.getCause(),
					e.getMessage()));
			delete();
		}
	}
	
	public void storeContent(byte[] doc) throws PersistenceException, ElexisException{
		File file = getStorageFile(false);
		if (file == null) {
			setBinary(FLD_DOC, doc);
		} else {
			try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file))) {
				bout.write(doc);
			}
			catch (Exception e) {
				throw new ElexisException(file.getAbsolutePath(), e);
			}
		}
	}
	

	/**
	 * If force is set or the preference Preferences.STOREFS is true a new File object is created.
	 * Else the file is a BLOB in the db and null is returned.
	 * 
	 * The path of the new file will be: Preferences.BASEPATH/PatientCode/
	 * 
	 * The name of the new file will be: PersistentObjectId.FileExtension
	 * 
	 * @param force
	 *            access to the file system
	 * @return File to read from, or write to, or null
	 */
	public File getStorageFile(boolean force){
		if (force || Preferences.storeInFilesystem()) {
			String pathname = Preferences.getBasepath();
			if (pathname != null) {
				File dir = new File(pathname);
				if (dir.isDirectory()) {
					Patient pat = Patient.load(get(FLD_PATID));
					File subdir = new File(dir, pat.getPatCode());
					if (!subdir.exists()) {
						subdir.mkdir();
					}
					File file = new File(subdir, getId() + "." //$NON-NLS-1$
						+ FileTool.getExtension(get(FLD_MIMETYPE)));
					if (!file.exists()) {
						file = new File(subdir, getId() + "." //$NON-NLS-1$
							+ getFileExtension());
					}
					return file;
				}
			}
			if (Preferences.storeInFilesystem()) {
				configError();
			}
		}
		return null;
	}
	public String getCategoryName(){
		return checkNull(get(FLD_CAT));
	}
	
	public boolean isCategory(){
		return get(FLD_MIMETYPE).equals(CATEGORY_MIMETYPE);
	}
	
	public DocHandle getCategoryDH(){
		String name = getCategoryName();
		if (!StringTool.isNothing(name)) {
			List<DocHandle> ret = new Query<DocHandle>(DocHandle.class, FLD_TITLE, name).execute();
			if (ret != null && ret.size() > 0) {
				return ret.get(0);
			}
		}
		return null;
	}
	
	public List<DocHandle> getMembers(Patient pat){
		Query<DocHandle> qbe = new Query<DocHandle>(DocHandle.class, FLD_CAT, get(FLD_TITLE));
		if (pat != null) {
			qbe.add(FLD_PATID, Query.EQUALS, pat.getId());
		}
		return qbe.execute();
	}
	
	@Override
	public String getLabel(){
		StringBuilder sb = new StringBuilder();
		// avoid adding only a space - causes trouble in renaming of categories
		String date = get(FLD_DATE);
		if (date != null && !date.isEmpty()) {
			sb.append(get(FLD_DATE));
			sb.append(StringConstants.SPACE);
		}
		sb.append(get(FLD_TITLE));
		return sb.toString();
	}
	
	public String getTitle(){
		return get(FLD_TITLE);
	}
	
	public String getKeywords(){
		return get(FLD_KEYWORDS);
	}
	
	public String getDate(){
		toStringTool.set(get(FLD_DATE));
		return toStringTool.toString(TimeTool.DATE_GER);
	}
	
	public void setDate(Date d){
		TimeTool tt = new TimeTool();
		tt.setTime(d);
		set(FLD_DATE, tt.toString(TimeTool.DATE_COMPACT));
	}
	
	@Override
	public String getCreationDate(){
		toStringTool.set(get(FLD_CREATION_DATE));
		return toStringTool.toString(TimeTool.DATE_GER);
		
	}
	
	public void setCreationDate(Date d){
		TimeTool tt = new TimeTool();
		tt.setTime(d);
		set(FLD_CREATION_DATE, tt.toString(TimeTool.DATE_COMPACT));
	}
	
	public byte[] getContents(){
		byte[] ret = getBinary(FLD_DOC);
		if (ret == null) {
			File file = getStorageFile(true);
			if (file != null) {
				try {
					byte[] bytes = Files.readAllBytes(Paths.get(file.toURI()));
					// if we stored the file in the file system but decided
					// later to store it in the database:
					// copy the file from the file system to the database
					if (!Preferences.storeInFilesystem()) {
						try {
							setBinary(FLD_DOC, bytes);
						} catch (PersistenceException pe) {
							SWTHelper.showError(Messages.DocHandle_readErrorCaption,
								Messages.DocHandle_importErrorText + "; " + pe.getMessage());
						}
					}
					
					return bytes;
				} catch (Exception ex) {
					ExHandler.handle(ex);
					SWTHelper.showError(Messages.DocHandle_readErrorHeading,
						Messages.DocHandle_importError2,
						MessageFormat.format(Messages.DocHandle_importErrorText2 + ex.getMessage(),
							file.getAbsolutePath()));
				}
			}
		}
		return ret;
	}

	//TODO: 20210327js: This is the execute function found in 3.7.
	// It looks less elaborate than my older one, because
	// (a) It does not decode MimeTypes for the extension, takes it as given via getTitle()
	// (b) It does not check whether the temp file could be written before trying to start a viewer
	// So for now, I retire this one. But it remains an open ToDo,
	// to check whether there is anyhting in favour of this one,
	// OR to re-clean the temp file creation code:
	// Now, I've just brought back the original from 2.1.7js / omnivore_js
	// that accepts an extension, whereas Niklaus has moved similar code out
	// to Utils.createNiceFilename(DocHandle dh) --- that will need some consolidation work.
	//
	// The new / old / other execute() is located further below.
	// Original file: 
	// jsigle@blackbox  Sa Mär 27  20:13:22  /mnt/sdb3/Elexis-workspace/elexis-2.1.7-20130523/elexis-bootstrap-js-201712191036-last-20130605based-with-MSWord_js-as-used-by-JH-since-201701-before-gitpush  
	// $ kate ./elexis-base/ch.elexis.omnivore/src/ch/elexis/omnivore/data/DocHandle.java
	
	//20210327js: Simpler original execute() function:
	/*
	public void execute(){
		try {
			String ext = StringConstants.SPACE; //""; //$NON-NLS-1$
			File temp = createTemporaryFile(getTitle());

			log.debug("execute {} readable {}", temp.getAbsolutePath(), Files.isReadable(temp.toPath()));

			Program proggie = Program.findProgram(ext);
			if (proggie != null) {
				proggie.execute(temp.getAbsolutePath());
			} else {
				if (Program.launch(temp.getAbsolutePath()) == false) {
					Runtime.getRuntime().exec(temp.getAbsolutePath());
				}
				
			}
			
		} catch (Exception ex) {
			ExHandler.handle(ex);
			SWTHelper.showError(Messages.DocHandle_runErrorHeading, ex.getMessage());
		}
	}
	*/
	//20210327js: End of simpler original modified execute() function:
	
	private String getFileExtension() {
		String mimetype = get(FLD_MIMETYPE);
		String fileExtension = MimeTool.getExtension(mimetype);
		if (StringUtils.isBlank(fileExtension)) {
			fileExtension = FileTool.getExtension(mimetype);
			if (StringUtils.isBlank(fileExtension)) {
				fileExtension = FileTool.getExtension(get(FLD_TITLE));
			}
			if (StringUtils.isBlank(fileExtension) && StringUtils.isNotBlank(mimetype)) {
				fileExtension = mimetype;
			}
		}
		return fileExtension;
	}
	
	/**
	 * create a temporary file
	 * 
	 * @return temporary file
	 */
	public File createTemporaryFile(String title){
		String fileExtension = getFileExtension();
		
		if (fileExtension == null) {
			fileExtension = "";
		}
		
		String config_temp_filename = Utils.createNiceFilename(this);
		
		log.debug("createTemporaryFile: config_temp_filename=<{}>", config_temp_filename );
		
		File temp = null;
		
		try {
			Path tmpDir = Files.createTempDirectory("elexis");
			if (config_temp_filename.length() > 0) {
				temp = new File(tmpDir.toString(), config_temp_filename + "." + fileExtension);
				
			} else {
				// use title if given
				if (title != null && !title.isEmpty()) {
					// Remove all characters that shall not appear in the generated filename			
					//TODO: 20210327js: See comments in PreferenceConstants.java, Utils.java etc.					
					String cleanTitle = title.replaceAll(java.util.regex.Matcher
							.quoteReplacement(Preferences.cotf_unwanted_chars_ngregex), "_");
					
					if (!cleanTitle.toLowerCase().contains("." + fileExtension.toLowerCase())) {
						temp = new File(tmpDir.toString(), cleanTitle + "." + fileExtension);
					} else {
						temp = new File(tmpDir.toString(), cleanTitle);
					}
				} else {
					temp = Files.createTempFile(tmpDir, "omni_", "_vore." + fileExtension).toFile();
				}
			}
			tmpDir.toFile().deleteOnExit();
			temp.deleteOnExit();
	
			byte[] b = getContents(); // getBinary(FLD_DOC);
			if (b == null) {
				SWTHelper.showError(Messages.DocHandle_readErrorCaption2,
					Messages.DocHandle_loadErrorText);
				return temp;
			}
			try (FileOutputStream fos = new FileOutputStream(temp)) {
				fos.write(b);
			}
			log.debug("createTemporaryFile {} size {} ext {} ", temp.getAbsolutePath(),
				Files.size(temp.toPath()), fileExtension);
		} catch (FileNotFoundException e) {
			log.debug("File not found " + e, Log.WARNINGS);
		} catch (IOException e) {
			log.debug("Error creating file " + e, Log.WARNINGS);
		}
		
		return temp;
	}
	
	public String getMimetype(){
		return get(FLD_MIMETYPE);
	}
	
	public boolean storeExternal(String filename){
		byte[] b = getContents();
		if (b == null) {
			SWTHelper.showError(Messages.DocHandle_readErrorCaption2,
				Messages.DocHandle_couldNotLoadError);
			return false;
		}
		try (FileOutputStream fos = new FileOutputStream(filename)) {
			fos.write(b);
			return true;
		} catch (IOException ios) {
			ExHandler.handle(ios);
			SWTHelper.showError(Messages.DocHandle_writeErrorCaption2,
				Messages.DocHandle_writeErrorCaption2, ios.getMessage());
			return false;
		}
	}
	
	public static List<DocHandle> assimilate(List<ImageData> images){
		List<DocHandle> ret = new ArrayList<DocHandle>();
		FileImportDialog fid = new FileImportDialog(Messages.DocHandle_scannedImageDialogCaption);
		//TODO: Prüfen, ob hier im weiteren Verlauf auch
		//noch ein Check einer Dateinamens-Länge nötig ist.
		if (fid.open() == Dialog.OK) {
			try {
				Document pdf = new Document(PageSize.A4);
				pdf.setMargins(0, 0, 0, 0);
				ByteArrayOutputStream baos = new ByteArrayOutputStream(100000);
				PdfWriter.getInstance(pdf, baos);
				pdf.open();
				ImageLoader il = new ImageLoader();
				for (int i = 0; i < images.size(); i++) {
					ImageData[] id = new ImageData[] {
						images.get(i)
					};
					il.data = id;
					ByteArrayOutputStream bimg = new ByteArrayOutputStream();
					il.save(bimg, SWT.IMAGE_PNG);
					Image image = Image.getInstance(bimg.toByteArray());
					int width = id[0].width;
					int height = id[0].height;
					// 210mm = 8.27 In = 595 px bei 72dpi
					// 297mm = 11.69 In = 841 px
					if ((width > 595) || (height > 841)) {
						image.scaleToFit(595, 841);
					}
					pdf.add(image);
				}
				pdf.close();
				DocHandle docHandle = new DocHandle(fid.category, baos.toByteArray(),
					ElexisEventDispatcher.getSelectedPatient(), fid.originDate, fid.title,
					"image.pdf", fid.keywords); //$NON-NLS-1$
				Utils.archiveFile(docHandle.getStorageFile(true), docHandle);
				ret.add(docHandle);
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_readError,
					Messages.DocHandle_readErrorText2);
			}
		}
		return ret;
	}
	
	public static DocHandle assimilate(String f){
		Patient act = ElexisEventDispatcher.getSelectedPatient();
		if (act == null) {
			SWTHelper.showError(Messages.DocHandle_noPatientSelected,
				Messages.DocHandle_pleaseSelectPatient);
			return null;
		}
		File file = new File(f);
		if (!file.canRead()) {
			SWTHelper.showError(Messages.DocHandle_cantReadCaption,
				MessageFormat.format(Messages.DocHandle_cantReadText, f));
			return null;
		}
		
		// can't import complete directory
		if (file.isDirectory()) {
			SWTHelper.showError(Messages.DocHandle_importErrorDirectory,
				Messages.DocHandle_importErrorDirectoryText);
			return null;
		}

		//20210326js: Add missing filename length check here, too.
		//js: Check filename length to avoid lockup observed in MS Windows
		Integer maxOmnivoreFilenameLength = Preferences.getOmnivoreMax_Filename_Length();
		String nam = file.getName();
		if (nam.length() > maxOmnivoreFilenameLength) {
			SWTHelper.showError(Messages.DocHandle_readErrorCaption,
					Messages.DocHandle_fileNameTooLong);
			return null;
		}
		
		FileImportDialog fid = new FileImportDialog(file.getName());
		if (fid.open() == Dialog.OK) {
			try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
					ByteArrayOutputStream baos = new ByteArrayOutputStream();) {
				int in;
				while ((in = bis.read()) != -1) {
					baos.write(in);
				}
				
				/*
				 * TODO: 20210326js: This is redundant here. Was already checked above.
				 * Review and remove if truly obsolete.
				String nam = file.getName();
				if (nam.length() > 255) {
					SWTHelper.showError(Messages.DocHandle_readErrorCaption3,
						Messages.DocHandle_fileNameTooLong);
					return null;
				}
				 */
				
				String category = fid.category;
				if (category == null || category.length() == 0) {
					category = DocHandle.getDefaultCategory().getCategoryName();
				}
				
				DocHandle dh = new DocHandle(category, baos.toByteArray(), act, fid.originDate,
					fid.title, file.getName(), fid.keywords);
			
				if (Preferences.getDateModifiable()) {
					dh.setDate(fid.saveDate);
					dh.setCreationDate(fid.originDate);
				}

				//20210326js: Add rule based auto archiving, here as well.
				//TODO: Check if this is the right place to have Utils.archiveFile
				//in this assimilate() method; 
				//compare this with the other assimilate() method below.

				//js: Automatisches Wegarchivieren des soeben importierten Files
				//    unter Beachtung der Regeln (Muster, Ziel-Ordner) aus
				//    PREF_SRC_PATTERN[] und PREF_DEST_DIR[]
				Utils.archiveFile(file, dh);

				return dh;
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_readErrorCaption3,
					Messages.DocHandle_readErrorText2);
			}
		}
		return null;
	}
	
	public static DocHandle assimilate(String f, String selectedCategory){
		Patient act = ElexisEventDispatcher.getSelectedPatient();
		if (act == null) {
			SWTHelper.showError(Messages.DocHandle_noPatientSelected,
				Messages.DocHandle_pleaseSelectPatient);
			return null;
		}
		
		File file = new File(f);
		if (!file.canRead()) {
			SWTHelper.showError(Messages.DocHandle_cantReadCaption,
				String.format(Messages.DocHandle_cantReadMessage, f));
			return null;
		}
		
		// can't import complete directory
		if (file.isDirectory()) {
			SWTHelper.showError(Messages.DocHandle_importErrorDirectory,
				Messages.DocHandle_importErrorDirectoryText);
			return null;
		}
		
		//js: Check filename length to avoid lockup observed in MS Windows
		Integer maxOmnivoreFilenameLength = Preferences.getOmnivoreMax_Filename_Length();
		String nam = file.getName();
		if (nam.length() > maxOmnivoreFilenameLength) {
			//20210326js: Use more specific message here.
			SWTHelper.showError(Messages.DocHandle_readErrorCaption,
					Messages.DocHandle_fileNameTooLong);
			return null;
		}
		
		FileImportDialog fid;
		if (selectedCategory == null) {
			fid = new FileImportDialog(file.getName());
		} else {
			fid = new FileImportDialog(file.getName(), selectedCategory);
		}
		
		DocHandle dh = null;
		if (fid.open() == Dialog.OK) {
			try (BufferedInputStream bis = new BufferedInputStream(new FileInputStream(file));
					ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
				
				int in;
				while ((in = bis.read()) != -1) {
					baos.write(in);
				}
			
				/*
				 * TODO: 20210326js: This is redundant here. Was already checked above.
				 * Review and remove if truly obsolete.
				String fileName = file.getName();
				if (fileName.length() > 255) {
					SWTHelper.showError(Messages.DocHandle_readErrorCaption,
						Messages.DocHandle_fileNameTooLong);
					return null;
				}
				 */
				
				String category = fid.category;
				if (category == null || category.length() == 0) {
					category = DocHandle.getDefaultCategory().getCategoryName();
				}
				dh = new DocHandle(category, baos.toByteArray(), act, fid.title.trim(),
					file.getName(), fid.keywords.trim());
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_importErrorCaption,
					Messages.DocHandle_importErrorMessage2);
				return null;
			}
			
			//js: Automatisches Wegarchivieren des soeben importierten Files
			//    unter Beachtung der Regeln (Muster, Ziel-Ordner) aus
			//    PREF_SRC_PATTERN[] und PREF_DEST_DIR[]
			Utils.archiveFile(file, dh);
		}
		return dh;
	}

	private void configError(){
		SWTHelper.showError("config error", Messages.DocHandle_configErrorCaption, //$NON-NLS-1$
			Messages.DocHandle_configErrorText);
	}
	
	// IDocument
	@Override
	public String getCategory(){
		return getCategoryName();
	}
	
	@Override
	public String getMimeType(){
		return checkNull(get(FLD_MIMETYPE));
	}
	
	@Override
	public Patient getPatient(){
		return Patient.load(get(FLD_PATID));
	}
	
	@Override
	public InputStream getContentsAsStream() throws ElexisException{
		return new ByteArrayInputStream(getContents());
	}
	
	@Override
	public byte[] getContentsAsBytes() throws ElexisException{
		return getContents();
	}
	
	@Override
	public String getGUID(){
		return getId();
	}
	
	/**
	 * Move the DocHandle from the db to the file system and delete the BLOB afterwards.
	 */
	public boolean exportToFileSystem(){
		byte[] doc = getBinary(FLD_DOC);
		// return true if doc is already on file system
		if (doc == null)
			return true;
		File file = getStorageFile(true);
		try (BufferedOutputStream bout = new BufferedOutputStream(new FileOutputStream(file))) {
			bout.write(doc);
			setBinary(FLD_DOC, null);
		} catch (IOException ios) {
			ExHandler.handle(ios);
			log.warn("Exporting dochandle [{}] to filesystem fails.", getId(), ios);
			SWTHelper.showError(Messages.DocHandle_writeErrorCaption2,
				Messages.DocHandle_writeErrorCaption2, ios.getMessage());
			return false;
		}
		return true;
	}
	
	//201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
	//Therefore, separated the pre Omnivore_js-1.4.6 method execute() into multiple methods. 
	public String obtainExt() {
		String ext = ""; //$NON-NLS-1$
		String typname = get("Mimetype"); //$NON-NLS-1$
		int r = typname.lastIndexOf('.');
		if (r == -1) {
			typname = get("Titel"); //$NON-NLS-1$
			r = typname.lastIndexOf('.');
		}
		
		if (r != -1) {
			ext = typname.substring(r + 1);
		}
		return ext;
	}

	//201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
	//Therefore, separated the pre Omnivore_js-1.4.6 method execute() into multiple methods.

	//TODO: CHECK HOW THIS OVERLAPS WIH NIKLAUS MOVING createNiceFileName(DocHandle dh) to Utils Java!
	
	public File makeTempFile(String ext){
			try {
	  			//20130411js: Make the temporary filename configurable
				StringBuffer configured_temp_filename=new StringBuffer();
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				configured_temp_filename.append(Utils.getTempFilenameElement("constant1",""));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				configured_temp_filename.append(Utils.getTempFilenameElement("PID",getPatient().getKuerzel()));	//getPatient() liefert in etwa: ch.elexis.com@1234567; getPatient().getId() eine DB-ID; getPatient().getKuerzel() die Patientennummer.
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				configured_temp_filename.append(Utils.getTempFilenameElement("fn",getPatient().getName()));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				configured_temp_filename.append(Utils.getTempFilenameElement("gn",getPatient().getVorname()));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				configured_temp_filename.append(Utils.getTempFilenameElement("dob",getPatient().getGeburtsdatum()));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());

				configured_temp_filename.append(Utils.getTempFilenameElement("dt",getTitle()));				//not more than 80 characters, laut javadoc
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				configured_temp_filename.append(Utils.getTempFilenameElement("dk",getKeywords()));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				//Da könnten auch noch Felder wie die Document Create Time etc. rein - siehe auch unten, die Methoden getPatient() etc.
				
				configured_temp_filename.append(Utils.getTempFilenameElement("dguid",getGUID()));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				
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
				int  needed_bits = (int) Math.round(Math.ceil(Math.log(Preferences.nPreferences_cotf_element_digits_max)/Math.log(2)));
				configured_temp_filename.append(Utils.getTempFilenameElement("random",new BigInteger(needed_bits , random).toString() ));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				
				configured_temp_filename.append(Utils.getTempFilenameElement("constant2",""));
				log.debug("DocHandle.makeTempFileString: configured_temp_filename = <{}>", configured_temp_filename.toString());
				
				File temp;
				if (configured_temp_filename.length()>0) {
					//The following file will have a unique variable part after the configured_temp_filename_and before the .ext,
					//but will be located in the temporary directory.
					File uniquetemp = File.createTempFile(configured_temp_filename.toString()+"_","."+ext); //$NON-NLS-1$ //$NON-NLS-2$
					String temp_pathname=uniquetemp.getParent();
					uniquetemp.delete(); 
					
					//remove the _unique variable part from the temporary filename and create a new file in the same directory as the previously automatically created unique temp file
					log.debug("DocHandle.makeTempFileString: temp_pathname = <{}>", temp_pathname);
					log.debug("DocHandle.makeTempFileString: configured_temp_filename.ext = <{}.{}>", configured_temp_filename , ext);
					temp = new File(temp_pathname,configured_temp_filename+"."+ext);
					temp.createNewFile();
				}
				else {
					//if special rules for the filename are not configured, then generate it simply as before Omnivore_js Version 1.4.4
					temp = File.createTempFile("omni_", "_vore." + ext); //$NON-NLS-1$ //$NON-NLS-2$
				}
				
			return temp;	
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_execError, ex.getMessage());
				return null;
			}
		}
		
		//201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
		//Therefore, separated the pre Omnivore_js-1.4.6 method execute() into multiple methods. 
		public boolean writeDocToTempFile(File temp) {
			if (temp == null) {return false;}
			
			try {
				temp.deleteOnExit();
				
				byte[] b = getBinary("Doc"); //$NON-NLS-1$
				if (b == null) {
					SWTHelper.showError(Messages.DocHandle_readErrorCaption,
							Messages.DocHandle_readError);
					return false;
				}
				
				FileOutputStream fos = new FileOutputStream(temp);
				fos.write(b);
				fos.close();
				
				return true;
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_execError, ex.getMessage());
			}
			
			return false;
		}
		
		//201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
		//Therefore, separated the pre Omnivore_js-1.4.6 method execute() into multiple methods. 

		//This method tries to write selected content into a file, and launch a suitable program to display it.
		//The temporary filename is constructed to be meaningfull, according to configured settings.
		//The temporary file should exist until the program is closed. But its filename might be re-used,
		//also depending upon settings for temporary filename generation.
		
		//20210327js: Joerg's original modified execute() function:
		public void execute(){
			try {
				String ext = obtainExt();
				File temp = makeTempFile(ext);
				if (writeDocToTempFile(temp)) {	
					Program proggie = Program.findProgram(ext);
					if (proggie != null) {
						proggie.execute(temp.getAbsolutePath());
					} else {
						if (Program.launch(temp.getAbsolutePath()) == false) {
							Runtime.getRuntime().exec(temp.getAbsolutePath());
						}
					}
				} else {
					SWTHelper.showError(Messages.DocHandle_readErrorCaption,
							Messages.DocHandle_readError);				
				}
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_execError, ex.getMessage());
			}
		}
		//20210327js: End of Joerg's original modified execute() function:

		//201305280110js: Add drag support, so stored documents can be dragged into an e-mail program.
		//Therefore, separated the pre Omnivore_js-1.4.6 method execute() into multiple methods...
		//...and finally adding the method giveAway(), which needs the same preparations as execute()

		//Omnivore_js version 1.4.6:
		//This method tries to write selected content into a file, and returns the name of that file.
		//Which in turn can be given away e.g. via drag&drop to an E-mail program etc.
		//The temporary filename is constructed to be meaningfull, according to configured settings.
		//The temporary file should exist until the program is closed. But its filename might be re-used,
		//also depending upon settings for temporary filename generation.
		public String giveAway(){
			try {
				String ext = obtainExt();
				File temp = makeTempFile(ext);
				if (writeDocToTempFile(temp)) {
					
					log.debug("js DocHandle.giveAwaz: Ready to giveAway the file: <{}>"+temp.getAbsolutePath());
					log.debug("js DocHandle.giveAwaz: WARNING: temp.deleteOnExit() has been set above,");
					log.debug("js DocHandle.giveAwaz: so the temp file will/may be deleted when the virtual machine exits.");
					return temp.getAbsolutePath();
					}
				else {
					SWTHelper.showError(Messages.DocHandle_readErrorCaption,
						Messages.DocHandle_readError);				
				}
			} catch (Exception ex) {
				ExHandler.handle(ex);
				SWTHelper.showError(Messages.DocHandle_execError, ex.getMessage());
			}
			return null;
		}

}
