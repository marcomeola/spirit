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

package com.actelion.research.spiritapp.ui.location;

import java.awt.Dimension;
import java.util.Collection;
import java.util.List;

import javax.swing.BorderFactory;
import javax.swing.Box;
import javax.swing.JButton;
import javax.swing.JScrollPane;

import com.actelion.research.spiritapp.Spirit;
import com.actelion.research.spiritapp.ui.location.ContainerTableModel.ContainerTableModelType;
import com.actelion.research.spiritapp.ui.util.HelpBinder;
import com.actelion.research.spiritapp.ui.util.SpiritChangeListener;
import com.actelion.research.spiritapp.ui.util.SpiritChangeType;
import com.actelion.research.spiritapp.ui.util.component.JSpiritEscapeDialog;
import com.actelion.research.spiritcore.business.biosample.Biosample;
import com.actelion.research.spiritcore.business.biosample.Container;
import com.actelion.research.spiritcore.services.SpiritUser;
import com.actelion.research.spiritcore.services.dao.DAOBiosample;
import com.actelion.research.spiritcore.services.dao.JPAUtil;
import com.actelion.research.util.ui.FastFont;
import com.actelion.research.util.ui.JCustomLabel;
import com.actelion.research.util.ui.JExceptionDialog;
import com.actelion.research.util.ui.UIUtils;

public class CheckoutDlg extends JSpiritEscapeDialog {

	private List<Container> containers;

	public CheckoutDlg(Collection<Biosample> mySamples) {
		super(UIUtils.getMainFrame(), "Checkout Dialog", CheckoutDlg.class.getName());
		if(mySamples.size()==0) return;

		List<Biosample> biosamples = JPAUtil.reattach(mySamples);
		this.containers = Biosample.getContainers(biosamples, true);
		ContainerTable table = new ContainerTable(ContainerTableModelType.EXPANDED);
		table.setRows(this.containers);

		JScrollPane sp = new JScrollPane(table);
		sp.setPreferredSize(new Dimension(800, 500));

		JButton cancelButton = new JButton("Cancel");
		cancelButton.addActionListener(e-> {
			dispose();
		});

		JButton okButton = new JButton("Checkout");
		okButton.addActionListener(e-> {
			eventOk();
		});

		setContentPane(UIUtils.createBox(sp,
				UIUtils.createBox(BorderFactory.createEmptyBorder(5, 5, 5, 5), new JCustomLabel("Do you want to Checkout those containers (ie. set the location to none)?", FastFont.BIGGER)),
				UIUtils.createHorizontalBox(HelpBinder.createHelpButton(), Box.createHorizontalGlue(), okButton, cancelButton)));
		getRootPane().setDefaultButton(cancelButton);


		UIUtils.adaptSize(this, 1024, 450);
		setVisible(true);
	}

	public void eventOk() {
		try {
			SpiritUser user = Spirit.askForAuthentication();

			List<Biosample> biosamples = Container.getBiosamples(containers);
			for (Biosample b : biosamples) {
				b.setLocPos(null, -1);
			}
			DAOBiosample.persistBiosamples(biosamples, user);

			dispose();
			SpiritChangeListener.fireModelChanged(SpiritChangeType.MODEL_UPDATED, Biosample.class, biosamples);
		} catch (Exception e) {
			JExceptionDialog.showError(e);
		}
	}


}
