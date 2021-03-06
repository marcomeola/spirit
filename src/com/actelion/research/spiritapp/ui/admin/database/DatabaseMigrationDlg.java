/*
 * Spirit, a study/biosample management tool for research.
 * Copyright (C) 2018 Idorsia Pharmaceuticals Ltd., Hegenheimermattweg 91,
 * CH-4123 Allschwil, Switzerland.
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>
 *
 * @author Joel Freyss
 */

package com.actelion.research.spiritapp.ui.admin.database;

import java.awt.Color;
import java.awt.Dimension;

import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JDialog;
import javax.swing.JEditorPane;
import javax.swing.JFrame;
import javax.swing.JLabel;
import javax.swing.JOptionPane;
import javax.swing.JScrollPane;
import javax.swing.JTextArea;
import javax.swing.text.SimpleAttributeSet;
import javax.swing.text.StyleConstants;

import org.slf4j.LoggerFactory;

import com.actelion.research.spiritapp.ui.util.component.JHeaderLabel;
import com.actelion.research.spiritapp.ui.util.editor.ImageEditorPane;
import com.actelion.research.spiritcore.adapter.DBAdapter;
import com.actelion.research.spiritcore.services.dao.JPAUtil;
import com.actelion.research.spiritcore.services.dao.SpiritProperties;
import com.actelion.research.spiritcore.services.migration.MigrationScript;
import com.actelion.research.spiritcore.services.migration.MigrationScript.ILogger;
import com.actelion.research.util.ui.JExceptionDialog;
import com.actelion.research.util.ui.SwingWorkerExtended;
import com.actelion.research.util.ui.UIUtils;

/**
 * Class responsible for setting/testing the connection (ie. dbadapter.initAdapter(true) should be successful)
 *
 * @author freyssj
 *
 */
public class DatabaseMigrationDlg extends JDialog {

	private boolean hasErrors = false;
	private final JTextArea sqlPane = new JTextArea();
	private final JEditorPane errorPane = new ImageEditorPane();

	public DatabaseMigrationDlg() {
		super(UIUtils.getMainFrame(), "Spirit - DB Migration", true);

		sqlPane.setEditable(false);
		sqlPane.setCaretPosition(0);
		JScrollPane sp1 = new JScrollPane(sqlPane);
		sp1.setPreferredSize(new Dimension(750, 300));

		errorPane.setEditable(false);
		errorPane.setCaretPosition(0);
		JScrollPane sp2 = new JScrollPane(errorPane);
		sp2.setPreferredSize(new Dimension(750, 400));

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e-> {
			dispose();
		});

		JButton updateButton = new JButton("Let SPIRIT update the BD");
		updateButton.addActionListener(e-> {
			updateDB();
		});
		getRootPane().setDefaultButton(updateButton);

		//Init script
		try {
			String script = MigrationScript.getSql(DBAdapter.getInstance().getVendor());
			if(script.length()==0) {
				//Assume this is correct and update the version
			}
			sqlPane.setText(script);
		} catch(Exception e) {
			errorPane.setText(e.getMessage());
		}
		String version;
		try {
			version = MigrationScript.getDBVersion();
		} catch(Exception e) {
			version = null;
		}

