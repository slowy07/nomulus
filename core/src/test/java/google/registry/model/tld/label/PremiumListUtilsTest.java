// Copyright 2019 The Nomulus Authors. All Rights Reserved.
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

package google.registry.model.tld.label;

import static com.google.common.truth.Truth.assertThat;
import static google.registry.model.tld.label.PremiumListUtils.parseToPremiumList;
import static org.joda.money.CurrencyUnit.USD;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.google.common.collect.ImmutableList;
import google.registry.testing.AppEngineExtension;
import java.math.BigDecimal;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.RegisterExtension;

/** Unit tests for {@link PremiumListUtils}. */
class PremiumListUtilsTest {

  @RegisterExtension
  final AppEngineExtension appEngine =
      AppEngineExtension.builder().withDatastoreAndCloudSql().build();

  @Test
  void parseInputToPremiumList_works() {
    PremiumList premiumList =
        parseToPremiumList(
            "testlist", USD, ImmutableList.of("foo,USD 99.50", "bar,USD 30", "baz,USD 10"));
    assertThat(premiumList.getName()).isEqualTo("testlist");
    assertThat(premiumList.getLabelsToPrices())
        .containsExactly("foo", twoDigits(99.50), "bar", twoDigits(30), "baz", twoDigits(10));
  }

  @Test
  void parseInputToPremiumList_throwsOnInconsistentCurrencies() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                parseToPremiumList(
                    "testlist",
                    USD,
                    ImmutableList.of("foo,USD 99.50", "bar,USD 30", "baz,JPY 990")));
    assertThat(thrown).hasMessageThat().isEqualTo("The currency unit must be USD");
  }

  private static BigDecimal twoDigits(double num) {
    return BigDecimal.valueOf((long) (num * 100.0), 2);
  }
}
