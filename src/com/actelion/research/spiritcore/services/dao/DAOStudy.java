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

package com.actelion.research.spiritcore.services.dao;

import java.text.DecimalFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Collection;
import java.util.Collections;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;

import javax.persistence.EntityManager;
import javax.persistence.EntityTransaction;
import javax.persistence.LockModeType;
import javax.persistence.Query;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.actelion.research.spiritcore.adapter.DBAdapter;
import com.actelion.research.spiritcore.business.RightLevel;
import com.actelion.research.spiritcore.business.biosample.Biosample;
import com.actelion.research.spiritcore.business.biosample.BiosampleQuery;
import com.actelion.research.spiritcore.business.biosample.Biotype;
import com.actelion.research.spiritcore.business.biosample.ContainerType;
import com.actelion.research.spiritcore.business.property.PropertyKey;
import com.actelion.research.spiritcore.business.result.Result;
import com.actelion.research.spiritcore.business.result.ResultQuery;
import com.actelion.research.spiritcore.business.result.Test;
import com.actelion.research.spiritcore.business.study.AttachedBiosample;
import com.actelion.research.spiritcore.business.study.Group;
import com.actelion.research.spiritcore.business.study.Measurement;
import com.actelion.research.spiritcore.business.study.NamedSampling;
import com.actelion.research.spiritcore.business.study.NamedTreatment;
import com.actelion.research.spiritcore.business.study.Phase;
import com.actelion.research.spiritcore.business.study.Randomization;
import com.actelion.research.spiritcore.business.study.Sampling;
import com.actelion.research.spiritcore.business.study.Study;
import com.actelion.research.spiritcore.business.study.StudyAction;
import com.actelion.research.spiritcore.business.study.StudyQuery;
import com.actelion.research.spiritcore.services.SpiritRights;
import com.actelion.research.spiritcore.services.SpiritUser;
import com.actelion.research.spiritcore.util.MiscUtils;
import com.actelion.research.spiritcore.util.Pair;
import com.actelion.research.spiritcore.util.QueryTokenizer;
import com.actelion.research.spiritcore.util.Triple;

/**
 * DAO functions linked to studies
 *
 * @author Joel Freyss
 */
@SuppressWarnings("unchecked")
public class DAOStudy {

	private static Logger logger = LoggerFactory.getLogger(DAOStudy.class);


	public static List<Study> getStudies() {
		EntityManager session = JPAUtil.getManager();
		List<Study> res = (List<Study>) Cache.getInstance().get("allstudies");
		if(res==null) {
			res = session.createQuery("from Study").getResultList();
			Collections.sort(res);
			Cache.getInstance().add("allstudies", res, Cache.LONG);
		}
		return res;
	}

	public static List<Study> getRecentStudies(SpiritUser user, RightLevel level) {
		String key = "studies_"+user+"_"+level+"_"+JPAUtil.getManager();
		List<Study> studies = (List<Study>) Cache.getInstance().get(key);

		//Make sure studies are in the same session, or reset the cache
		if(studies!=null && studies.size()>0 && !JPAUtil.getManager().contains(studies.get(0))) {
			studies = null;
		}

		//Load the studies
		if(studies==null) {
			EntityManager session = JPAUtil.getManager();
			Query query = session.createQuery("from Study s where s.creDate > ?1");
			Calendar cal = Calendar.getInstance();
			cal.setTime(JPAUtil.getCurrentDateFromDatabase());
			cal.add(Calendar.DAY_OF_YEAR, user==null? -365: -365*5);
			query.setParameter(1, cal.getTime());

			List<Study> res = query.getResultList();


			studies = new ArrayList<>();
			if(user!=null) {
				for (Study study : res) {
					if(level==RightLevel.ADMIN) {
						if(SpiritRights.canEdit(study, user)) studies.add(study);
					} else if(level==RightLevel.BLIND) {
						if(SpiritRights.isBlind(study, user) || SpiritRights.canEditBiosamples(study, user)) studies.add(study);
					} else if(level==RightLevel.WRITE) {
						if(SpiritRights.canEditBiosamples(study, user)) studies.add(study);
					} else if(level==RightLevel.READ) {
						if(SpiritRights.canRead(study, user)) studies.add(study);
					}
				}
			} else {
				studies.addAll(studies);
			}
			Collections.sort(studies);

			Cache.getInstance().add(key, studies, 120);
		}
		return studies;

	}

	protected static void postLoad(Collection<Study> studies) {
		if(studies==null) return;

		//2nd loading pass: load the tests from the serialized measurements
		Set<Integer> testIds = new HashSet<>();
		for (Study study : studies) {
			testIds.addAll(Measurement.getTestIds(study.getAllMeasurementsFromActions()));
			testIds.addAll(Measurement.getTestIds(study.getAllMeasurementsFromSamplings()));
		}

		Map<Integer, Test> id2test =  JPAUtil.mapIds(DAOTest.getTests(testIds));
		for (Study study : studies) {
			for(StudyAction a: study.getStudyActions()) {
				for(Measurement m: a.getMeasurements()) {
					m.setTest(id2test.get(m.getTestId()));
				}
			}
			for(NamedSampling ns: study.getNamedSamplings()) {
				for(Sampling s: ns.getAllSamplings()) {
					for(Measurement m: s.getMeasurements()) {
						m.setTest(id2test.get(m.getTestId()));
					}
				}
			}
		}
	}

