package com.alibaba.easyretry.mybatis;

import com.alibaba.easyretry.common.RetryConfiguration;
import com.alibaba.easyretry.common.RetryContainer;
import com.alibaba.easyretry.common.RetryExecutor;
import com.alibaba.easyretry.common.access.RetrySerializerAccess;
import com.alibaba.easyretry.common.access.RetryStrategyAccess;
import com.alibaba.easyretry.common.access.RetryTaskAccess;
import com.alibaba.easyretry.common.event.RetryEventMulticaster;
import com.alibaba.easyretry.common.filter.RetryFilterDiscover;
import com.alibaba.easyretry.common.filter.RetryFilterInvocation;
import com.alibaba.easyretry.common.filter.RetryFilterInvocationHandler;
import com.alibaba.easyretry.common.filter.RetryFilterRegister;
import com.alibaba.easyretry.common.filter.RetryFilterRegisterHandler;
import com.alibaba.easyretry.common.resolve.ExecutorSolver;
import com.alibaba.easyretry.common.serializer.ResultPredicateSerializer;
import com.alibaba.easyretry.common.strategy.StopStrategy;
import com.alibaba.easyretry.common.strategy.WaitStrategy;
import com.alibaba.easyretry.core.PersistenceRetryExecutor;
import com.alibaba.easyretry.core.access.DefaultRetrySerializerAccess;
import com.alibaba.easyretry.core.container.SimpleRetryContainer;
import com.alibaba.easyretry.core.event.SimpleRetryEventMulticaster;
import com.alibaba.easyretry.core.filter.DefaultRetryFilterInvocationHandler;
import com.alibaba.easyretry.core.filter.DefaultRetryFilterRegisterHandler;
import com.alibaba.easyretry.core.filter.SimpleRetryFilterRegister;
import com.alibaba.easyretry.core.serializer.HessianResultPredicateSerializer;
import com.alibaba.easyretry.core.strategy.DefaultRetryStrategy;
import com.alibaba.easyretry.extension.mybatis.access.MybatisRetryTaskAccess;
import com.alibaba.easyretry.extension.mybatis.dao.RetryTaskDAO;
import com.alibaba.easyretry.extension.mybatis.dao.RetryTaskDAOImpl;
import com.alibaba.easyretry.extension.spring.RetryListenerInitialize;
import com.alibaba.easyretry.extension.spring.SpringEventApplicationListener;
import com.alibaba.easyretry.extension.spring.SpringRetryFilterDiscover;
import com.alibaba.easyretry.extension.spring.aop.RetryInterceptor;
import com.alibaba.easyretry.mybatis.conifg.EasyRetryMybatisProperties;

import javax.sql.DataSource;
import lombok.extern.slf4j.Slf4j;
import org.apache.ibatis.session.SqlSessionFactory;
import org.mybatis.spring.SqlSessionFactoryBean;
import org.springframework.beans.BeansException;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.ApplicationContext;
import org.springframework.context.ApplicationContextAware;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;

/**
 * @author Created by wuhao on 2021/2/19.
 */
@Configuration
@Slf4j
@EnableConfigurationProperties(EasyRetryMybatisProperties.class)
@ConditionalOnProperty(name = "spring.easyretry.mybatis.enabled", matchIfMissing = true)
public class MybatisAutoConfiguration implements ApplicationContextAware{

	private ApplicationContext applicationContext;

	@Autowired
	private EasyRetryMybatisProperties easyRetryMybatisProperties;

	@Value("classpath:/dal/easyretry/easy-mybatis-config.xml")
	private Resource easyRetryMybatisResouse;

	@Bean("easyRetrySqlSessionFactory")
	public SqlSessionFactory sqlSessionFactory(
		@Qualifier("easyRetryMybatisDataSource") DataSource easyRetryMybatisDataSource)
		throws Exception {
		SqlSessionFactoryBean sqlSessionFactoryBean = new SqlSessionFactoryBean();
		sqlSessionFactoryBean.setDataSource(easyRetryMybatisDataSource);
		sqlSessionFactoryBean.setConfigLocation(easyRetryMybatisResouse);
		return sqlSessionFactoryBean.getObject();
	}

	@Bean
	public RetryTaskDAO retryTaskDAO(
		@Qualifier("easyRetrySqlSessionFactory") SqlSessionFactory sqlSessionFactory) {
		return new RetryTaskDAOImpl(sqlSessionFactory);
	}

	@Bean
	public RetryTaskAccess mybatisRetryTaskAccess(RetryTaskDAO retryTaskDAO) {
		return new MybatisRetryTaskAccess(retryTaskDAO);
	}

