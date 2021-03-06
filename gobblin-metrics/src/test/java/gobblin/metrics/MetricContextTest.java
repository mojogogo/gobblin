/*
 * Copyright (C) 2014-2015 LinkedIn Corp. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License"); you may not use
 * this file except in compliance with the License. You may obtain a copy of the
 * License at  http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software distributed
 * under the License is distributed on an "AS IS" BASIS, WITHOUT WARRANTIES OR
 * CONDITIONS OF ANY KIND, either express or implied.
 */

package gobblin.metrics;

import java.io.IOException;
import java.util.Map;
import java.util.SortedMap;
import java.util.SortedSet;
import java.util.concurrent.TimeUnit;

import org.testng.Assert;
import org.testng.annotations.AfterClass;
import org.testng.annotations.BeforeClass;
import org.testng.annotations.Test;

import com.codahale.metrics.Counter;
import com.codahale.metrics.Gauge;
import com.codahale.metrics.Histogram;
import com.codahale.metrics.Meter;
import com.codahale.metrics.Metric;
import com.codahale.metrics.MetricFilter;
import com.codahale.metrics.MetricRegistry;
import com.codahale.metrics.Timer;

import com.google.common.collect.Lists;

import static gobblin.metrics.TestConstants.*;

import gobblin.metrics.reporter.ContextAwareScheduledReporter;


/**
 * Unit tests for {@link MetricContext}.
 *
 * <p>
 *   This test class also tests classes {@link ContextAwareCounter}, {@link ContextAwareMeter},
 *   {@link ContextAwareHistogram}, {@link ContextAwareTimer}, {@link ContextAwareGauge},
 *   {@link gobblin.metrics.reporter.ContextAwareScheduledReporter}, and {@link TagBasedMetricFilter}.
 * </p>
 *
 * @author ynli
 */
@Test(groups = {"gobblin.metrics"})
public class MetricContextTest {

  private static final String CHILD_CONTEXT_NAME = "TestChildContext";
  private static final String JOB_ID_KEY = "job.id";
  private static final String JOB_ID_PREFIX = "TestJob-";
  private static final String TASK_ID_KEY = "task.id";
  private static final String TASK_ID_PREFIX = "TestTask-";
  private static final String METRIC_GROUP_KEY = "metric.group";
  private static final String INPUT_RECORDS_GROUP = "INPUT_RECORDS";
  private static final String TEST_REPORTER_NAME = TestContextAwareScheduledReporter.class.getName();

  private MetricContext context;
  private MetricContext childContext;

  @BeforeClass
  public void setUp() {
    this.context = MetricContext.builder(CONTEXT_NAME)
        .addTag(new Tag<String>(JOB_ID_KEY, JOB_ID_PREFIX + 0))
        .addContextAwareScheduledReporter(
            new TestContextAwareScheduledReporter.TestContextAwareScheduledReporterBuilder(TEST_REPORTER_NAME))
        .reportFullyQualifiedNames(false)
        .build();

    Assert.assertEquals(this.context.getName(), CONTEXT_NAME);
    Assert.assertFalse(this.context.getParent().isPresent());
    Assert.assertEquals(this.context.getTags().size(), 2); // uuid tag gets added automatically
    Assert.assertEquals(this.context.getTags().get(0).getKey(), JOB_ID_KEY);
    Assert.assertEquals(this.context.getTags().get(0).getValue(), JOB_ID_PREFIX + 0);
    // Second tag should be uuid
    Assert.assertTrue(this.context.getTags().get(1).getValue().toString()
        .matches("[0-9a-f]{8}-[0-9a-f]{4}-[1-5][0-9a-f]{3}-[89ab][0-9a-f]{3}-[0-9a-f]{12}"));
    Assert.assertEquals(this.context.metricNamePrefix(false),
        this.context.getTags().get(0).getValue() + "." + this.context.getTags().get(1).getValue());
  }