	public static List<String> getMetadataValues(String metadata) {
		List<String> res = (List<String>) Cache.getInstance().get("study_"+metadata);
		if(res==null) {
			Set<String> set = new TreeSet<>();
			for (Study s : getStudies()) {
				if(s.getMetadata(metadata)!=null) {
					set.add(s.getMetadata(metadata));
				}
			}
			res = new ArrayList<>(set);
			Cache.getInstance().add("study_"+metadata, res, Cache.LONG);
		}
		return res;
	}

	public static Study getStudy(int id) {
		EntityManager session = JPAUtil.getManager();
		List<Study> res = session.createQuery("select s from Study s where s.id = ?1")
				.setParameter(1, id)
				.getResultList();
		Study s = res.size()==1? res.get(0): null;
		if(s!=null) postLoad(Collections.singleton(s));
		return s;
	}

	public static Study getStudyByStudyId(String studyId) {
		EntityManager session = JPAUtil.getManager();
		List<Study> res = session.createQuery("select s from Study s where s.studyId = ?1")
				.setParameter(1, studyId)
				.getResultList();
		Study s = res.size()==1? res.get(0): null;
		if(s!=null) postLoad(Collections.singleton(s));
		return s;
	}

	public static List<Study> getStudyByLocalIdOrStudyIds(String ids) {
		EntityManager session = JPAUtil.getManager();
		try {
			String sql = "select s from Study s where " + QueryTokenizer.expandOrQuery("s.localId = ? or s.studyId = ?", ids);
			List<Study> res = session.createQuery(sql).getResultList();
			postLoad(res);
			return res;
		} catch(Exception e) {
			throw new RuntimeException(e);
		}
	}

	public static List<ContainerType> getContainerTypes(Study study) {
		List<ContainerType> res = (List<ContainerType>) Cache.getInstance().get("study_containers_"+study);
		if(res==null) {
			EntityManager session = JPAUtil.getManager();
			res = session.createQuery("select distinct(b.container.containerType) from Biosample b where b.inheritedStudy = ?1 and b.container.containerType is not null")
					.setParameter(1, study)
					.getResultList();
			Collections.sort(res);
			Cache.getInstance().add("study_containers_"+study, res);
		}
		return res;
	}

	public static List<Biotype> getBiotypes(Study study){
		List<Biotype> res = (List<Biotype>) Cache.getInstance().get("study_biotypes_"+study);
		if(res==null) {
			EntityManager session = JPAUtil.getManager();
			res = session.createQuery("select distinct(b.biotype) from Biosample b where b.inheritedStudy = ?1 and b.biotype is not null")
					.setParameter(1, study)
					.getResultList();
			Collections.sort(res);
			Cache.getInstance().add("study_biotypes_"+study, res);
		}
		return res;
	}

