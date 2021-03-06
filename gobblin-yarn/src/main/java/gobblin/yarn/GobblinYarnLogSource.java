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

package gobblin.yarn;

import java.io.IOException;

import org.apache.hadoop.conf.Configuration;
import org.apache.hadoop.fs.FileSystem;
import org.apache.hadoop.fs.Path;
import org.apache.hadoop.yarn.api.ApplicationConstants;
import org.apache.hadoop.yarn.api.records.ContainerId;

import com.google.common.collect.ImmutableSet;

import gobblin.util.logs.LogCopier;


/**
 * A base class for container processes that are sources of Gobblin Yarn application logs.
 *
 * <p>
 *   The source log files are supposed to be on the local {@link FileSystem} and will
 *   be copied to a given destination {@link FileSystem}, which is typically HDFS.
 * </p>
 *
 * @author ynli
 */
class GobblinYarnLogSource {

  /**
   * Build a {@link LogCopier} instance used to copy the logs out from this {@link GobblinYarnLogSource}.
   *
   * @param containerId the {@link ContainerId} of the container the {@link LogCopier} runs in
   * @param destFs the destination {@link FileSystem}
   * @param appWorkDir the Gobblin Yarn application working directory on HDFS
   * @return a {@link LogCopier} instance
   * @throws IOException if it fails on any IO operation
   */
  protected LogCopier buildLogCopier(ContainerId containerId, FileSystem destFs, Path appWorkDir)
      throws IOException {
    return LogCopier.newBuilder()
        .useSrcFileSystem(FileSystem.getLocal(new Configuration()))
        .useDestFileSystem(destFs)
        .readFrom(getLocalLogDir())
        .writeTo(getHdfsLogDir(destFs, appWorkDir))
        .acceptsLogFileExtensions(ImmutableSet.of(ApplicationConstants.STDOUT, ApplicationConstants.STDERR))
        .useLogFileNamePrefix(containerId.toString())
        .build();
  }

  private Path getLocalLogDir() throws IOException {
    return new Path(System.getenv(ApplicationConstants.Environment.LOG_DIRS.toString()));
  }

  private Path getHdfsLogDir(FileSystem destFs, Path appWorkDir) throws IOException {
    Path logRootDir = new Path(appWorkDir, GobblinYarnConfigurationKeys.APP_LOGS_DIR_NAME);
    if (!destFs.exists(logRootDir)) {
      destFs.mkdirs(logRootDir);
    }

    return new Path(logRootDir, System.getenv(ApplicationConstants.Environment.CONTAINER_ID.toString()));
  }
}