  @Test
  public void testChildContext() {
    this.childContext = this.context.childBuilder(CHILD_CONTEXT_NAME)
        .addTag(new Tag<String>(TASK_ID_KEY, TASK_ID_PREFIX + 0))
        .addContextAwareScheduledReporter(
            new TestContextAwareScheduledReporter.TestContextAwareScheduledReporterBuilder(TEST_REPORTER_NAME))
        .reportFullyQualifiedNames(false)
        .build();

    Assert.assertEquals(this.childContext.getName(), CHILD_CONTEXT_NAME);
    Assert.assertTrue(this.childContext.getParent().isPresent());
    Assert.assertEquals(this.childContext.getParent().get(), this.context);
    Assert.assertEquals(this.childContext.getTags().size(), 3);
    Assert.assertEquals(this.childContext.getTags().get(0).getKey(), JOB_ID_KEY);
    Assert.assertEquals(this.childContext.getTags().get(0).getValue(), JOB_ID_PREFIX + 0);
    Assert.assertEquals(this.childContext.getTags().get(1).getKey(), MetricContext.METRIC_CONTEXT_ID_TAG_NAME);
    Assert.assertEquals(this.childContext.getTags().get(2).getKey(), TASK_ID_KEY);
    Assert.assertEquals(this.childContext.getTags().get(2).getValue(), TASK_ID_PREFIX + 0);
    Assert.assertEquals(this.childContext.metricNamePrefix(false),
        MetricRegistry.name(JOB_ID_PREFIX + 0, this.childContext.getTags().get(1).getValue().toString(),
            TASK_ID_PREFIX + 0));
  }

  @Test(dependsOnMethods = "testChildContext")
  public void testContextAwareCounter() {
    ContextAwareCounter jobRecordsProcessed = this.context.contextAwareCounter(RECORDS_PROCESSED);
    Assert.assertEquals(this.context.getCounters().get(
            MetricRegistry.name(this.context.metricNamePrefix(false), jobRecordsProcessed.getName())),
        jobRecordsProcessed);
    Assert.assertEquals(jobRecordsProcessed.getContext(), this.context);
    Assert.assertEquals(jobRecordsProcessed.getName(), RECORDS_PROCESSED);

    Assert.assertTrue(jobRecordsProcessed.getTags().isEmpty());
    jobRecordsProcessed.addTag(new Tag<String>(METRIC_GROUP_KEY, INPUT_RECORDS_GROUP));
    Assert.assertEquals(jobRecordsProcessed.getTags().size(), 1);
    Assert.assertEquals(jobRecordsProcessed.getTags().get(0).getKey(), METRIC_GROUP_KEY);
    Assert.assertEquals(jobRecordsProcessed.getTags().get(0).getValue(), INPUT_RECORDS_GROUP);
    Assert.assertEquals(
        jobRecordsProcessed.getFullyQualifiedName(false), MetricRegistry.name(INPUT_RECORDS_GROUP, RECORDS_PROCESSED));

    jobRecordsProcessed.inc();
    Assert.assertEquals(jobRecordsProcessed.getCount(), 1l);
    jobRecordsProcessed.inc(5);
    Assert.assertEquals(jobRecordsProcessed.getCount(), 6l);
    jobRecordsProcessed.dec();
    Assert.assertEquals(jobRecordsProcessed.getCount(), 5l);
    jobRecordsProcessed.dec(3);
    Assert.assertEquals(jobRecordsProcessed.getCount(), 2l);

    ContextAwareCounter taskRecordsProcessed = this.childContext.contextAwareCounter(RECORDS_PROCESSED);
    Assert.assertEquals(this.childContext.getCounters()
            .get(MetricRegistry.name(this.childContext.metricNamePrefix(false), taskRecordsProcessed.getName())),
        taskRecordsProcessed);
    Assert.assertEquals(taskRecordsProcessed.getContext(), this.childContext);
    Assert.assertEquals(taskRecordsProcessed.getName(), RECORDS_PROCESSED);

    taskRecordsProcessed.inc();
    Assert.assertEquals(taskRecordsProcessed.getCount(), 1l);
    Assert.assertEquals(jobRecordsProcessed.getCount(), 3l);
    taskRecordsProcessed.inc(3);
    Assert.assertEquals(taskRecordsProcessed.getCount(), 4l);
    Assert.assertEquals(jobRecordsProcessed.getCount(), 6l);
    taskRecordsProcessed.dec(4);
    Assert.assertEquals(taskRecordsProcessed.getCount(), 0l);
    Assert.assertEquals(jobRecordsProcessed.getCount(), 2l);
  }