	public static List<Study> queryStudies(StudyQuery q, SpiritUser user) throws Exception {
		assert q!=null;
		EntityManager session = JPAUtil.getManager();
		long s = System.currentTimeMillis();

		String jpql = "SELECT s FROM Study s where 1=1 ";
		StringBuilder clause = new StringBuilder();
		List<Object> parameters = new ArrayList<>();

		if(q.getStudyIds()!=null && q.getStudyIds().length()>0) {
			clause.append(" and (" + QueryTokenizer.expandOrQuery("s.studyId = ?", q.getStudyIds()) + ")");
		}

		if(q.getLocalIds()!=null && q.getLocalIds().length()>0) {
			clause.append(" and (" + QueryTokenizer.expandOrQuery("s.localId = ?", q.getLocalIds()) + ")");
		}

		if(q.getKeywords()!=null && q.getKeywords().length()>0) {
			String expr = "lower(s.studyId) like lower(?)" +
					" or lower(s.localId) like lower(?)" +
					" or lower(s.serializedMetadata) like lower(?)" +
					" or lower(s.title) like lower(?)" +
					" or lower(s.adminUsers) like lower(?)" +
					" or lower(s.expertUsers) like lower(?)" +
					" or lower(s.blindUsers) like lower(?)" +
					" or lower(s.state) like lower(?)" +
					" or lower(s.creUser) like lower(?)" +
					" or lower(s.updUser) like lower(?)" +
					" or s.id in (select nt.study.id from NamedTreatment nt where lower(nt.name) like lower(?) or lower(nt.compoundName) like lower(?) or lower(nt.compoundName2) like lower(?))";
			clause.append(" and (" + QueryTokenizer.expandQuery(expr, q.getKeywords(), true, true) + ")");
		}

		if(q.getState()!=null && q.getState().length()>0) {
			clause.append(" and s.state = ?");
			parameters.add(q.getState());
		}

		if(q.getType()!=null && q.getType().length()>0) {
			clause.append(" and s.type = ?");
			parameters.add(q.getType());
		}

		if(q.getMetadataMap().size()>0) {
			for (Map.Entry<String, String> e : q.getMetadataMap().entrySet()) {
				if(e.getValue().length()>0) {
					clause.append(" and s.serializedMetadata like ?");
					parameters.add("%" + e.getValue() + "%");
				}
			}
		}

		if(q.getUser()!=null && q.getUser().length()>0) {
			clause.append(" and (lower(s.adminUsers) like lower(?) or lower(s.expertUsers) like lower(?) or lower(s.creUser) like lower(?))");
			parameters.add("%"+q.getUser()+"%");
			parameters.add("%"+q.getUser()+"%");
			parameters.add("%"+q.getUser()+"%");
		}


		if(q.getUpdDays()!=null && q.getUpdDays().length()>0) {
			String digits = MiscUtils.extractStartDigits(q.getUpdDays());
			if(digits.length()>0) {
				try {
					clause.append(" and s.updDate > ?");
					Calendar cal = Calendar.getInstance();
					cal.setTime(new Date());
					cal.add(Calendar.DAY_OF_YEAR, -Integer.parseInt(digits));
					parameters.add(cal.getTime());
				} catch (Exception e) {
				}
			}
		}
		if(q.getCreDays()!=null && q.getCreDays().length()>0) {
			String digits = MiscUtils.extractStartDigits(q.getCreDays());
			if(digits.length()>0) {
				try {
					clause.append(" and s.creDate > ?");
					Calendar cal = Calendar.getInstance();
					cal.setTime(new Date());
					cal.add(Calendar.DAY_OF_YEAR, -Integer.parseInt(digits));
					parameters.add(cal.getTime());
				} catch (Exception e) {
				}
			}
		}
		if(q.getRecentStartDays()>0) {
			Calendar cal = Calendar.getInstance();
			cal.setTime(new Date());
			cal.add(Calendar.DAY_OF_YEAR, -q.getRecentStartDays());
			clause.append(" and s.startingDate > ?");
			parameters.add(cal.getTime());
		}

		if(clause.length()>0) jpql += clause;

		//Query the DB
		jpql = JPAUtil.makeQueryJPLCompatible(jpql);
		Query query = session.createQuery(jpql);
		for (int i = 0; i < parameters.size(); i++) {
			query.setParameter(1+i, parameters.get(i));
		}
		List<Study> studies = query.getResultList();

		//Post Filter according to metadata
		if(q.getMetadataMap().size()>0) {
			List<Study> filtered = new ArrayList<>();
			loop: for (Study study : studies) {
				for (Map.Entry<String, String> e : q.getMetadataMap().entrySet()) {
					if(e.getValue().length()>0 && !study.getMetadata(e.getKey()).toLowerCase().contains(e.getValue().toLowerCase())) continue loop;
				}
				filtered.add(study);
			}
			studies = filtered;
		}
		Collections.sort(studies, (o1,o2) -> -o1.getCreDate().compareTo(o2.getCreDate()));

		//Post Filter according to user rights
		if(user!=null) {
			for (Iterator<Study> iterator = studies.iterator(); iterator.hasNext();) {
				Study study = iterator.next();
				if(!SpiritRights.canRead(study, user)) iterator.remove();
			}
		}
		LoggerFactory.getLogger(DAOStudy.class).info("queryStudies() in "+(System.currentTimeMillis()-s)+"ms");
		return studies;
	}

	/**
	 * Persists the study
	 * @param study
	 * @param user
	 * @return
	 * @throws Exception
	 */
	public static List<Study> persistStudies(Collection<Study> studies, SpiritUser user) throws Exception {
		EntityManager session = JPAUtil.getManager();
		EntityTransaction txn = null;
		try {
			txn = session.getTransaction();
			txn.begin();

			List<Study> res = persistStudies(session, studies, user);

			txn.commit();
			txn = null;
			return res;
		} finally {
			if(txn!=null && txn.isActive()) txn.rollback();
		}

	}

	private static void testConcurrentModification(EntityManager session, Collection<Study> studies) throws Exception {
		for (Study study : studies) {
			if(study.getId()>0) {
				Object[] lastUpdate = (Object[]) session.createQuery("select s.updDate, s.updUser from Study s where s.id = "+study.getId()).getSingleResult();
				Date lastDate = (Date) lastUpdate[0];
				String lastUser = (String) lastUpdate[1];
				logger.info("last update of " + study + " was " + lastDate + " by " + lastUser + " attached object: "+study.getUpdDate());

				if(lastDate!=null && study.getUpdDate()!=null) {
					int diffSeconds = (int) (lastDate.getTime() - study.getUpdDate().getTime());
					if(diffSeconds>0) throw new Exception("The study "+study+" has just been updated by "+lastUser+" [" + diffSeconds + "seconds ago].\nYou cannot overwrite those changes unless you reopen the newest version.");
				}
			}
		}
	}

