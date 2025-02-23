// Copyright 2021 The Nomulus Authors. All Rights Reserved.
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

package google.registry.schema.replay;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.persistence.transaction.TransactionManagerFactory.tm;

import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.registrar.RegistrarContact.RegistrarPocId;
import google.registry.persistence.VKey;
import google.registry.testing.AppEngineExtension;
import google.registry.testing.DatastoreEntityExtension;
import google.registry.testing.TmOverrideExtension;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Order;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Unit tests for {@link SqlEntity#getPrimaryKeyString}. */
public class SqlEntityTest {

  @RegisterExtension
  @Order(1)
  final DatastoreEntityExtension datastoreEntityExtension = new DatastoreEntityExtension();

  @RegisterExtension
  final AppEngineExtension database =
      new AppEngineExtension.Builder().withCloudSql().withoutCannedData().build();

  @RegisterExtension
  @Order(Order.DEFAULT + 1)
  TmOverrideExtension tmOverrideExtension = TmOverrideExtension.withJpa();

  @BeforeEach
  void setup() throws Exception {
    AppEngineExtension.loadInitialData();
  }

  @Test
  void getPrimaryKeyString_oneIdColumn() {
    // AppEngineExtension canned data: Registrar1
    assertThat(
            tm().transact(() -> tm().loadByKey(Registrar.createVKey("NewRegistrar")))
                .getPrimaryKeyString())
        .contains("NewRegistrar");
  }

  @Test
  void getPrimaryKeyString_multiId() {
    // AppEngineExtension canned data: RegistrarContact1
    VKey<RegistrarContact> key =
        VKey.createSql(
            RegistrarContact.class, new RegistrarPocId("janedoe@theregistrar.com", "NewRegistrar"));
    String expected = "emailAddress=janedoe@theregistrar.com\n    registrarId=NewRegistrar";
    assertThat(tm().transact(() -> tm().loadByKey(key)).getPrimaryKeyString()).contains(expected);
  }
}