  @Test
  public void testContextAwareMeter() {
    ContextAwareMeter jobRecordsProcessRate = this.context.contextAwareMeter(RECORD_PROCESS_RATE);
    Assert.assertEquals(this.context.getMeters()
            .get(MetricRegistry.name(this.context.metricNamePrefix(false), jobRecordsProcessRate.getName())),
        jobRecordsProcessRate);
    Assert.assertEquals(jobRecordsProcessRate.getContext(), this.context);
    Assert.assertEquals(jobRecordsProcessRate.getName(), RECORD_PROCESS_RATE);

    Assert.assertTrue(jobRecordsProcessRate.getTags().isEmpty());
    jobRecordsProcessRate.addTag(new Tag<String>(METRIC_GROUP_KEY, INPUT_RECORDS_GROUP));
    Assert.assertEquals(jobRecordsProcessRate.getTags().size(), 1);
    Assert.assertEquals(jobRecordsProcessRate.getTags().get(0).getKey(), METRIC_GROUP_KEY);
    Assert.assertEquals(jobRecordsProcessRate.getTags().get(0).getValue(), INPUT_RECORDS_GROUP);
    Assert.assertEquals(
        jobRecordsProcessRate.getFullyQualifiedName(false),
        MetricRegistry.name(INPUT_RECORDS_GROUP, RECORD_PROCESS_RATE));

    jobRecordsProcessRate.mark();
    jobRecordsProcessRate.mark(3);
    Assert.assertEquals(jobRecordsProcessRate.getCount(), 4l);

    ContextAwareMeter taskRecordsProcessRate = this.childContext.contextAwareMeter(RECORD_PROCESS_RATE);
    Assert.assertEquals(this.childContext.getMeters()
            .get(MetricRegistry.name(this.childContext.metricNamePrefix(false), taskRecordsProcessRate.getName())),
        taskRecordsProcessRate);
    Assert.assertEquals(taskRecordsProcessRate.getContext(), this.childContext);
    Assert.assertEquals(taskRecordsProcessRate.getName(), RECORD_PROCESS_RATE);

    taskRecordsProcessRate.mark(2);
    Assert.assertEquals(taskRecordsProcessRate.getCount(), 2l);
    Assert.assertEquals(jobRecordsProcessRate.getCount(), 6l);
    taskRecordsProcessRate.mark(5);
    Assert.assertEquals(taskRecordsProcessRate.getCount(), 7l);
    Assert.assertEquals(jobRecordsProcessRate.getCount(), 11l);
  }

  @Test
  public void testContextAwareHistogram() {
    ContextAwareHistogram jobRecordSizeDist = this.context.contextAwareHistogram(RECORD_SIZE_DISTRIBUTION);
    Assert.assertEquals(
        this.context.getHistograms().get(
            MetricRegistry.name(this.context.metricNamePrefix(false), jobRecordSizeDist.getName())),
        jobRecordSizeDist);
    Assert.assertEquals(jobRecordSizeDist.getContext(), this.context);
    Assert.assertEquals(jobRecordSizeDist.getName(), RECORD_SIZE_DISTRIBUTION);

    Assert.assertTrue(jobRecordSizeDist.getTags().isEmpty());
    jobRecordSizeDist.addTag(new Tag<String>(METRIC_GROUP_KEY, INPUT_RECORDS_GROUP));
    Assert.assertEquals(jobRecordSizeDist.getTags().size(), 1);
    Assert.assertEquals(jobRecordSizeDist.getTags().get(0).getKey(), METRIC_GROUP_KEY);
    Assert.assertEquals(jobRecordSizeDist.getTags().get(0).getValue(), INPUT_RECORDS_GROUP);
    Assert.assertEquals(
        jobRecordSizeDist.getFullyQualifiedName(false),
        MetricRegistry.name(INPUT_RECORDS_GROUP, RECORD_SIZE_DISTRIBUTION));

    jobRecordSizeDist.update(2);
    jobRecordSizeDist.update(4);
    jobRecordSizeDist.update(7);
    Assert.assertEquals(jobRecordSizeDist.getCount(), 3l);
    Assert.assertEquals(jobRecordSizeDist.getSnapshot().getMin(), 2l);
    Assert.assertEquals(jobRecordSizeDist.getSnapshot().getMax(), 7l);

    ContextAwareHistogram taskRecordSizeDist = this.childContext.contextAwareHistogram(RECORD_SIZE_DISTRIBUTION);
    Assert.assertEquals(this.childContext.getHistograms()
            .get(MetricRegistry.name(this.childContext.metricNamePrefix(false), taskRecordSizeDist.getName())),
        taskRecordSizeDist);
    Assert.assertEquals(taskRecordSizeDist.getContext(), this.childContext);
    Assert.assertEquals(taskRecordSizeDist.getName(), RECORD_SIZE_DISTRIBUTION);

    taskRecordSizeDist.update(3);
    taskRecordSizeDist.update(14);
    taskRecordSizeDist.update(11);
    Assert.assertEquals(taskRecordSizeDist.getCount(), 3l);
    Assert.assertEquals(taskRecordSizeDist.getSnapshot().getMin(), 3l);
    Assert.assertEquals(taskRecordSizeDist.getSnapshot().getMax(), 14l);
    Assert.assertEquals(jobRecordSizeDist.getCount(), 6l);
    Assert.assertEquals(jobRecordSizeDist.getSnapshot().getMin(), 2l);
    Assert.assertEquals(jobRecordSizeDist.getSnapshot().getMax(), 14l);
  }

