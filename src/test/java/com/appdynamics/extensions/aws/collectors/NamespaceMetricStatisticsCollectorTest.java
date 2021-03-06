package com.appdynamics.extensions.aws.collectors;

import static org.junit.Assert.assertEquals;
import static org.mockito.Matchers.any;
import static org.mockito.Matchers.anyInt;
import static org.mockito.Matchers.isA;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.powermock.api.mockito.PowerMockito.whenNew;

import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.concurrent.ExecutorCompletionService;

import org.junit.Test;
import org.junit.runner.RunWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.powermock.core.classloader.annotations.PowerMockIgnore;
import org.powermock.core.classloader.annotations.PrepareForTest;
import org.powermock.modules.junit4.PowerMockRunner;

import com.amazonaws.services.cloudwatch.model.Metric;
import com.appdynamics.extensions.aws.config.Account;
import com.appdynamics.extensions.aws.config.ConcurrencyConfig;
import com.appdynamics.extensions.aws.config.CredentialsDecryptionConfig;
import com.appdynamics.extensions.aws.config.MetricsConfig;
import com.appdynamics.extensions.aws.config.MetricsTimeRange;
import com.appdynamics.extensions.aws.config.ProxyConfig;
import com.appdynamics.extensions.aws.metric.AccountMetricStatistics;
import com.appdynamics.extensions.aws.metric.MetricStatistic;
import com.appdynamics.extensions.aws.metric.NamespaceMetricStatistics;
import com.appdynamics.extensions.aws.metric.RegionMetricStatistics;
import com.appdynamics.extensions.aws.metric.processors.MetricsProcessor;
import com.google.common.collect.Lists;

@RunWith(PowerMockRunner.class)
@PrepareForTest({NamespaceMetricStatisticsCollector.class,
	ExecutorCompletionService.class})
@PowerMockIgnore({"org.apache.*, javax.xml.*"})
public class NamespaceMetricStatisticsCollectorTest {
	
	private NamespaceMetricStatisticsCollector classUnderTest;
	
	@Mock
	private MetricsProcessor mockMetricsProcessor;
	
	@Mock
	private MetricsConfig mockMetricsConfig;
	
	@Mock
	private ConcurrencyConfig mockConcurrencyConfig;
	
	@SuppressWarnings("unchecked")
	@Test
	public void testMetricsRetrievalIsSuccessful() throws Exception {
		List<Account> testAccounts = getTestAccounts();
		
		when(mockMetricsProcessor.getNamespace()).thenReturn("TestNamespace");
		
		AccountMetricStatisticsCollector mockAccountStatsCollector1 = mock(AccountMetricStatisticsCollector.class);
		AccountMetricStatistics accountStats1 = createTestAccountMetricStatistics(testAccounts.get(0).getDisplayAccountName());
		when(mockAccountStatsCollector1.call()).thenReturn(accountStats1);
		
		AccountMetricStatisticsCollector mockAccountStatsCollector2 = mock(AccountMetricStatisticsCollector.class);
		AccountMetricStatistics accountStats2 = createTestAccountMetricStatistics(testAccounts.get(1).getDisplayAccountName());
		when(mockAccountStatsCollector2.call()).thenReturn(accountStats2);
		
		// simulate account stats collector creation
		AccountMetricStatisticsCollector.Builder mockBuilder = mock(AccountMetricStatisticsCollector.Builder.class);
		whenNew(AccountMetricStatisticsCollector.Builder.class).withNoArguments().thenReturn(mockBuilder);
		when(mockBuilder.withAccount(any(Account.class))).thenReturn(mockBuilder);
		when(mockBuilder.withMaxErrorRetrySize(anyInt())).thenReturn(mockBuilder);
		when(mockBuilder.withMetricsProcessor(any(MetricsProcessor.class))).thenReturn(mockBuilder);
		when(mockBuilder.withMetricsTimeRange(any(MetricsTimeRange.class))).thenReturn(mockBuilder);
		when(mockBuilder.withNoOfMetricThreadsPerRegion(anyInt())).thenReturn(mockBuilder);
		when(mockBuilder.withNoOfRegionThreadsPerAccount(anyInt())).thenReturn(mockBuilder);
		when(mockBuilder.withCredentialsDecryptionConfig(any(CredentialsDecryptionConfig.class))).thenReturn(mockBuilder);
		when(mockBuilder.withProxyConfig(any(ProxyConfig.class))).thenReturn(mockBuilder);
		when(mockBuilder.build()).thenReturn(mockAccountStatsCollector1, mockAccountStatsCollector2);
		
		ArgumentCaptor<NamespaceMetricStatistics> argumentCaptor =
				ArgumentCaptor.forClass(NamespaceMetricStatistics.class);
		
		// since metricsprocessor converts NamespaceMetricStatistics to a map and we don't know the implementation
		// let's just capture it here for assertion
		Map<String, Double> mockMap = mock(Map.class);
		when(mockMetricsProcessor.createMetricStatsMapForUpload(argumentCaptor.capture())).thenReturn(mockMap);
		
		classUnderTest = new NamespaceMetricStatisticsCollector.Builder(testAccounts, 
				mockConcurrencyConfig, 
				mockMetricsConfig, 
				mockMetricsProcessor)
			.build();
		
		classUnderTest.call();
		
		NamespaceMetricStatistics result = argumentCaptor.getValue();
		
		verify(mockMetricsProcessor).createMetricStatsMapForUpload(isA(NamespaceMetricStatistics.class));
		assertEquals(accountStats1, result.getAccountMetricStatisticsList().get(0));
		assertEquals(accountStats2, result.getAccountMetricStatisticsList().get(1));
	}
	
	private List<Account> getTestAccounts() {
		List<Account> testAccounts = Lists.newArrayList();
		
		for (int index=0; index < 2; index++) {
			Account testAccount = new Account();
			testAccount.setAwsAccessKey("testAccessKey" + index);
			testAccount.setAwsSecretKey("testAwsSecretKey" + index);
			testAccount.setDisplayAccountName("TestAccount" + index);
			testAccounts.add(testAccount);
		}
		
		return testAccounts;
	}
	
	private AccountMetricStatistics createTestAccountMetricStatistics(String account) {
		AccountMetricStatistics accountStats = new AccountMetricStatistics();
		accountStats.setAccountName(account);
		
		Random random = new Random();
		
		for (int regionIndex=0; regionIndex< random.nextInt(3); regionIndex++) {
			RegionMetricStatistics regionStats = new RegionMetricStatistics();
			regionStats.setRegion("region" + regionIndex);
			
			for (int metricIndex=0; metricIndex < random.nextInt(10); metricIndex++) {
				Metric metric = new Metric();
				metric.setMetricName("testMetric" + metricIndex);
				
				MetricStatistic metricStatistic = new MetricStatistic();
				metricStatistic.setMetric(metric);
				metricStatistic.setValue(new Random().nextDouble());
				metricStatistic.setUnit("testUnit");
				
				regionStats.addMetricStatistic(metricStatistic);
			}
			
			accountStats.add(regionStats);
		}

		return accountStats;
	}

}