	/**
	 * Persists the study
	 * @param study
	 * @param user
	 * @return the persisted studies
	 * @throws Exception
	 */
	public static List<Study> persistStudies(EntityManager session, Collection<Study> studies, SpiritUser user) throws Exception {
		List<Study> res = new ArrayList<>();
		if(studies==null || studies.size()==0) return res;

		logger.info("Persist "+studies.size()+ " studies");
		assert user!=null;
		assert session!=null;
		assert session.getTransaction().isActive();


		//Test that nobody else modified the study
		testConcurrentModification(session, studies);

		//Check rights
		Date now = JPAUtil.getCurrentDateFromDatabase();
		for (Study study : studies) {
			//			if(!SpiritRights.canEdit(study, user) && !SpiritRights.canBlind(study, user)) throw new Exception("You are not allowed to edit this study");
			Study existing = DAOStudy.getStudyByStudyId(study.getStudyId());
			if(existing!=null && existing.getId()!=study.getId()) throw new Exception("The studyId "+study.getStudyId()+" is not unique");
		}

		//Update studies
		for (Study study : studies) {
			study.setUpdUser(user.getUsername());
			study.setUpdDate(now);


			if(study.getState()==null || study.getState().length()==0) {
				study.setState(SpiritProperties.getInstance().getValue(PropertyKey.STUDY_DEFAULTSTATE));
			}

			for (StudyAction a : new ArrayList<>(study.getStudyActions())) {
				if(a.isEmpty() || a.getSubGroup()<0 || a.getSubGroup()>=a.getGroup().getNSubgroups()) {
					a.remove();
				}
			}

			//Now save the study
			study.preSave();
			if(study.getId()>0) {
				if(!session.contains(study)) {
					study = session.merge(study);
					logger.info("Merge "+study);
				}
			} else {
				if(study.getStudyId()==null || study.getStudyId().length()==0) {
					study.setStudyId(getNextStudyId(session));
				}

				study.setCreUser(study.getUpdUser());
				study.setCreDate(study.getUpdDate());
				session.persist(study);
				logger.info("Persist "+study);
			}

			for(Phase phase: study.getPhases()) {
				phase.serializeRandomization();
			}
			res.add(study);
		}
		Cache.getInstance().remove("studies_"+user);
		Cache.getInstance().remove("allstudies");
		return res;
	}


	/**
	 * Delete the studies.
	 * If forceCascade is true, this will also delete all the related results/biosamples
	 * If forceCascade is false, this method will throw an exception if there are still results available
	 *
	 * @param studies
	 * @param forceCascade
	 * @param user
	 * @throws Exception
	 */
	public static void deleteStudies(Collection<Study> studies, boolean forceCascade, SpiritUser user) throws Exception {

		EntityManager session = JPAUtil.getManager();
		EntityTransaction txn = null;
		try {
			txn = session.getTransaction();
			txn.begin();

			deleteStudies(session, studies, forceCascade, user);

			txn.commit();
			txn = null;
		} finally {
			if(txn!=null && txn.isActive()) try{ txn.rollback();} catch(Exception e2) {e2.printStackTrace();}
		}
	}

	/**
	 * Delete the studies.
	 * If forceCascade is true, this will also delete all the related results/biosamples
	 * If forceCascade is false, this method will throw an exception if there are still results available
	 *
	 * @param session
	 * @param studies
	 * @param forceCascade
	 * @param user
	 * @throws Exception
	 */
	public static void deleteStudies(EntityManager session, Collection<Study> studies, boolean forceCascade, SpiritUser user) throws Exception {
		assert session!=null;
		assert session.getTransaction().isActive();
		for (Study study : studies) {

			if(!SpiritRights.canDelete(study, user)) throw new Exception("You are not allowed to delete the study");


			if(!session.contains(study)) {
				study = session.merge(study);
			}

			//Make sure that there are no attached results
			ResultQuery q = ResultQuery.createQueryForStudyIds(study.getStudyId());
			List<Result> l = DAOResult.queryResults(session, q, null);
			if(l.size()>0) {
				if(forceCascade) {
					DAOResult.deleteResults(session, l, user);
				} else {
					throw new Exception("You cannot delete a study if there are " + l.size() + " results linked to it");
				}
			}

			//Make sure that there are no attached animals
			BiosampleQuery q2 = BiosampleQuery.createQueryForStudyIds(study.getStudyId());
			List<Biosample> l2 = DAOBiosample.queryBiosamples(session, q2, null);
			if(l2.size()>0) {
				if(forceCascade) {
					DAOBiosample.deleteBiosamples(session, l2, user);
				} else {
					throw new Exception("You cannot delete this study because there are " + l2.size() +" biosamples linked to it");
				}
			}

			//Remove
			study.remove();
			session.remove(study);
			session.flush();
		}

		Cache.getInstance().remove("studies_"+user);
		Cache.getInstance().remove("allstudies");
	}