  @Test
  public void testContextAwareTimer() {
    ContextAwareTimer jobTotalDuration = this.context.contextAwareTimer(TOTAL_DURATION);
    Assert.assertEquals(this.context.getTimers().get(
        MetricRegistry.name(this.context.metricNamePrefix(false), jobTotalDuration.getName())), jobTotalDuration);
    Assert.assertEquals(jobTotalDuration.getContext(), this.context);
    Assert.assertEquals(jobTotalDuration.getName(), TOTAL_DURATION);

    Assert.assertTrue(jobTotalDuration.getTags().isEmpty());
    jobTotalDuration.addTag(new Tag<String>(METRIC_GROUP_KEY, INPUT_RECORDS_GROUP));
    Assert.assertEquals(jobTotalDuration.getTags().size(), 1);
    Assert.assertEquals(jobTotalDuration.getTags().get(0).getKey(), METRIC_GROUP_KEY);
    Assert.assertEquals(jobTotalDuration.getTags().get(0).getValue(), INPUT_RECORDS_GROUP);
    Assert.assertEquals(
        jobTotalDuration.getFullyQualifiedName(false), MetricRegistry.name(INPUT_RECORDS_GROUP, TOTAL_DURATION));

    jobTotalDuration.update(50, TimeUnit.SECONDS);
    jobTotalDuration.update(100, TimeUnit.SECONDS);
    jobTotalDuration.update(150, TimeUnit.SECONDS);
    Assert.assertEquals(jobTotalDuration.getCount(), 3l);
    Assert.assertEquals(jobTotalDuration.getSnapshot().getMin(), TimeUnit.SECONDS.toNanos(50l));
    Assert.assertEquals(jobTotalDuration.getSnapshot().getMax(), TimeUnit.SECONDS.toNanos(150l));

    Assert.assertTrue(jobTotalDuration.time().stop() >= 0l);
  }

  @Test
  public void testTaggableGauge() {
    ContextAwareGauge<Long> queueSize = this.context.newContextAwareGauge(
        QUEUE_SIZE,
        new Gauge<Long>() {
          @Override
          public Long getValue() {
            return 1000l;
          }
        });
    this.context.register(QUEUE_SIZE, queueSize);

    Assert.assertEquals(queueSize.getValue().longValue(), 1000l);

    Assert.assertEquals(
        this.context.getGauges().get(MetricRegistry.name(this.context.metricNamePrefix(false), queueSize.getName())),
        queueSize);
    Assert.assertEquals(queueSize.getContext(), this.context);
    Assert.assertEquals(queueSize.getName(), QUEUE_SIZE);

    Assert.assertTrue(queueSize.getTags().isEmpty());
    queueSize.addTag(new Tag<String>(METRIC_GROUP_KEY, INPUT_RECORDS_GROUP));
    Assert.assertEquals(queueSize.getTags().size(), 1);
    Assert.assertEquals(queueSize.getTags().get(0).getKey(), METRIC_GROUP_KEY);
    Assert.assertEquals(queueSize.getTags().get(0).getValue(), INPUT_RECORDS_GROUP);
    Assert.assertEquals(
        queueSize.getFullyQualifiedName(false), MetricRegistry.name(INPUT_RECORDS_GROUP, QUEUE_SIZE));
  }

