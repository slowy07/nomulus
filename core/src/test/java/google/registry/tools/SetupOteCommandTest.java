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

package google.registry.tools;

import static com.google.common.truth.Truth.assertThat;
import static com.google.common.truth.Truth8.assertThat;
import static google.registry.model.registrar.Registrar.State.ACTIVE;
import static google.registry.model.tld.Registry.TldState.GENERAL_AVAILABILITY;
import static google.registry.model.tld.Registry.TldState.START_DATE_SUNRISE;
import static google.registry.testing.CertificateSamples.SAMPLE_CERT_HASH;
import static google.registry.testing.DatabaseHelper.createTld;
import static google.registry.testing.DatabaseHelper.loadRegistrar;
import static google.registry.testing.DatabaseHelper.persistPremiumList;
import static google.registry.testing.DatabaseHelper.persistResource;
import static org.joda.money.CurrencyUnit.USD;
import static org.joda.time.DateTimeZone.UTC;
import static org.junit.jupiter.api.Assertions.assertThrows;

import com.beust.jcommander.ParameterException;
import com.google.common.collect.ImmutableList;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.ImmutableSortedMap;
import google.registry.model.registrar.Registrar;
import google.registry.model.registrar.RegistrarContact;
import google.registry.model.tld.Registry;
import google.registry.model.tld.Registry.TldState;
import google.registry.testing.DeterministicStringGenerator;
import google.registry.testing.FakeClock;
import google.registry.util.CidrAddressBlock;
import java.security.cert.CertificateParsingException;
import org.joda.money.Money;
import org.joda.time.DateTime;
import org.joda.time.Duration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/** Unit tests for {@link SetupOteCommand}. */
class SetupOteCommandTest extends CommandTestCase<SetupOteCommand> {

  private static final String PASSWORD = "abcdefghijklmnop";

  private DeterministicStringGenerator passwordGenerator =
      new DeterministicStringGenerator("abcdefghijklmnopqrstuvwxyz");

  @BeforeEach
  void beforeEach() {
    command.passwordGenerator = passwordGenerator;
    command.clock = new FakeClock(DateTime.parse("2018-07-07TZ"));
    persistPremiumList("default_sandbox_list", USD, "sandbox,USD 1000");
  }

  /** Verify TLD creation. */
  private void verifyTldCreation(
      String tldName,
      String roidSuffix,
      TldState tldState,
      boolean isEarlyAccess) {
    Registry registry = Registry.get(tldName);
    assertThat(registry).isNotNull();
    assertThat(registry.getRoidSuffix()).isEqualTo(roidSuffix);
    assertThat(registry.getTldState(DateTime.now(UTC))).isEqualTo(tldState);
    assertThat(registry.getDnsWriters()).containsExactly("VoidDnsWriter");
    assertThat(registry.getPremiumListName()).hasValue("default_sandbox_list");
    assertThat(registry.getAddGracePeriodLength()).isEqualTo(Duration.standardMinutes(60));
    assertThat(registry.getRedemptionGracePeriodLength()).isEqualTo(Duration.standardMinutes(10));
    assertThat(registry.getPendingDeleteLength()).isEqualTo(Duration.standardMinutes(5));
    ImmutableSortedMap<DateTime, Money> eapFeeSchedule = registry.getEapFeeScheduleAsMap();
    if (!isEarlyAccess) {
      assertThat(eapFeeSchedule)
          .isEqualTo(ImmutableSortedMap.of(new DateTime(0), Money.of(USD, 0)));
    } else {
      assertThat(eapFeeSchedule)
          .isEqualTo(
              ImmutableSortedMap.of(
                  new DateTime(0),
                  Money.of(USD, 0),
                  DateTime.parse("2018-03-01T00:00:00Z"),
                  Money.of(USD, 100),
                  DateTime.parse("2030-03-01T00:00:00Z"),
                  Money.of(USD, 0)));
    }
  }