	public static String getNextStudyId() {
		return getNextStudyId(JPAUtil.getManager());
	}

	public static String getNextStudyId(EntityManager session) {

		String pattern = SpiritProperties.getInstance().getValue(PropertyKey.STUDY_STUDYID_PATTERN);
		String formattedPattern = DAOBarcode.formatPattern(pattern, null);
		int prefLength = formattedPattern.indexOf("#");
		int incrementLength = formattedPattern.lastIndexOf("#")-formattedPattern.indexOf("#")+1;
		int suffLength = formattedPattern.length()-formattedPattern.lastIndexOf("#")-1;

		String hql = "select max(s.studyId) from Study s where s.studyId like ('" +
				formattedPattern.substring(0, prefLength) + "%" + formattedPattern.substring(formattedPattern.length()-suffLength) + "')";
		String s = (String) session.createQuery(hql).getSingleResult();
		int lastNo;
		if(s==null) {
			lastNo = 0;
			return formattedPattern.substring(0, prefLength) + new DecimalFormat(MiscUtils.repeat("0", incrementLength)).format(1) + formattedPattern.substring(formattedPattern.length()-suffLength);
		} else {
			try {
				lastNo = Integer.parseInt(s.substring(prefLength, formattedPattern.length()-suffLength));
			} catch (Exception e) {
				throw new RuntimeException("Invalid Last studyNo: "+s+" for pattern:"+formattedPattern);
			}
		}
		return formattedPattern.substring(0, prefLength) + new DecimalFormat(MiscUtils.repeat("0", incrementLength)).format(lastNo+1) + formattedPattern.substring(formattedPattern.length()-suffLength);
	}

	public static void changeOwnership(Study study, SpiritUser toUser, SpiritUser updater) {
		if(study.getId()<=0) return;

		EntityManager session = JPAUtil.getManager();
		EntityTransaction txn = null;
		Date now = JPAUtil.getCurrentDateFromDatabase();

		try {
			txn = session.getTransaction();
			txn.begin();

			study.setUpdUser(updater.getUsername());
			study.setUpdDate(now);
			study.setCreUser(toUser.getUsername());
			session.merge(study);

			txn.commit();
			txn = null;
		} finally {
			if(txn!=null && txn.isActive()) try{txn.rollback();}catch (Exception e) {e.printStackTrace();}
		}
	}

	/**
	 * Loads and populate the animals from the study randomization
	 * @param s
	 */
	public static void loadBiosamplesFromStudyRandomization(Randomization randomization) {

		List<AttachedBiosample> samples = randomization.getSamples();

		List<String> sampleIds = new ArrayList<String>();
		for (AttachedBiosample sample : samples) {
			String sampleId = sample.getSampleId();
			sampleIds.add(sampleId);
		}
		Map<String, Biosample> biosamples = DAOBiosample.getBiosamplesBySampleIds(sampleIds);

		for (AttachedBiosample sample : samples) {
			String sampleId = sample.getSampleId();
			if(sampleId==null) continue;
			if(sample.getBiosample()!=null && sample.getBiosample().getSampleId().equals(sampleId)) continue;

			//Check first in Spirit
			Biosample b = biosamples.get(sampleId);
			if(b==null) System.err.println(b+" not found");
			if(b==null) {
				//Then in the externaDB
				b = new Biosample();
				b.setSampleId(sampleId);
				try {
					DBAdapter.getInstance().populateFromExternalDB(b);
				} catch (Exception e) {
					e.printStackTrace();
				}
			}
			sample.setBiosample(b);
		}
	}



	/**
	 * Resolve the conflicts of samples with the same sampleId by cloning them and deriving them from the same parent.
	 *
	 * Careful, the samples have to be saved in the proper order
	 *
	 * @param toClone
	 * @param conflicts
	 * @return the updated samples in the order to be saved
	 * @throws Exception
	 */
	public static List<Biosample> cloneParticipants(List<Biosample> toClone, List<Biosample> conflicts) throws Exception {
		assert toClone.size() == conflicts.size();

		List<Biosample> updated = new ArrayList<>();
		for (int i = 0; i < toClone.size(); i++) {
			Biosample b = toClone.get(i);
			Biosample conflict = conflicts.get(i);

			String id = b.getSampleId();


			Biosample parent;
			if(conflict.getAttachedStudy()==null) {
				//Solve the conflict by setting the parent to the conflicting sample
				// TOP -- conflict_A
				parent = conflict;
			} else if(conflict.getParent()==null) {
				//Solve the conflict by adding a common parent
				// TOP -- conflict_A
				//     \- b_B
				parent = conflict.clone();
				parent.setId(0);
				parent.setSampleId(id+"_");
				parent.setContainer(null);
				parent.setAttachedStudy(null);

				updated.add(parent);
				System.out.println("EditBiosampleDlg.validate() CREATE "+parent);

				updated.add(conflict);
				conflict.setParent(parent);
				conflict.setSampleId(parent.getNextCloneId());
				System.out.println("EditBiosampleDlg.validate() Change conflict to "+conflict+" from "+conflict.getParent());


			} else {
				//Solve the conflict by linking to the parent of the conflict
				// TOP -- confict_A
				//     \- b_B
				parent = conflict.getParent();
			}

			b.setParent(parent);
			b.setSampleId(parent.getNextCloneId());
			updated.add(b);
			System.out.println("EditBiosampleDlg.validate() Change sample to "+b+" from "+b.getParent());

			//			parent.setSampleId(id);

		}
		System.out.println("DAOStudy.cloneParticipants() "+updated);
		return updated;
	}