	@Bean
	@ConditionalOnMissingBean(RetryConfiguration.class)
	public RetryConfiguration configuration(RetryTaskAccess mybatisRetryTaskAccess,
		RetryEventMulticaster retryEventMulticaster) {
		DefaultRetryStrategy defaultRetryStrategy = new DefaultRetryStrategy();
		ResultPredicateSerializer resultPredicateSerializer = new HessianResultPredicateSerializer();
		return new RetryConfiguration() {
			@Override
			public RetryTaskAccess getRetryTaskAccess() {
				return mybatisRetryTaskAccess;
			}

			@Override
			public RetrySerializerAccess getRetrySerializerAccess() {
				return new DefaultRetrySerializerAccess();
			}

			@Override
			public RetryStrategyAccess getRetryStrategyAccess() {
				return new RetryStrategyAccess() {

					@Override
					public StopStrategy getCurrentGlobalStopStrategy() {
						return defaultRetryStrategy;
					}

					@Override
					public WaitStrategy getCurrentGlobalWaitStrategy() {
						return defaultRetryStrategy;
					}
				};
			}

			@Override
			public ExecutorSolver getExecutorSolver() {
				return executorName -> applicationContext.getBean(executorName);
			}

			@Override
			public ResultPredicateSerializer getResultPredicateSerializer() {
				return resultPredicateSerializer;
			}

			@Override
			public Integer getMaxRetryTimes() {
				return easyRetryMybatisProperties.getMaxRetryTimes();
			}

			@Override
			public RetryEventMulticaster getRetryEventMulticaster() {
				return retryEventMulticaster;
			}
		};
	}

	@Bean
	@ConditionalOnMissingBean(RetryInterceptor.class)
	public RetryInterceptor retryInterceptor(RetryConfiguration configuration) {
		RetryInterceptor retryInterceptor = new RetryInterceptor();
		retryInterceptor.setApplicationContext(applicationContext);
		retryInterceptor.setRetryConfiguration(configuration);
		return retryInterceptor;
	}

	@Bean(initMethod = "start")
	public RetryContainer retryContainer(
		RetryConfiguration configuration, RetryExecutor defaultRetryExecutor) {
		log.warn("RetryConfiguration start");
		return new SimpleRetryContainer(
			configuration, defaultRetryExecutor);
	}

	@Override
	public void setApplicationContext(ApplicationContext applicationContext) throws BeansException {
		this.applicationContext = applicationContext;
	}


	@Bean
	@ConditionalOnMissingBean(RetryExecutor.class)
	public PersistenceRetryExecutor defaultRetryExecutor(RetryConfiguration configuration, RetryFilterInvocation retryInvocationHandler) {
		PersistenceRetryExecutor persistenceRetryExecutor = new PersistenceRetryExecutor();
		persistenceRetryExecutor.setRetryConfiguration(configuration);
		persistenceRetryExecutor.setRetryFilterInvocation(retryInvocationHandler);
		return persistenceRetryExecutor;
	}

	@Bean
	@ConditionalOnMissingBean(RetryEventMulticaster.class)
	public RetryEventMulticaster retryEventMulticaster() {
		return new SimpleRetryEventMulticaster();
	}

	@Bean
	@ConditionalOnMissingBean(RetryListenerInitialize.class)
	public RetryListenerInitialize retryListenerInitialize(RetryEventMulticaster retryEventMulticaster) {
		RetryListenerInitialize retryListenerInitialize = new RetryListenerInitialize();
		retryListenerInitialize.setRetryEventMulticaster(retryEventMulticaster);
		return retryListenerInitialize;
	}

	@Bean
	@ConditionalOnMissingBean(SpringRetryFilterDiscover.class)
	public SpringRetryFilterDiscover springRetryFilterDiscover() {
		return new SpringRetryFilterDiscover();
	}

	@Bean
	@ConditionalOnMissingBean(RetryFilterRegister.class)
	public SimpleRetryFilterRegister simpleRetryFilterRegister(){
		return new SimpleRetryFilterRegister();
	}

	@Bean
	@ConditionalOnMissingBean(RetryFilterInvocationHandler.class)
	public DefaultRetryFilterInvocationHandler retryInvocationHandler(RetryFilterRegister simpleRetryFilterRegister) {
		DefaultRetryFilterInvocationHandler defaultRetryFilterInvocationHandler =  new DefaultRetryFilterInvocationHandler();
		defaultRetryFilterInvocationHandler.setRetryFilterRegister(simpleRetryFilterRegister);
		return defaultRetryFilterInvocationHandler;
	}

	@Bean
	@ConditionalOnMissingBean(RetryFilterRegisterHandler.class)
	public RetryFilterRegisterHandler retryFilterRegisterHandler(RetryFilterDiscover springRetryFilterDiscover,RetryFilterRegister simpleRetryFilterRegister){
		DefaultRetryFilterRegisterHandler defaultRetryFilterRegisterHandler = new DefaultRetryFilterRegisterHandler();
		defaultRetryFilterRegisterHandler.setRetryFilterRegister(simpleRetryFilterRegister);
		defaultRetryFilterRegisterHandler.setRetryFilterDiscover(springRetryFilterDiscover);
		return defaultRetryFilterRegisterHandler;
	}

	@Bean
	public ApplicationListener easyRetryApplicationListener(RetryFilterInvocationHandler retryFilterInvocationHandler,RetryFilterRegisterHandler retryFilterRegisterHandler){
		SpringEventApplicationListener springEventApplicationListener = new SpringEventApplicationListener();
		springEventApplicationListener.setRetryFilterRegisterHandler(retryFilterRegisterHandler);
		springEventApplicationListener.setRetryFilterInvocationHandler(retryFilterInvocationHandler);
		return springEventApplicationListener;
	}

}