  private void verifyRegistrarCreation(
      String registrarName,
      String allowedTld,
      String password,
      ImmutableList<CidrAddressBlock> ipAllowList) {
    Registrar registrar = loadRegistrar(registrarName);
    assertThat(registrar).isNotNull();
    assertThat(registrar.getAllowedTlds()).containsExactlyElementsIn(ImmutableSet.of(allowedTld));
    assertThat(registrar.getRegistrarName()).isEqualTo(registrarName);
    assertThat(registrar.getState()).isEqualTo(ACTIVE);
    assertThat(registrar.verifyPassword(password)).isTrue();
    assertThat(registrar.getIpAddressAllowList()).isEqualTo(ipAllowList);
    assertThat(registrar.getClientCertificateHash()).hasValue(SAMPLE_CERT_HASH);
  }

  private void verifyRegistrarContactCreation(String registrarName, String email) {
    ImmutableSet<RegistrarContact> registrarContacts =
        loadRegistrar(registrarName).getContacts();
    assertThat(registrarContacts).hasSize(1);
    RegistrarContact registrarContact = registrarContacts.stream().findAny().get();
    assertThat(registrarContact.getEmailAddress()).isEqualTo(email);
    assertThat(registrarContact.getName()).isEqualTo(email);
    assertThat(registrarContact.getGaeUserId()).isNotNull();
  }

  @Test
  void testSuccess() throws Exception {
    runCommandForced(
        "--ip_allow_list=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--certfile=" + getCertFilename());

    verifyTldCreation("blobio-sunrise", "BLOBIOS0", START_DATE_SUNRISE, false);
    verifyTldCreation("blobio-ga", "BLOBIOG2", GENERAL_AVAILABILITY, false);
    verifyTldCreation("blobio-eap", "BLOBIOE3", GENERAL_AVAILABILITY, true);

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", PASSWORD, ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", PASSWORD, ipAddress);
    verifyRegistrarCreation("blobio-4", "blobio-ga", PASSWORD, ipAddress);
    verifyRegistrarCreation("blobio-5", "blobio-eap", PASSWORD, ipAddress);

    verifyRegistrarContactCreation("blobio-1", "contact@email.com");
    verifyRegistrarContactCreation("blobio-3", "contact@email.com");
    verifyRegistrarContactCreation("blobio-4", "contact@email.com");
    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  void testSuccess_shortRegistrarName() throws Exception {
    runCommandForced(
        "--ip_allow_list=1.1.1.1",
        "--registrar=abc",
        "--email=abc@email.com",
        "--certfile=" + getCertFilename());

    verifyTldCreation("abc-sunrise", "ABCSUNR0", START_DATE_SUNRISE, false);
    verifyTldCreation("abc-ga", "ABCGA2", GENERAL_AVAILABILITY, false);
    verifyTldCreation("abc-eap", "ABCEAP3", GENERAL_AVAILABILITY, true);

    ImmutableList<CidrAddressBlock> ipAddress =
        ImmutableList.of(CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("abc-1", "abc-sunrise", PASSWORD, ipAddress);
    verifyRegistrarCreation("abc-3", "abc-ga", PASSWORD, ipAddress);
    verifyRegistrarCreation("abc-4", "abc-ga", PASSWORD, ipAddress);
    verifyRegistrarCreation("abc-5", "abc-eap", PASSWORD, ipAddress);

    verifyRegistrarContactCreation("abc-1", "abc@email.com");
    verifyRegistrarContactCreation("abc-3", "abc@email.com");
    verifyRegistrarContactCreation("abc-4", "abc@email.com");
    verifyRegistrarContactCreation("abc-5", "abc@email.com");
  }

  @Test
  void testSuccess_multipleIps() throws Exception {
    runCommandForced(
        "--ip_allow_list=1.1.1.1,2.2.2.2",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--certfile=" + getCertFilename());

    verifyTldCreation("blobio-sunrise", "BLOBIOS0", START_DATE_SUNRISE, false);
    verifyTldCreation("blobio-ga", "BLOBIOG2", GENERAL_AVAILABILITY, false);
    verifyTldCreation("blobio-eap", "BLOBIOE3", GENERAL_AVAILABILITY, true);

    ImmutableList<CidrAddressBlock> ipAddresses = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"),
        CidrAddressBlock.create("2.2.2.2"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", PASSWORD, ipAddresses);
    verifyRegistrarCreation("blobio-3", "blobio-ga", PASSWORD, ipAddresses);
    verifyRegistrarCreation("blobio-4", "blobio-ga", PASSWORD, ipAddresses);
    verifyRegistrarCreation("blobio-5", "blobio-eap", PASSWORD, ipAddresses);

    verifyRegistrarContactCreation("blobio-1", "contact@email.com");
    verifyRegistrarContactCreation("blobio-3", "contact@email.com");
    verifyRegistrarContactCreation("blobio-4", "contact@email.com");
    verifyRegistrarContactCreation("blobio-5", "contact@email.com");
  }

  @Test
  void testFailure_missingIpAllowList() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("option is required: -a, --ip_allow_list");
  }

  @Test
  void testFailure_missingRegistrar() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("option is required: -r, --registrar");
  }

  @Test
  void testFailure_missingCertificateFile() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1", "--email=contact@email.com", "--registrar=blobio"));
    assertThat(thrown)
        .hasMessageThat()
        .contains("Must specify exactly one client certificate file.");
  }