	/*
	public static List<Study> getRelatedStudies(List<Study> studies, SpiritUser user) {
		List<Study> res = new ArrayList<>();
		if(studies.size()==0) {
			return res;
		}
		res.addAll(getRelatedStudies(studies.get(0), user));
		for (int i = 1; i < studies.size(); i++) {
			List<Study> r = getRelatedStudies(studies.get(i), user);
			res.retainAll(r);
		}
		res.removeAll(studies);
		return res;
	}

	private static String tos(double[] a) {
		StringBuilder sb = new StringBuilder();
		for (int j = 0; j < a.length; j++) {
			sb.append((j>0?",":"") + Formatter.format2(a[j]));
		}
		return "["+sb.toString()+"]";
	}
	public static List<Study> getRelatedStudies(Study study, SpiritUser user) {
		List<Study> res = new ArrayList<Study>();
		double[] desc1 = getDescriptors(study);
		PriorityQueue<Study> queue = new PriorityQueue<Study>();
		int last1 = study.getLastPhase()==null? 0: study.getLastPhase().getDays();
		for(Study s: getStudies()) {
			if(s==study) continue;
			double score = 0;
			Set<String> compounds = new HashSet<String>(study.getCompounds());
			if(compounds.size()>0) {
				compounds.retainAll(s.getCompounds());
				score += compounds.size()>0?0: 1;
			} else if(s.getCompounds().size()>0) {
				score += .5;
			}
//			if(CompareUtils.compare(s.getProject(), study.getProject())!=0) {
//				score += 1;
//			}

			int last2 = s.getLastPhase()==null? 0:s.getLastPhase().getDays();
			score += 3.0 * Math.abs(last1-last2) / Math.max(1, (last1+last2)/2);



			double[] desc2 = getDescriptors(s);
			for (int i = 0; i < desc2.length; i++) {
//				score += Math.abs(desc1[i]-desc2[i]) * 4.0 /desc2.length;
				score += Math.min(1, Math.abs(desc1[i]-desc2[i]) / ((.2+desc1[i]+desc2[i])/2)) * 4.0 /desc2.length;
			}

			queue.add(s, score);
		}
		for(@SuppressWarnings("rawtypes") Elt e: queue.getList()) {
			if(res.size()>5) break;
			Study s = (Study) e.obj;
			System.out.println("# "+study+" - " + s+" > " + Formatter.format2(e.val) +" > "+ s.getCompounds() + " > " +tos(getDescriptors(s)));
			res.add(s);
		}

		return res;
	}
	public static double[] getDescriptors(Study s) {
		int nMeasurements = 0;
		int nWeighings = 0;
		int nTreatments = 0;
		int nSamplings = 0;
		int nValid = 0;
		int nTotal = 0;
		double maxMeasurements = 0;
		double maxWeighings = 0;
		double maxTreatments = 0;
		double maxSamplings = 0;
		int nGroupsWithRnd = 0;
		int nGroupsWithFrom = 0;
//		int nGroupsWithStrats = 0;
		int nGroups = 0;
		for(Group g: s.getGroups()) {
			nGroups++;
			if(g.getFromGroup()!=null) nGroupsWithFrom++;
			if(g.getFromPhase()!=null) nGroupsWithRnd++;
//			if(g.getSubgroupSize()>1) nGroupsWithStrats++;
			for(int i=0; i<g.getNSubgroups(); i++) {

				int measurements = 0;
				int weighings = 0;
				int treatments = 0;
				int samplings = 0;
				int valid = 0;
				int total = 0;
				boolean ended = false;
				for(Phase p: s.getPhases()) {
					total++;
					if(g.getEndPhase(i)==p) ended = true;
					if(!ended) valid++;
					StudyAction a = s.getStudyAction(g, p, i);
					if(a==null) continue;
					if(a.getNamedTreatment()!=null) treatments++;
					if(a.getNamedSampling1()!=null || a.getNamedSampling2()!=null) samplings++;
					if(a.hasMeasurements()) measurements++;
					if(a.isMeasureWeight()) weighings++;
				}
				if(total==0) total=1;
				maxTreatments = Math.max(maxTreatments, (double)treatments/total);
				maxSamplings = Math.max(maxSamplings, (double)samplings/total);
				maxMeasurements = Math.max(maxMeasurements, (double)measurements/total);
				maxWeighings = Math.max(maxWeighings, (double)weighings/total);
				nTotal+=total;

				nTreatments+=treatments;
				nSamplings+=samplings;
				nMeasurements+=measurements;
				nWeighings+=weighings;
				nValid+=valid;

			}
		}
		if(nTotal==0) nTotal=1;
		if(nGroups==0) nGroups=1;
		return new double[] {
				(double) nGroupsWithFrom/nGroups,
				(double) nGroupsWithRnd/nGroups,
				(double)nTreatments/nTotal,
				maxTreatments,
				(double)nMeasurements/nTotal,
				maxMeasurements,
				(double)nWeighings/nTotal,
				(double)nSamplings/nTotal,
				maxSamplings,
				maxWeighings,
				(double) nValid/nTotal};

	}

	 */


