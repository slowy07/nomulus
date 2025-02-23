// Copyright 2017 The Nomulus Authors. All Rights Reserved.
//
// Licensed under the Apache License, Version 2.0 (the "License");
// you may not use this file except in compliance with the License.
// You may obtain a copy of the License at
//
//     http://www.apache.org/licenses/LICENSE-2.0
//
// Unless required by applicable law or agreed to in writing, software
// distributed under the License is distributed on an "AS IS" BASIS,
// WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
// See the License for the specific language governing permissions and
// limitations under the License.

package google.registry.model;

import google.registry.util.PreconditionsUtils;
import javax.persistence.Access;
import javax.persistence.AccessType;
import javax.persistence.AttributeOverride;
import javax.persistence.Column;
import javax.persistence.MappedSuperclass;
import javax.xml.bind.annotation.XmlTransient;

/**
 * Base class for entities that are the root of a Registry 2.0 entity group that gets enrolled in
 * commit logs for backup purposes.
 *
 * <p>The commit log system needs to preserve the ordering of closely timed mutations to entities in
 * a single entity group. We require an {@link UpdateAutoTimestamp} field on the root of a group so
 * that we can enforce strictly increasing timestamps.
 */
@MappedSuperclass
public abstract class BackupGroupRoot extends ImmutableObject {

  /**
   * An automatically managed timestamp of when this object was last written to Datastore.
   *
   * <p>Note that this is distinct from the EPP-specified {@link EppResource#lastEppUpdateTime}, in
   * that this is updated on every save, rather than only in response to an {@code <update>} command
   */
  @XmlTransient
  // Prevents subclasses from unexpectedly accessing as property (e.g., HostResource), which would
  // require an unnecessary non-private setter method.
  @Access(AccessType.FIELD)
  @AttributeOverride(name = "lastUpdateTime", column = @Column(name = "updateTimestamp"))
  UpdateAutoTimestamp updateTimestamp = UpdateAutoTimestamp.create(null);

  /** Get the {@link UpdateAutoTimestamp} for this entity. */
  public UpdateAutoTimestamp getUpdateTimestamp() {
    return updateTimestamp;
  }

  /**
   * Copies {@link #updateTimestamp} from another entity.
   *
   * <p>This method is for the few cases when {@code updateTimestamp} is copied between different
   * types of entities. Use {@link #clone} for same-type copying.
   */
  protected void copyUpdateTimestamp(BackupGroupRoot other) {
    this.updateTimestamp = PreconditionsUtils.checkArgumentNotNull(other, "other").updateTimestamp;
  }
}