  @Test
  void testFailure_missingEmail() {
    ParameterException thrown =
        assertThrows(
            ParameterException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--certfile=" + getCertFilename(),
                    "--registrar=blobio"));
    assertThat(thrown).hasMessageThat().contains("option is required: --email");
  }

  @Test
  void testFailure_invalidCert() {
    CertificateParsingException thrown =
        assertThrows(
            CertificateParsingException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--certfile=/dev/null"));
    assertThat(thrown).hasMessageThat().contains("No X509Certificate found");
  }

  @Test
  void testFailure_invalidRegistrar() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=3blo-bio",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Invalid registrar name: 3blo-bio");
  }

  @Test
  void testFailure_registrarTooShort() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=bl",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Invalid registrar name: bl");
  }

  @Test
  void testFailure_registrarTooLong() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=blobiotoooolong",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Invalid registrar name: blobiotoooolong");
  }

  @Test
  void testFailure_registrarInvalidCharacter() {
    IllegalArgumentException thrown =
        assertThrows(
            IllegalArgumentException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=blo#bio",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Invalid registrar name: blo#bio");
  }

  @Test
  void testFailure_tldExists() {
    createTld("blobio-sunrise");
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registry(\"blobio-sunrise\")");
  }

  @Test
  void testSuccess_tldExists_replaceExisting() throws Exception {
    createTld("blobio-sunrise");

    runCommandForced(
        "--overwrite",
        "--ip_allow_list=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--certfile=" + getCertFilename());

    verifyTldCreation("blobio-sunrise", "BLOBIOS0", START_DATE_SUNRISE, false);
    verifyTldCreation("blobio-ga", "BLOBIOG2", GENERAL_AVAILABILITY, false);
  }

  @Test
  void testFailure_registrarExists() {
    Registrar registrar =
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setRegistrarId("blobio-1")
            .setRegistrarName("blobio-1")
            .build();
    persistResource(registrar);
    IllegalStateException thrown =
        assertThrows(
            IllegalStateException.class,
            () ->
                runCommandForced(
                    "--ip_allow_list=1.1.1.1",
                    "--registrar=blobio",
                    "--email=contact@email.com",
                    "--certfile=" + getCertFilename()));
    assertThat(thrown).hasMessageThat().contains("Registrar(\"blobio-1\")");
  }

  @Test
  void testSuccess_registrarExists_replaceExisting() throws Exception {
    Registrar registrar =
        loadRegistrar("TheRegistrar")
            .asBuilder()
            .setRegistrarId("blobio-1")
            .setRegistrarName("blobio-1")
            .build();
    persistResource(registrar);

    runCommandForced(
        "--overwrite",
        "--ip_allow_list=1.1.1.1",
        "--registrar=blobio",
        "--email=contact@email.com",
        "--certfile=" + getCertFilename());

    ImmutableList<CidrAddressBlock> ipAddress = ImmutableList.of(
        CidrAddressBlock.create("1.1.1.1"));

    verifyRegistrarCreation("blobio-1", "blobio-sunrise", PASSWORD, ipAddress);
    verifyRegistrarCreation("blobio-3", "blobio-ga", PASSWORD, ipAddress);
  }
}