	public static Map<Biotype, Triple<Integer, String, Date>> countRecentSamplesByBiotype(Date minDate) {
		Map<Study, Map<Biotype, Triple<Integer, String, Date>>> res = countSamplesByStudyBiotype(null, minDate);
		if(res.size()>0) return res.values().iterator().next();
		return null;
	}

	public static Map<Study, Map<Biotype, Triple<Integer, String, Date>>> countSamplesByStudyBiotype(Collection<Study> studies) {
		return countSamplesByStudyBiotype(studies, null);
	}

	/**
	 * Return a map of study->(biotype->n.Samples)
	 * @param studies
	 * @return
	 */
	private static Map<Study, Map<Biotype, Triple<Integer, String, Date>>> countSamplesByStudyBiotype(Collection<Study> studies, Date minDate) {
		EntityManager session = JPAUtil.getManager();

		Map<Study, Map<Biotype, Triple<Integer, String, Date>>> res = new HashMap<>();
		Map<String, Map<String, Triple<Integer, String, Date>>> map = new HashMap<>();

		String query = studies==null?
				"select '', b.biotype.name, b.updUser, count(b), max(b.updDate) "
				+ " from Biosample b "
				+ " where b.inheritedStudy is null"
				+ (minDate!=null?" and b.updDate > ?1":"")
				+ " group by b.biotype.name, b.updUser":

					"select b.inheritedStudy.studyId, b.biotype.name, b.updUser, count(b), max(b.updDate) "
					+ " from Biosample b "
					+ " where " + QueryTokenizer.expandForIn("b.inheritedStudy.id", JPAUtil.getIds(studies))
					+ " group by b.inheritedStudy.studyId, b.biotype.name, b.updUser";

		Query q = session.createQuery(query);
		if(minDate!=null) q.setParameter(1, minDate);
		List<Object[]> results = q.getResultList();

		for (Object[] strings : results) {
			String sid = (String)strings[0];
			String key = (String)strings[1];
			int n = Integer.valueOf(""+strings[3]);
			String user = (String) strings[2];
			Date date = (Date) strings[4];

			Map<String, Triple<Integer, String, Date>> m = map.get(sid);
			if(m==null) {
				m = new HashMap<>();
				map.put(sid, m);
			}
			if(m.get(key)!=null) {
				Triple<Integer, String, Date> e = m.get(key);
				m.put(key, new Triple<Integer, String, Date>(e.getFirst() + n, e.getThird().after(date)? e.getSecond(): user, e.getThird().after(date)? e.getThird(): date));
			} else {
				m.put(key, new Triple<Integer, String, Date>(n, user, date));
			}
		}


		//Convert map to the underlying type
		Map<String, Study> mapStudy = Study.mapStudyId(studies);
		for (String n1 : map.keySet()) {
			Map<Biotype, Triple<Integer, String, Date>> m2 = new TreeMap<>();
			for (String n2 : map.get(n1).keySet()) {
				m2.put(DAOBiotype.getBiotype(n2), map.get(n1).get(n2));
			}
			res.put(mapStudy.get(n1), m2);
		}

		return res;
	}

