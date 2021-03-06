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

package com.actelion.research.spiritcore.services;

import java.io.Serializable;
import java.util.Collections;
import java.util.HashSet;
import java.util.Set;

import com.actelion.research.spiritcore.business.employee.Employee;
import com.actelion.research.spiritcore.business.employee.EmployeeGroup;
import com.actelion.research.spiritcore.util.MiscUtils;

/**
 * User logged in Spirit
 * @author freyssj
 */
public class SpiritUser implements Serializable {

	public static String ROLE_ADMIN = "admin";

	private String username;

	private Set<String> managedUsers = new HashSet<>();

	private Set<String> roles = new HashSet<>();

	private EmployeeGroup mainGroup;
	private Set<EmployeeGroup> groups = new HashSet<>();

	public SpiritUser(String username) {
		this.username = username;
		this.managedUsers.add(username);
	}

	public SpiritUser(Employee emp) {
		if(emp==null) throw new IllegalArgumentException("employee cannot be null");
		this.username = emp.getUserName();
		this.groups.addAll(emp.getEmployeeGroups());
		this.mainGroup = emp.getMainEmployeeGroup();
		roles = emp.getRoles();
		populateManagedUsersRec(emp, this.managedUsers);
	}

	public static SpiritUser getFakeAdmin() {
		SpiritUser user = new SpiritUser("######");
		user.setRole(ROLE_ADMIN);
		return user;
	}

	private void populateManagedUsersRec(Employee root, Set<String> res) {
		if(res.contains(root.getUserName())) return; //avoid loops;
		res.add(root.getUserName());
		for (Employee emp : root.getChildren()) {
			populateManagedUsersRec(emp, res);
		}
	}

	public String getUsername() {
		return username;
	}
	public void setUsername(String username) {
		this.username = username.toUpperCase();
	}

	/**
	 * Sets a unique role to the user
	 * @param role
	 */
	public void setRole(String role) {
		roles.clear();
		roles.add(role);
	}

	public void setRole(String role, boolean set) {
		if(set) {
			roles.add(role);
		} else {
			roles.remove(role);
		}
	}

	public boolean isRole(String role) {
		return roles.contains(role);
	}

	public Set<String> getRoles() {
		return Collections.unmodifiableSet(roles);
	}

	public String getRolesString() {
		return MiscUtils.flatten(roles, ", ");
	}
	//
	/**
	 * Returns true if the user is member of the given group, <strikethrough> or any of the super group. </strikethrough>
	 * <pre>
	 * 			GroupA        <- User1
	 *        /       \
	 *       /         \
	 *   GroupB       GroupC  <- User2
	 *
	 *
	 *  User1 and User2 are both member of GroupA
	 *
	 * </pre>
	 * @param gr
	 * @return
	 */
	public boolean isMember(EmployeeGroup gr) {
		return getGroups().contains(gr);
	}

	public boolean isSuperAdmin() {
		return isRole(ROLE_ADMIN);
	}

	@Override
	public String toString() {
		return username;
	}

	/**
	 * Returns all the groups of this user
	 * @return
	 */
	public Set<EmployeeGroup> getGroups() {
		return groups;
	}

	/**
	 * Returns the logged in group
	 * @return
	 */
	public EmployeeGroup getMainGroup() {
		return mainGroup;
	}

	public void setMainGroup(EmployeeGroup mainGroup) {
		if(mainGroup!=null && !groups.contains(mainGroup)) groups.add(mainGroup);
		this.mainGroup = mainGroup;
	}

	@Override
	public boolean equals(Object obj) {
		if(!(obj instanceof SpiritUser)) return false;
		return getUsername().equals(((SpiritUser)obj).getUsername());
	}

	/**
	 * Returns all users down the hierarchy (including itself)
	 * @return
	 */
	public Set<String> getManagedUsers() {
		return managedUsers;
	}
}
