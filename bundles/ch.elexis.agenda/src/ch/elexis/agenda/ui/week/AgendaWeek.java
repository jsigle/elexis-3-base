/*******************************************************************************
 * Copyright (c) 2009, G. Weirich and Elexis
 * All rights reserved. This program and the accompanying materials
 * are made available under the terms of the Eclipse Public License v1.0
 * which accompanies this distribution, and is available at
 * http://www.eclipse.org/legal/epl-v10.html
 *
 * Contributors:
 *    G. Weirich - initial implementation
 *    
 *******************************************************************************/
package ch.elexis.agenda.ui.week;

import java.util.ArrayList;

import org.eclipse.jface.action.Action;
import org.eclipse.jface.action.IAction;
import org.eclipse.jface.action.IMenuCreator;
import org.eclipse.jface.action.IToolBarManager;
import org.eclipse.jface.action.Separator;
import org.eclipse.jface.dialogs.Dialog;
import org.eclipse.swt.SWT;
import org.eclipse.swt.custom.ScrolledComposite;
import org.eclipse.swt.events.SelectionAdapter;
import org.eclipse.swt.events.SelectionEvent;
import org.eclipse.swt.layout.GridLayout;
import org.eclipse.swt.widgets.Composite;
import org.eclipse.swt.widgets.Control;
import org.eclipse.swt.widgets.Menu;
import org.eclipse.swt.widgets.MenuItem;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import ch.elexis.actions.Activator;
import ch.elexis.agenda.data.IPlannable;
import ch.elexis.agenda.preferences.PreferenceConstants;
import ch.elexis.agenda.ui.BaseView;
import ch.elexis.core.data.activator.CoreHub;
import ch.elexis.core.ui.dialogs.DateSelectorDialog;
import ch.elexis.core.ui.icons.Images;
import ch.elexis.core.ui.util.SWTHelper;
import ch.rgw.tools.StringTool;
import ch.rgw.tools.TimeTool;

public class AgendaWeek extends BaseView {
	public static Logger log = LoggerFactory.getLogger("ch.elexis.agenda.AgendaWeek"); //$NON-NLS-1$
	private IAction weekFwdAction, weekBackAction, showCalendarAction;
	
	private ProportionalSheet sheet;
	private ColumnHeader header;
	
	public AgendaWeek(){
		
	}
	
	public ColumnHeader getHeader(){
		return header;
	}
	
	@Override
	protected void create(Composite parent){
		makePrivateActions();
		Composite wrapper = new Composite(parent, SWT.NONE);
		wrapper.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		wrapper.setLayout(new GridLayout());
		header = new ColumnHeader(wrapper, this);
		header.setLayoutData(SWTHelper.getFillGridData(1, true, 1, false));
		ScrolledComposite bounding = new ScrolledComposite(wrapper, SWT.V_SCROLL);
		bounding.setLayoutData(SWTHelper.getFillGridData(1, true, 1, true));
		// bounding.setBackground(Desk.getColor(Desk.COL_RED));
		sheet = new ProportionalSheet(bounding, this);
		// sheet.setSize(sheet.computeSize(SWT.DEFAULT,SWT.DEFAULT));
		bounding.setContent(sheet);
		bounding.setMinSize(sheet.computeSize(SWT.DEFAULT, SWT.DEFAULT));
		bounding.setExpandHorizontal(true);
		bounding.setExpandVertical(true);
		TimeTool tt = new TimeTool();
		for (String s : getDisplayedDays()) {
			tt.set(s);
			checkDay(null, tt);
		}
	}
	
	void clear(){
		sheet.clear();
	}
	
	@Override
	protected IPlannable getSelection(){
		// TODO Auto-generated method stub
		return null;
	}
	
	@Override
	protected void refresh(){
		TimeTool ttMonday = agenda.getActDate();
		ttMonday.set(TimeTool.DAY_OF_WEEK, TimeTool.MONDAY);
		StringBuilder sb = new StringBuilder(ttMonday.toString(TimeTool.DATE_GER));
		ttMonday.addDays(6);
		sb.append("-").append(ttMonday.toString(TimeTool.DATE_GER)); //$NON-NLS-1$
		
		showCalendarAction.setText(sb.toString());
		sheet.refresh();
		
	}
	