	/**
	 * Return a map of studyId->(testName->n.Samples)
	 * @param studies
	 * @return
	 */
	public static Map<Study, Map<Test, Triple<Integer, String, Date>>> countResultsByStudyTest(Collection<Study> studies) {
		assert studies!=null;

		EntityManager session = JPAUtil.getManager();

		Map<Study, Map<Test, Triple<Integer, String, Date>>> res = new HashMap<>();
		Map<String, Map<String, Triple<Integer, String, Date>>> map = new HashMap<>();

		String query = "select r.biosample.inheritedStudy.studyId, r.test.name, r.updUser, count(r), max(r.updDate) "
				+ " from Result r "
				+ " where " + QueryTokenizer.expandForIn("r.biosample.inheritedStudy.id", JPAUtil.getIds(studies))
				+ " group by r.biosample.inheritedStudy.studyId, r.test.name, r.updUser";

		List<Object[]> results = session.createQuery(query).getResultList();

		for (Object[] strings : results) {
			String sid = (String)strings[0];
			String key = (String)strings[1];
			int n = Integer.valueOf(""+strings[3]);
			String user = (String) strings[2];
			Date date = (Date) strings[4];


			Map<String, Triple<Integer, String, Date>> m = map.get(sid);
			if(m==null) {
				m = new HashMap<>();
				map.put(sid, m);
			}
			if(m.get(key)!=null) {
				Triple<Integer, String, Date> e = m.get(key);
				m.put(key, new Triple<Integer, String, Date>(e.getFirst() + n, e.getThird().after(date)? e.getSecond(): user, e.getThird().after(date)? e.getThird(): date));
			} else {
				m.put(key, new Triple<Integer, String, Date>(n, user, date));
			}
		}

		//Convert map to the underlying type
		Map<String, Study> mapStudy = Study.mapStudyId(studies);
		for (String n1 : map.keySet()) {
			Map<Test, Triple<Integer, String, Date>> m2 = new TreeMap<Test, Triple<Integer, String, Date>>();
			for (String n2 : map.get(n1).keySet()) {
				Test t = DAOTest.getTest(n2);
				if(t!=null && map.get(n1).get(n2)!=null) {
					m2.put(t, map.get(n1).get(n2));
				} else {
					System.err.println("Test "+n2+" not found");
				}
			}
			res.put(mapStudy.get(n1), m2);
		}

		return res;
	}


	public static Map<Test, Integer> countResults(Collection<Study> studies, Biotype biotype) {

		EntityManager session = JPAUtil.getManager();

		Map<Test, Integer> res = new TreeMap<>();
		List<Object[]> results;
		String sql = "select r.test.id, count(r) from Result r"
				+ " where 1=1"
				+ (studies==null || studies.size()==0? "": " and " + QueryTokenizer.expandForIn("r.biosample.inheritedStudy.id", JPAUtil.getIds(studies)))
				+ (biotype==null? "": " and " + "r.biosample.biotype.id = "+biotype.getId())
				+ " group by r.test.id";
		results = session.createQuery(sql).getResultList();

		for (Object[] strings : results) {
			Test t = DAOTest.getTest(Integer.parseInt(""+strings[0]));
			res.put(t, Integer.parseInt(""+strings[1]));
		}
		return res;
	}

	public static Map<Phase, Pair<Integer, Integer>> countBiosampleAndResultsByPhase(Study study) {

		EntityManager session = JPAUtil.getManager();

		Map<Phase, Pair<Integer, Integer>> res = new TreeMap<>();
		List<Object[]> results;
		String sql = "select p, (select count(*) from Biosample b where b.inheritedPhase = p), (select count(*) from Result r where r.phase = p)"
				+ " from Phase p where p.study.id = " + study.getId();
		results = session.createQuery(sql).getResultList();

		for (Object[] o : results) {
			res.put((Phase)o[0], new Pair<Integer, Integer>( ((Long) o[1]).intValue(), ((Long)o[2]).intValue()));
		}
		return res;
	}

	public static List<Study> getRecentChanges(int days) throws Exception {
		Calendar cal = Calendar.getInstance();
		cal.setTime(JPAUtil.getCurrentDateFromDatabase());
		cal.set(Calendar.HOUR_OF_DAY, 0);
		cal.set(Calendar.DAY_OF_YEAR, cal.get(Calendar.DAY_OF_YEAR)-days);
		Date date = cal.getTime();
		return getRecentChanges(date);
	}

	public static List<Study> getRecentChanges(Date d) throws Exception {
		EntityManager em = JPAUtil.getManager();
		Set<Study> list1 = new HashSet<>();
		list1.addAll(em.createQuery("select s from Study s where s.updDate > ?1 ").setParameter(1, d).setLockMode(LockModeType.NONE).getResultList());
		list1.addAll(em.createQuery("select b.inheritedStudy from Biosample b where b.updDate > ?1 and b.inheritedStudy is not null").setParameter(1, d).setLockMode(LockModeType.NONE).getResultList());
		list1.addAll(em.createQuery("select r.biosample.inheritedStudy from Result r where r.updDate > ?1 and r.biosample.inheritedStudy is not null").setLockMode(LockModeType.NONE).setParameter(1, d).getResultList());

		List<Study> res = new ArrayList<>(list1);
		Collections.sort(res);
		return res;
	}

	/**
	 * Fully load a study for the display (attached, sampling, treatment, actions)
	 * @param study
	 */
	@SuppressWarnings("unused")
	public static void fullLoad(Study study) {
		if(study!=null) {
			for(Biosample b: study.getParticipants()) {

			}
			for(Group g: study.getGroups()) {
				g.getFromGroup();
				g.getFromPhase();
			}

			for(Phase p: study.getPhases()) {
				p.getRandomization();
			}

			for(NamedTreatment ns: study.getNamedTreatments()) {}
			for(NamedSampling ns: study.getNamedSamplings()) {}
			for(StudyAction a: study.getStudyActions()) {}
		}
	}

}



