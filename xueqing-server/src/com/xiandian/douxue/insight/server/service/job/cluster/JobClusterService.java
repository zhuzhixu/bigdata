/*
 * Copyright (c) 2017, 1DAOYUN and/or its affiliates. All rights reserved.
 * 1DAOYUN PROPRIETARY/CONFIDENTIAL. Use is subject to license terms.
 */
package com.xiandian.douxue.insight.server.service.job.cluster;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.concurrent.atomic.AtomicBoolean;

import org.json.JSONArray;
import org.json.JSONObject;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.mongodb.MongoClient;
import com.sun.corba.se.spi.activation.ORBPortInfoListHelper;
import com.xiandian.douxue.insight.server.base.Server;
import com.xiandian.douxue.insight.server.base.Service;
import com.xiandian.douxue.insight.server.base.ServiceJob;
import com.xiandian.douxue.insight.server.base.ServiceState;
import com.xiandian.douxue.insight.server.dao.HbaseStatistics;
import com.xiandian.douxue.insight.server.dao.MongoDBStorage;
import com.xiandian.douxue.insight.server.utils.ReadFile;
import com.xiandian.douxue.insight.server.utils.UtilTools;

import shapeless.newtype;

/**
 * 岗位聚类。岗位通过爬取，取标签，然后在分感知数据，最后进行岗位距离。
 * 
 * @since v1.0
 * @date 20170815
 * @author XianDian Cloud Team
 */
public class JobClusterService implements Service {
	private Properties cronProperties = UtilTools
			.getConfig(System.getProperty("user.dir") + "/configuration/service_cron.properties");
	private Server server;
	private MongoClient mongoClient;
	private Service parent;

	private static MongoDBStorage mongodbstorage = MongoDBStorage.getInstance();

	private String SERVICE_NAME = "douxue/insight/job/cluster";
	private Logger logger = LoggerFactory.getLogger(getClass());

	// 记录任务是否完成
	private AtomicBoolean isTaskDone = new AtomicBoolean();
	private int[] clusterCount = { 3};

	/**
	 * 岗位聚类服务。
	 * 
	 * @param server
	 * @param service
	 */
	public JobClusterService(Server server, Service service) {
		this.server = server;
		this.parent = service;
		init();
	}

	@Override
	public Server getServer() {
		return server;
	}

	@Override
	public String getServiceName() {
		return SERVICE_NAME;
	}

	@Override
	public ServiceState init() {
		isTaskDone.set(true);
		return ServiceState.STATE_INITIALIZED;

	}

	@Override
	public ServiceState start() {
		String isStop = cronProperties.getProperty("jobcluster_service");
		if (isStop.equals("stop")) {
			logger.info("外部User控制不启动服务" + SERVICE_NAME);
			return ServiceState.STATE_NOTSTART;
		}
		// 本次强制执
		ServiceJob.submitOnceJob(server, SERVICE_NAME + "/start", this.parent.getServiceName(), this);

		String cron = cronProperties.getProperty("jobcluster_cron");
		logger.info(this.SERVICE_NAME + cron);
		ServiceJob.submitCronJob(server, SERVICE_NAME, this.parent.getServiceName(), this, cron);
		return ServiceState.STATE_STARTED;
	}

	/**
	 * 具体的实现。
	 */
	public ServiceState process() {/////
		try {
			isTaskDone.set(false);
			List<Map<String, Object>> oList = new ArrayList<Map<String,Object>>();
			mongoClient = mongodbstorage.setUp();
			JobCluster jobCluster = new JobCluster();
			String config = ReadFile.ReadFile(System.getProperty("user.dir")+"/configuration/job_industry_skills_config.json");
			JSONObject object = new JSONObject(config);
			JSONArray array = object.getJSONArray("industry");
			for(int i = 0 ; i < array.length(); i++) {
				Map<String, Object> map = new HashMap<String, Object>();
				JSONObject object2 = array.getJSONObject(i);
				String nameString = object2.getString("industry-id");
				String[] industryStrings = nameString.split("_");
				map.put("industry-id", industryStrings[0]);
				
				String category = object2.getString("category");
				String[] categoryS = category.split(",");
				List<String> list = new ArrayList();
				for(String string : categoryS) {
					list.add(string);
				}
				map.put("categorys", list);
				
				String skill = object2.getString("skills");
				String[] skills = skill.split(",");
				List<String> list1 = new ArrayList();
				for(String string : skills) {
					list1.add(string);
				}
				map.put("skills", list1);
				oList.add(map);
			}
			
			for (Map<String, Object> map : oList) {
				String industryString = (String)map.get("industry-id");
				for (String categotyString: (List<String>)map.get("categorys")) {
					for (int i = 0; i < clusterCount.length; i++) {
						jobCluster.cluster(industryString, categotyString, (List<String>)map.get("skills"), clusterCount[i], mongoClient);
					}
				}
			}
			new HbaseStatistics().doDataStatistics();
			
		} catch (Exception e) {
			e.printStackTrace();
		}finally {
			isTaskDone.set(true);
		}
		return ServiceState.STATE_RUNNING;
	}

	@Override
	public boolean isDone() {
		return isTaskDone.get();
	}

	@Override
	public ServiceState getState() {

		return null;
	}

}