	@Override
	public void setFocus(){
		refresh();
	}
	
	public String[] getDisplayedDays(){
		TimeTool ttMonday = Activator.getDefault().getActDate();
		//log.debug("getDisplayDays: ttMonday: "+ttMonday.dump()); 

		ttMonday.set(TimeTool.DAY_OF_WEEK, TimeTool.MONDAY);
		//log.debug("getDisplayDays: ttMonday: "+ttMonday.dump()); 

		ttMonday.chop(3);
		//log.debug("getDisplayDays: ttMonday: "+ttMonday.dump()); 
		
		//log.debug("getDisplayDays: CoreHub.localCfg.get(PreferenceConstants.AG_DAYSTOSHOW) = {}",CoreHub.localCfg.get(PreferenceConstants.AG_DAYSTOSHOW));
		//log.debug("getDisplayDays: TimeTool.Wochentage.length) = {}",TimeTool.Wochentage.length);
		//for (int i=0; i< TimeTool.Wochentage.length; i++) {
		//	log.debug("getDisplayDays: TimeTool.Wochentage[{}]) = {}",i,TimeTool.Wochentage[i]);
		//}
		
		//20210331js: Der folgende Code soll wohl einen String mit Datums-Angaben liefern,
		//welcher diejenigen Tage bezeichnet, deren Wochentagsbezeichnung in einer
		//konfigurierbaren Liste von in der Agenda anzuzeigenden Wochentagen enthalten ist,
		//- beginnend frühestens mit dem Montag ab eingestelltem Datum(sbereich).
		//- NUUUUN ja.
		//I convert the setting to upper case on the fly to exclude a possible problem for matching below.
		String resources =
		CoreHub.localCfg.get(PreferenceConstants.AG_DAYSTOSHOW,
				StringTool.join(TimeTool.Wochentage, ",")).toUpperCase(); //$NON-NLS-1$
		
		if (resources == null) {
			log.debug("getDisplayDays: ERROR: resources == null, therefore returning new String[0]");
			return new String[0];
		} else {
			//log.debug("getDisplayDays: resources <> null...");
			//log.debug("getDisplayDays: resources: "+resources);
			//log.debug("getDisplayDays: resources.length() = {}",resources.length());
			//log.debug("getDisplayDays: building ArrayList<String> ret in order to return that...");
			
			ArrayList<String> ret = new ArrayList<String>(resources.length());			
			
			//20210331js: Enhanced the string comparison to better tolerate
			//mismatching languages and upperCase/lowerCase:
			//On this system, the original code produced no match because here and now:
			//ressources contained:		Monday,Tuesday,Wednesday,Thursday,Friday,Saturday,Sunday
			//							(maybe due to a missing country specific configuration setting)
			//TimeTool.DAYS.values()[0].FullName.toString() was:	"Sonntag" (etc. for the others)
			//TimeTool.DAYS.values()[0].toString() was:				"SUNDAY" (etc. for the others)
			//and the comparison would only search for "Sonntag" in "Monday,Tuesday,Wednesday,...Sunday",
			//which would naturally deliver zero hits.
			//This would lead to ProportionalSheet.java refresh() getting nothing for it's days variable,
			//and constructing an invalid SQL query, rendering the Wochenanzeige completely dysfunctional.
			//TODO: Please check for other places in the program that could be affected in a similar way.
			//Similar problems might persist anywhere else where TimeTool is being used.
			for (TimeTool.DAYS wd : TimeTool.DAYS.values()) {
				//log.debug("getDisplayDays: Processing wd.toString() = {}",wd.toString());
				//log.debug("getDisplayDays: Processing wd.fullName = {}",wd.fullName.toString());
				String wdStrUc = wd.toString().toUpperCase();
				String wdFnStrUc = wd.fullName.toString().toUpperCase();
				if ((resources.indexOf(wdStrUc) != -1) 										
					|| (resources.indexOf(wdFnStrUc) != -1)) {
					//log.debug("getDisplayDays: adding {} to array ret... ",ttMonday.toString(TimeTool.DATE_COMPACT));
					ret.add(ttMonday.toString(TimeTool.DATE_COMPACT));
				}
				ttMonday.addDays(1);
			}

			log.debug("getDisplayDays: about to return ret.toArray(new String[0])...");
			return ret.toArray(new String[0]);
		}
	}
	