  @Test(dependsOnMethods = {
      "testContextAwareCounter",
      "testContextAwareMeter",
      "testContextAwareHistogram",
      "testContextAwareTimer",
      "testTaggableGauge"
  })
  public void testContextAwareScheduledReporter() {
    this.context.reportMetrics();
  }

  @Test(dependsOnMethods = {
      "testContextAwareCounter",
      "testContextAwareMeter",
      "testContextAwareHistogram",
      "testContextAwareTimer",
      "testTaggableGauge"
  })
  public void testGetMetrics() {
    SortedSet<String> names = this.context.getNames();
    Assert.assertEquals(names.size(), 6);
    Assert.assertTrue(names.contains(MetricRegistry.name(this.context.metricNamePrefix(false), RECORDS_PROCESSED)));
    Assert.assertTrue(names.contains(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_PROCESS_RATE)));
    Assert.assertTrue(
        names.contains(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_SIZE_DISTRIBUTION)));
    Assert.assertTrue(names.contains(MetricRegistry.name(this.context.metricNamePrefix(false), TOTAL_DURATION)));
    Assert.assertTrue(names.contains(MetricRegistry.name(this.context.metricNamePrefix(false), QUEUE_SIZE)));

    SortedSet<String> childNames = this.childContext.getNames();
    Assert.assertEquals(childNames.size(), 4);
    Assert.assertTrue(
        childNames.contains(MetricRegistry.name(this.childContext.metricNamePrefix(false), RECORDS_PROCESSED)));
    Assert.assertTrue(
        childNames.contains(MetricRegistry.name(this.childContext.metricNamePrefix(false), RECORD_PROCESS_RATE)));
    Assert.assertTrue(
        childNames.contains(MetricRegistry.name(this.childContext.metricNamePrefix(false), RECORD_SIZE_DISTRIBUTION)));

    Map<String, Metric> metrics = this.context.getMetrics();
    Assert.assertEquals(metrics.size(), 6);
    Assert.assertTrue(
        metrics.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORDS_PROCESSED)));
    Assert.assertTrue(
        metrics.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_PROCESS_RATE)));
    Assert.assertTrue(
        metrics.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_SIZE_DISTRIBUTION)));
    Assert.assertTrue(metrics.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), TOTAL_DURATION)));
    Assert.assertTrue(metrics.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), QUEUE_SIZE)));

    Map<String, Counter> counters = this.context.getCounters();
    Assert.assertEquals(counters.size(), 1);
    Assert.assertTrue(
        counters.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORDS_PROCESSED)));

    Map<String, Meter> meters = this.context.getMeters();
    Assert.assertEquals(meters.size(), 1);
    Assert.assertTrue(
        meters.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_PROCESS_RATE)));

    Map<String, Histogram> histograms = this.context.getHistograms();
    Assert.assertEquals(histograms.size(), 1);
    Assert.assertTrue(
        histograms.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_SIZE_DISTRIBUTION)));

    Map<String, Timer> timers = this.context.getTimers();
    Assert.assertEquals(timers.size(), 2);
    Assert.assertTrue(timers.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), TOTAL_DURATION)));

    Map<String, Gauge> gauges = this.context.getGauges();
    Assert.assertEquals(gauges.size(), 1);
    Assert.assertTrue(gauges.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), QUEUE_SIZE)));
  }

  @Test(dependsOnMethods = "testGetMetrics")
  @SuppressWarnings("unchecked")
  public void testGetMetricsWithFilter() {
    MetricFilter filter = new TagBasedMetricFilter(
        Lists.<Tag<?>>newArrayList(new Tag<String>(METRIC_GROUP_KEY, INPUT_RECORDS_GROUP)));

    Map<String, Counter> counters = this.context.getCounters(filter);
    Assert.assertEquals(counters.size(), 1);
    Assert.assertTrue(
        counters.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORDS_PROCESSED)));

    Map<String, Meter> meters = this.context.getMeters(filter);
    Assert.assertEquals(meters.size(), 1);
    Assert.assertTrue(
        meters.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_PROCESS_RATE)));

    Map<String, Histogram> histograms = this.context.getHistograms(filter);
    Assert.assertEquals(histograms.size(), 1);
    Assert.assertTrue(
        histograms.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), RECORD_SIZE_DISTRIBUTION)));

    Map<String, Timer> timers = this.context.getTimers(filter);
    Assert.assertEquals(timers.size(), 1);
    Assert.assertTrue(timers.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), TOTAL_DURATION)));

    Map<String, Gauge> gauges = this.context.getGauges(filter);
    Assert.assertEquals(gauges.size(), 1);
    Assert.assertTrue(gauges.containsKey(MetricRegistry.name(this.context.metricNamePrefix(false), QUEUE_SIZE)));
  }

  @Test(dependsOnMethods = {
      "testGetMetricsWithFilter",
      "testContextAwareScheduledReporter"
  })
  public void testRemoveMetrics() {
    Assert.assertTrue(this.childContext.remove(RECORDS_PROCESSED));
    Assert.assertTrue(this.childContext.getCounters().isEmpty());

    Assert.assertTrue(this.childContext.remove(RECORD_PROCESS_RATE));
    Assert.assertTrue(this.childContext.getMeters().isEmpty());

    Assert.assertTrue(this.childContext.remove(RECORD_SIZE_DISTRIBUTION));
    Assert.assertTrue(this.childContext.getHistograms().isEmpty());

    Assert.assertEquals(this.childContext.getNames().size(), 1);
  }

  @AfterClass
  public void tearDown() throws IOException {
    if (this.context != null) {
      this.context.close();
    }
  }

  private static class TestContextAwareScheduledReporter extends ContextAwareScheduledReporter {

    protected TestContextAwareScheduledReporter(MetricContext context, String name, MetricFilter filter,
        TimeUnit rateUnit, TimeUnit durationUnit) {
      super(context, name, filter, rateUnit, durationUnit);
    }

    @Override
    protected void reportInContext(MetricContext context,
                                   SortedMap<String, Gauge> gauges,
                                   SortedMap<String, Counter> counters,
                                   SortedMap<String, Histogram> histograms,
                                   SortedMap<String, Meter> meters,
                                   SortedMap<String, Timer> timers) {

      Assert.assertEquals(context.getName(), CONTEXT_NAME);

      Assert.assertEquals(gauges.size(), 1);
      Assert.assertTrue(gauges.containsKey(MetricRegistry.name(context.metricNamePrefix(false), QUEUE_SIZE)));

      Assert.assertEquals(counters.size(), 1);
      Assert.assertTrue(counters.containsKey(MetricRegistry.name(context.metricNamePrefix(false), RECORDS_PROCESSED)));

      Assert.assertEquals(histograms.size(), 1);
      Assert.assertTrue(
          histograms.containsKey(MetricRegistry.name(context.metricNamePrefix(false), RECORD_SIZE_DISTRIBUTION)));

      Assert.assertEquals(meters.size(), 1);
      Assert.assertTrue(meters.containsKey(MetricRegistry.name(context.metricNamePrefix(false), RECORD_PROCESS_RATE)));

      Assert.assertEquals(timers.size(), 2);
      Assert.assertTrue(timers.containsKey(MetricRegistry.name(context.metricNamePrefix(false), TOTAL_DURATION)));
    }

    private static class TestContextAwareScheduledReporterBuilder extends Builder {

      public TestContextAwareScheduledReporterBuilder(String name) {
        super(name);
      }

      @Override
      public ContextAwareScheduledReporter build(MetricContext context) {
        return new MetricContextTest.TestContextAwareScheduledReporter(
            context, this.name, this.filter, this.rateUnit, this.durationUnit);
      }
    }
  }
}
