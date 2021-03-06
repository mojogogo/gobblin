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

package gobblin.source.extractor.extract.kafka.workunit.packer;

import gobblin.source.workunit.WorkUnit;


/**
 * Estimates the size of a Kafka {@link WorkUnit}, which contains one or more partitions of the same topic.
 *
 * @author ziliu
 */
public interface KafkaWorkUnitSizeEstimator {

  /**
   * Estimates the size of a Kafka {@link WorkUnit}.
   */
  public double calcEstimatedSize(WorkUnit workUnit);
}