	private void makePrivateActions(){
		weekFwdAction = new Action(Messages.AgendaWeek_weekForward) {
			{
				setToolTipText(Messages.AgendaWeek_showNextWeek);
				setImageDescriptor(Images.IMG_NEXT.getImageDescriptor());
			}
			
			@Override
			public void run(){
				agenda.addDays(7);
				TimeTool tt = new TimeTool();
				for (String s : getDisplayedDays()) {
					tt.set(s);
					checkDay(null, tt);
				}
				refresh();
			}
		};
		
		weekBackAction = new Action(Messages.AgendaWeek_weekBackward) {
			{
				setToolTipText(Messages.AgendaWeek_showPreviousWeek);
				setImageDescriptor(Images.IMG_PREVIOUS.getImageDescriptor());
			}
			
			@Override
			public void run(){
				agenda.addDays(-7);
				TimeTool tt = new TimeTool();
				for (String s : getDisplayedDays()) {
					tt.set(s);
					checkDay(null, tt);
				}
				refresh();
			}
		};
		showCalendarAction = new Action(Messages.AgendaWeek_selectWeek) {
			{
				setToolTipText(Messages.AgendaWeek_showCalendarToSelect);
				// setImageDescriptor(Activator.getImageDescriptor("icons/calendar.png"));
			}
			
			@Override
			public void run(){
				DateSelectorDialog dsl =
					new DateSelectorDialog(getViewSite().getShell(), agenda.getActDate());
				if (dsl.open() == Dialog.OK) {
					agenda.setActDate(dsl.getSelectedDate());
					TimeTool tt = new TimeTool();
					for (String s : getDisplayedDays()) {
						tt.set(s);
						checkDay(null, tt);
					}
					
					refresh();
				}
			}
		};
		
		final IAction zoomAction = new Action(Messages.AgendaWeek_zoom, Action.AS_DROP_DOWN_MENU) {
			Menu mine;
			{
				setToolTipText(Messages.AgendaWeek_setZoomFactor);
				setImageDescriptor(Activator.getImageDescriptor("icons/zoom.png")); //$NON-NLS-1$
				setMenuCreator(new IMenuCreator() {
					
					public void dispose(){
						mine.dispose();
					}
					
					public Menu getMenu(Control parent){
						mine = new Menu(parent);
						fillMenu();
						return mine;
					}
					
					public Menu getMenu(Menu parent){
						mine = new Menu(parent);
						fillMenu();
						return mine;
					}
				});
			}
			
			private void fillMenu(){
				String currentFactorString =
					CoreHub.localCfg.get(PreferenceConstants.AG_PIXEL_PER_MINUTE, "0.4");
				int currentFactor = (int) (Float.parseFloat(currentFactorString) * 100);
				for (String s : new String[] {
					"40", "60", "80", "100", "120", //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$ //$NON-NLS-5$
					"140", "160", "200", "300"}) { //$NON-NLS-1$ //$NON-NLS-2$ //$NON-NLS-3$ //$NON-NLS-4$
					MenuItem it = new MenuItem(mine, SWT.RADIO);
					it.setText(s + "%"); //$NON-NLS-1$
					it.setData(s);
					it.setSelection(Integer.parseInt(s) == currentFactor);
					it.addSelectionListener(new SelectionAdapter() {
						
						@Override
						public void widgetSelected(SelectionEvent e){
							MenuItem mi = (MenuItem) e.getSource();
							int scale = Integer.parseInt(mi.getText().split("%")[0]); //$NON-NLS-1$
							double factor = scale / 100.0;
							CoreHub.localCfg.set(PreferenceConstants.AG_PIXEL_PER_MINUTE,
								Double.toString(factor));
							sheet.recalc();
						}
						
					});
				}
			}
		};
		IToolBarManager tmr = getViewSite().getActionBars().getToolBarManager();
		tmr.add(new Separator());
		tmr.add(weekBackAction);
		tmr.add(showCalendarAction);
		tmr.add(weekFwdAction);
		tmr.add(new Separator());
		tmr.add(zoomAction);
	}
	
}
