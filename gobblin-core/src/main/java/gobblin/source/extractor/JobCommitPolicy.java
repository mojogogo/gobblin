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

package gobblin.source.extractor;

import java.util.Properties;

import gobblin.configuration.ConfigurationKeys;
import gobblin.configuration.State;


/**
 * A enumeration of policies on how to commit a completed job.
 *
 * @author ynli
 */
public enum JobCommitPolicy {

  /**
   * Commit a job if and only if all its tasks successfully complete and commit.
   */
  COMMIT_ON_FULL_SUCCESS("full"),

  /**
   * Commit a job even if some of its tasks fail. It's up to the {@link gobblin.publisher.DataPublisher}
   * to decide whether data of failed tasks of the job should be committed or not.
   */
  COMMIT_ON_PARTIAL_SUCCESS("partial"),

  /**
   * Commit output data of tasks whose state are {@link gobblin.configuration.WorkUnitState.WorkingState#SUCCESSFUL}.
   * This is different from {@link #COMMIT_ON_PARTIAL_SUCCESS} in that this policy won't publish partial output data
   * of a task if it has not succeeded, whereas {@link #COMMIT_ON_PARTIAL_SUCCESS} will.
   *
   * It is recommended to use this commit policy in conjunction with task-level data publishing (i.e., when
   * {@link ConfigurationKeys#PUBLISH_DATA_AT_JOB_LEVEL} is set to {@code false}).
   */
  COMMIT_SUCCESSFUL_TASKS("successful");

  private final String name;

  JobCommitPolicy(String name) {
    this.name = name;
  }

  /**
   * Get a {@link JobCommitPolicy} for the given job commit policy name.
   *
   * @param name Job commit policy name
   * @return a {@link JobCommitPolicy} for the given job commit policy name
   */
  public static JobCommitPolicy forName(String name) {
    if (COMMIT_ON_FULL_SUCCESS.name.equalsIgnoreCase(name)) {
      return COMMIT_ON_FULL_SUCCESS;
    }

    if (COMMIT_ON_PARTIAL_SUCCESS.name.equalsIgnoreCase(name)) {
      return COMMIT_ON_PARTIAL_SUCCESS;
    }

    if (COMMIT_SUCCESSFUL_TASKS.name.equalsIgnoreCase(name)) {
      return COMMIT_SUCCESSFUL_TASKS;
    }

    throw new IllegalArgumentException(String.format("Job commit policy with name %s is not supported", name));
  }

  /**
   * Get a {@link JobCommitPolicy} through its name specified in configuration property
   * {@link ConfigurationKeys#JOB_COMMIT_POLICY_KEY}.
   *
   * @param jobProps a {@link Properties} instance carrying job configuration properties
   * @return a {@link JobCommitPolicy} with the given name specified in {@link ConfigurationKeys#JOB_COMMIT_POLICY_KEY}
   */
  public static JobCommitPolicy getCommitPolicy(Properties jobProps) {
    return forName(jobProps.getProperty(ConfigurationKeys.JOB_COMMIT_POLICY_KEY,
        ConfigurationKeys.DEFAULT_JOB_COMMIT_POLICY));
  }

  /**
   * Get a {@link JobCommitPolicy} through its name specified in configuration property
   * {@link ConfigurationKeys#JOB_COMMIT_POLICY_KEY}.
   *
   * @param state a {@link State} instance carrying job configuration properties
   * @return a {@link JobCommitPolicy} with the given name specified in {@link ConfigurationKeys#JOB_COMMIT_POLICY_KEY}
   */
  public static JobCommitPolicy getCommitPolicy(State state) {
    return forName(state.getProp(ConfigurationKeys.JOB_COMMIT_POLICY_KEY, ConfigurationKeys.DEFAULT_JOB_COMMIT_POLICY));
  }
}