		add(UIUtils.createBox(UIUtils.createBox(sp2, sp1),
				UIUtils.createVerticalBox(
						new JHeaderLabel("Your database is not up to date"),
						new JLabel("<html><div style='font-size:105%;color:red;padding:10px'>You must update the DB schema from version " + (version==null?"NA":version) + " to " + MigrationScript.getExpectedDBVersion() + ".<br>"
								+ "To continue you must commit the following code on the DB.<br>"
								+ "You can either do it manually or let SPIRIT do it for you.</div> ")),
				UIUtils.createHorizontalBox(Box.createHorizontalGlue(), cancelButton, updateButton)));
		pack();
		setLocationRelativeTo(null);
		setDefaultCloseOperation(JFrame.DISPOSE_ON_CLOSE);
		setVisible(true);

	}

	private void updateDB() {

		hasErrors = false;
		final ILogger logger = new ILogger() {
			@Override
			public void info(String sql, String msg) {
				try {

					SimpleAttributeSet att1 = new SimpleAttributeSet();
					StyleConstants.setFontSize(att1, 11);
					SimpleAttributeSet att2 = new SimpleAttributeSet();
					StyleConstants.setForeground(att2, UIUtils.getColor(0, 128, 0));

					javax.swing.text.Document doc = errorPane.getDocument();
					doc.insertString(doc.getLength(), sql.replaceAll("\n", " ")+"\n", att1);
					doc.insertString(doc.getLength(), " > " + msg.replaceAll("\n", " ")+"\n", att2);

					errorPane.setCaretPosition(doc.getLength());
				} catch(Exception e2) {
					e2.printStackTrace();
				}
			}
			@Override
			public void error(String sql, Exception e) {
				try {

					hasErrors = true;

					SimpleAttributeSet att1 = new SimpleAttributeSet();
					StyleConstants.setFontSize(att1, 11);
					SimpleAttributeSet att2 = new SimpleAttributeSet();
					StyleConstants.setForeground(att2, Color.RED);

					javax.swing.text.Document doc = errorPane.getDocument();
					doc.insertString(doc.getLength(), sql.replaceAll("\n", " ")+"\n", att1);
					doc.insertString(doc.getLength(), " > " + (e.getMessage()==null? e.toString(): e.getMessage().replaceAll("\n", " "))+"\n", att2);

					errorPane.setCaretPosition(doc.getLength());
				} catch(Exception e2) {
					e2.printStackTrace();
				}
			}
		};

		//Update DB in the backgrounf,
		new SwingWorkerExtended(errorPane, SwingWorkerExtended.FLAG_ASYNCHRONOUS20MS) {
			@Override
			protected void doInBackground() throws Exception {
				errorPane.setText("<html><div style='white-space:nowrap'>");
				MigrationScript.updateDB(DBAdapter.getInstance().getVendor(), logger);
			}

			@Override
			protected void done() {
				try {
					boolean ok = testSchema(DBAdapter.getInstance());
					if(ok) {
						if(hasErrors) {
							JExceptionDialog.showWarning(DatabaseMigrationDlg.this, "The Spirit database was successfully migrated.\n There were however some migration errors, please check what went wrong.");
						} else {
							JExceptionDialog.showInfo(DatabaseMigrationDlg.this, "The Spirit database was successfully migrated.");
							dispose();
						}
					} else {
						JExceptionDialog.showError(DatabaseMigrationDlg.this, "The Spirit database could not be migrated. Please check the errors.");
					}
				} catch(Exception e) {
					JExceptionDialog.showError(DatabaseMigrationDlg.this, e);
				}
			}
		};
	}

	/**
	 * Tests the schema and returns true if the schema is as expected.
	 * Returns false if the schema does not match (this function may suggest automatic update, not advised in normal cases).
	 * Throws an exception for DB connection errors
	 *
	 * @param adapter
	 * @return
	 * @throws Exception
	 */
	public static boolean testSchema(DBAdapter adapter) throws Exception {
		boolean ok;
		try {
			JPAUtil.closeFactory();
			LoggerFactory.getLogger(DatabaseMigrationDlg.class).debug("Test Schema: preinit "+adapter.getClass());
			adapter.preInit();
			LoggerFactory.getLogger(DatabaseMigrationDlg.class).debug("Test Schema: validate "+adapter.getClass());
			adapter.validate();
			ok = true;
		} catch(Exception e) {
			e.printStackTrace();
			int res = JOptionPane.showConfirmDialog(null, "There were some errors (" + e.getCause() + ").\n\nDo you want Spirit to fix the errors?", "Error", JOptionPane.YES_NO_OPTION, JOptionPane.WARNING_MESSAGE);
			if(res==JOptionPane.YES_OPTION) {
				JPAUtil.initFactory(adapter, "update");
				ok = true;

				if(adapter==DBAdapter.getInstance() && MigrationScript.getExpectedDBVersion().compareTo(MigrationScript.getDBVersion())!=0) {
					LoggerFactory.getLogger(DatabaseMigrationDlg.class).debug("Test Schema: set version to "+MigrationScript.getExpectedDBVersion());
					SpiritProperties.getInstance().setDBVersion(MigrationScript.getExpectedDBVersion());
					SpiritProperties.getInstance().saveValues();
				}

			} else {
				ok = false;
			}
		}

		return ok;
	}

	public static void main(String[] args) {
		new DatabaseMigrationDlg();
	}
}
