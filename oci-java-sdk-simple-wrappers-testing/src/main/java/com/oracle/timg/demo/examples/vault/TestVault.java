/*Copyright (c) 2026 Oracle and/or its affiliates.

The Universal Permissive License (UPL), Version 1.0

Subject to the condition set forth below, permission is hereby granted to any
person obtaining a copy of this software, associated documentation and/or data
(collectively the "Software"), free of charge and under any and all copyright
rights in the Software, and any and all patent rights owned or freely
licensable by each licensor hereunder covering either (i) the unmodified
Software as contributed to or provided by such licensor, or (ii) the Larger
Works (as defined below), to deal in both

(a) the Software, and
(b) any piece of software and/or hardware listed in the lrgrwrks.txt file if
one is included with the Software (each a "Larger Work" to which the Software
is contributed by such licensors),

without restriction, including without limitation the rights to copy, create
derivative works of, display, perform, and distribute the Software and make,
use, sell, offer for sale, import, export, have made, and have sold the
Software and the Larger Work(s), and to sublicense the foregoing rights on
either these or other terms.

This license is subject to the following condition:
The above copyright notice and either this complete permission notice or at
a minimum a reference to the UPL must be included in all copies or
substantial portions of the Software.

THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR
IMPLIED, INCLUDING BUT NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY,
FITNESS FOR A PARTICULAR PURPOSE AND NONINFRINGEMENT. IN NO EVENT SHALL THE
AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM, DAMAGES OR OTHER
LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE
SOFTWARE.
 */
package com.oracle.timg.demo.examples.vault;

import java.io.IOException;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.List;
import java.util.stream.Collectors;

import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.keymanagement.model.Key;
import com.oracle.bmc.keymanagement.model.KeySummary;
import com.oracle.bmc.keymanagement.model.KeyVersion;
import com.oracle.bmc.keymanagement.model.Vault;
import com.oracle.bmc.vault.model.Secret;
import com.oracle.bmc.vault.model.SecretGenerationContext;
import com.oracle.bmc.vault.model.SecretVersion;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;
import com.oracle.timg.oci.identity.IdentityProcessor;
import com.oracle.timg.oci.vault.VaultProcessor;

import timgutilities.textio.ChoiceDescription;
import timgutilities.textio.ChoiceDescriptionData;
import timgutilities.textio.TextIOUtils;
import timgutilities.textio.TextIOUtils.NUM_TYPE;

public class TestVault {
	public static void main(String args[]) throws IllegalArgumentException, Exception {
		TextIOUtils.doOutput("Configuring OCI connection");
		AuthenticationProcessor authenticationProcessor = new AuthenticationProcessor("DEFAULT");
		IdentityProcessor identityProcessor = new IdentityProcessor(authenticationProcessor);
		VaultProcessor vaultProcessor = new VaultProcessor(authenticationProcessor);
		String compartmentPath = TextIOUtils.getString("Please enter the compartment path to operate in",
				"/domain-specialists/tim.graves");
		Compartment compartment = identityProcessor.locateCompartmentByPath(compartmentPath);
		if (compartment == null) {
			TextIOUtils.doOutput("Can't locate compartment " + compartmentPath + ", cannot continue");
			System.exit(0);
		}
		TextIOUtils.doOutput("OCID of " + compartmentPath + " is " + compartment.getId());
		List<Vault> vaults = vaultProcessor.listVaults(compartment);
		if (vaults.isEmpty()) {
			TextIOUtils.doOutput("No vaults found in compartment, cannot continue");
			return;
		}
		ChoiceDescriptionData<Vault> vaultCdd = new ChoiceDescriptionData<>(
				vaults.stream().map(vault -> new ChoiceDescription<Vault>(vault.getDisplayName(), vault)).toList());
		Vault vault = TextIOUtils.getParamChoice("Please chose the vault", vaultCdd);
		TextIOUtils.doOutput("You chose vault " + vault.getDisplayName());
		List<KeySummary> keySummaries = vaultProcessor.listKeySummaries(vault);
		if (keySummaries.isEmpty()) {
			TextIOUtils.doOutput("No key found in vault, cannot continue");
			return;
		}
		ChoiceDescriptionData<KeySummary> keySummaryCdd = new ChoiceDescriptionData<>(keySummaries.stream()
				.map(keySummary -> new ChoiceDescription<KeySummary>(keySummary.getDisplayName(), keySummary))
				.toList());
		KeySummary keySummary = TextIOUtils.getParamChoice("Please chose the key", keySummaryCdd);
		TextIOUtils.doOutput("You chose key summary " + keySummary.getDisplayName());
		Key key = vaultProcessor.getKey(vault, keySummary);
		TextIOUtils.doOutput("The summary is for key " + key.toString());
		List<KeyVersion> keyVersions = vaultProcessor.listKeyVersions(vault, key, null);
		if (keyVersions.isEmpty()) {
			TextIOUtils.doOutput("No versions found for key, cannot continue");
			return;
		}
		ChoiceDescriptionData<KeyVersion> keyVersionCdd = new ChoiceDescriptionData<>(keyVersions.stream()
				.map(keyVersion -> new ChoiceDescription<KeyVersion>(keyVersion.toString(), keyVersion)).toList());
		KeyVersion keyVersion = TextIOUtils.getParamChoice("Please chose the key version", keyVersionCdd);
		TextIOUtils.doOutput("You chose key version " + keyVersion.toString());

		List<Secret> secrets = vaultProcessor.listSecrets(vault);

		ChoiceDescriptionData<Secret> secretCdd = new ChoiceDescriptionData<>(
				secrets.stream().map(secret -> new ChoiceDescription<Secret>(secret.getSecretName(), secret)).toList());
		Secret secret = TextIOUtils.getParamChoice("Please chose the secret", secretCdd);
		TextIOUtils.doOutput("You chose secret " + secret.toString());

		SecretVersion secretVersion = listAndChoseSecretVersion(vaultProcessor, secret);
		TextIOUtils.doOutput("You chose secret version " + secretVersion.toString());
		TextIOUtils.doOutput("getting contents of current version");

		String secretVersionContents = vaultProcessor.getSecretContents(secretVersion);
		TextIOUtils.doOutput("The secret version contents are " + secretVersionContents);

		SecretVersion secretVersionCurrent = vaultProcessor.getSecretVersionCurrent(secret);
		TextIOUtils.doOutput("Current version of secret is " + secretVersionCurrent);
		SecretVersion secretVersionLatest = vaultProcessor.getSecretVersionLatest(secret);
		TextIOUtils.doOutput("Latest version of secret is " + secretVersionLatest);
		List<SecretVersion> secretVersionsPending = vaultProcessor.listSecretVersionsPending(secret);
		TextIOUtils.doOutput("Pending versions of secret is "
				+ secretVersionsPending.stream().map(sv -> "V " + sv.getVersionNumber() + "(" + sv.getStages() + ")")
						.collect(Collectors.joining("\n")));
		List<SecretVersion> secretVersionsDeleted = vaultProcessor.listSecretVersionsDeleted(secret);
		TextIOUtils.doOutput("Deprecated versions of secret are "
				+ secretVersionsDeleted.stream().map(sv -> "V " + sv.getVersionNumber() + "(" + sv.getStages() + ")")
						.collect(Collectors.joining("\n")));

		Secret testSecret = null;
		SecretVersion testSecretVersionRetrieved = null;
		SecretVersion updatedSecretVersion = null;
		if (TextIOUtils.getYN("Do you want to test creating a secret using the vault & key you identified above ?",
				false)) {
			if (TextIOUtils.getYN("Do you want to test creating a manually specified secret?", false)) {
				String testSecretName = TextIOUtils.getString("Please enter the secret name");
				String testSecretVersionContents = TextIOUtils.getString("Please enter the secrets initial contents");
				String testSecretVersionName = TextIOUtils
						.getString("Please enter the secrets initial version name (or empty string for no name)", "");
				if (testSecretVersionName.isEmpty()) {
					testSecretVersionName = null;
				}
				String testSecretDescription = TextIOUtils
						.getString("Please enter the secrets initial description (or empty string for no name)", "");
				if (testSecretDescription.isEmpty()) {
					testSecretDescription = null;
				}
				testSecret = vaultProcessor.createSecretManual(compartment.getId(), testSecretName, key.getId(),
						vault.getId(), testSecretVersionContents, testSecretVersionName, testSecretDescription);
				testSecretVersionRetrieved = vaultProcessor.getSecretVersionCurrent(testSecret);
				String testSecretVersionRetrievedContents = vaultProcessor
						.getSecretContents(testSecretVersionRetrieved);
				TextIOUtils.doOutput("You created the secret " + testSecret.toString());
				TextIOUtils.doOutput("It's current version is  " + testSecretVersionRetrieved.toString());
				TextIOUtils.doOutput("It's current version contents is  " + testSecretVersionRetrievedContents);
			} else if (TextIOUtils.getYN("Do you want to test creating a generated pass phrase secret?", false)) {
				String testSecretName = TextIOUtils.getString("Please enter the secret name");
				int testSecretLength = TextIOUtils.getInt("Please enter the secrets length contents",
						NUM_TYPE.AT_OR_ABOVE, 14, 128);
				String testSecretDescription = TextIOUtils
						.getString("Please enter the secrets initial description (or empty string for no name)", "");
				if (testSecretDescription.isEmpty()) {
					testSecretDescription = null;
				}
				testSecret = vaultProcessor.createSecretGeneratedPassphrase(compartment.getId(), testSecretName,
						key.getId(), vault.getId(), testSecretLength, testSecretDescription, null);
				testSecretVersionRetrieved = vaultProcessor.getSecretVersionCurrent(testSecret);
				String testSecretVersionRetrievedContents = vaultProcessor
						.getSecretContents(testSecretVersionRetrieved);
				TextIOUtils.doOutput("You created the secret " + testSecret.toString());
				TextIOUtils.doOutput("It's current version is  " + testSecretVersionRetrieved.toString());
				TextIOUtils.doOutput("It's current version contents is  " + testSecretVersionRetrievedContents);
			}
		}
		// check for it still being in the creating state
		if (testSecret != null) {
			while (!testSecret.getLifecycleState().equals(Secret.LifecycleState.Active)) {
				TextIOUtils.doOutput("Secret is not active, waiting 10 secs before retry");
				Thread.sleep(10000);
				testSecret = vaultProcessor.getSecret(testSecret.getId());
			}
		}
		TextIOUtils.doOutput("Loading versions of secret");
		SecretVersion initialSecretVersion = listAndChoseSecretVersion(vaultProcessor, testSecret);
		String initialSecretVersionContents = vaultProcessor.getSecretContents(initialSecretVersion);
		TextIOUtils.doOutput("Version is " + initialSecretVersion.toString());
		TextIOUtils.doOutput("Content is " + initialSecretVersionContents);
		if (testSecret != null) {
			if (TextIOUtils.getYN("Do you want to test creating a secret version of the secret you just created ?",
					false)) {
				Secret updateSecret;
				SecretGenerationContext secretGenerationContextRetrieved = testSecret.getSecretGenerationContext();
				if (secretGenerationContextRetrieved != null) {
					TextIOUtils.doOutput(
							"Secret generation details are " + testSecret.getSecretGenerationContext().toString());
				} else {
					TextIOUtils.doOutput("Secret generation details are not present, probabaly manuall entered");

				}
				if (!testSecret.getIsAutoGenerationEnabled()) {
					String updateSecretContents = TextIOUtils.getString("Please enter the new contents for the secret");
					String updateSecretName = TextIOUtils.getString(
							"Please enter the name for the secret version (or empty string for no name)", "");
					if (updateSecretName.isEmpty()) {
						updateSecretName = null;
					}
					String updateSecretDescription = TextIOUtils
							.getString("Please enter the secrets new description (or empty string for no name)", "");
					if (updateSecretDescription.isEmpty()) {
						updateSecretDescription = null;
					}
					boolean setToCurrent = TextIOUtils
							.getYN("Do you want to make this the current version (Y) or pending (N)", true);
					if (setToCurrent) {
						updateSecret = vaultProcessor.createSecretVersionManualAsCurrent(testSecret.getId(),
								updateSecretContents, updateSecretName, updateSecretDescription);
					} else {
						updateSecret = vaultProcessor.createSecretVersionManualAsPending(testSecret.getId(),
								updateSecretContents, updateSecretName, updateSecretDescription);
					}
					// get the latest version we just created
					updatedSecretVersion = vaultProcessor.getSecretVersionLatest(updateSecret);
					TextIOUtils.doOutput("You updated the secret " + updateSecret.toString());
					TextIOUtils.doOutput("It's latest version is  " + updatedSecretVersion.toString());
					TextIOUtils.doOutput("Waiting up to 120 seconds for secret to become active");
					Boolean updatesSecretStatus = vaultProcessor.waitForSecretToBecomeActive(updateSecret);
					if (updatesSecretStatus == null) {
						TextIOUtils.doOutput("Secret is not found or update times out");
					} else if (updatesSecretStatus) {
						TextIOUtils.doOutput("Secret update timed out");
					} else {
						String updatedSecretVersionRetrievedContents = vaultProcessor
								.getSecretContents(updatedSecretVersion);
						TextIOUtils.doOutput("Secret updated, it's current version contents is  "
								+ updatedSecretVersionRetrievedContents);
					}
				} else {
					TextIOUtils.doOutput("Secret was generated");
					String updateSecretDescription = TextIOUtils
							.getString("Please enter the secrets new description (or empty string for no name)", "");
					if (updateSecretDescription.isEmpty()) {
						updateSecretDescription = null;
					}
					// at least for now there seems to be no way (or at least nothing documented) on
					// making a generated secret version pending (vs current) - this is despite the
					// oci cli and web ui supporting creating a pending version
					updateSecret = vaultProcessor.createSecretVersionGeneratedCurrent(testSecret.getId(),
							updateSecretDescription);
					// get the latest version we just created
					updatedSecretVersion = vaultProcessor.getSecretVersionLatest(updateSecret);
					TextIOUtils.doOutput("You updated the secret " + updateSecret.toString());
					TextIOUtils.doOutput("It's latest version is  " + updatedSecretVersion.toString());
					TextIOUtils.doOutput("Waiting up to 120 seconds for secret to become active");
					Boolean updatesSecretStatus = vaultProcessor.waitForSecretToBecomeActive(updateSecret);
					if (updatesSecretStatus == null) {
						TextIOUtils.doOutput("Secret is not found or update times out");
					} else if (updatesSecretStatus) {
						TextIOUtils.doOutput("Secret update timed out");
					} else {
						String updatedSecretVersionRetrievedContents = vaultProcessor
								.getSecretContents(updatedSecretVersion);
						TextIOUtils.doOutput("Secret updated, it's current version contents is  "
								+ updatedSecretVersionRetrievedContents);
					}
				}
				TextIOUtils.doOutput("Loading versions of secret");
				SecretVersion newSecretVersion = listAndChoseSecretVersion(vaultProcessor, testSecret);
				String newSecretVersionContents = vaultProcessor.getSecretContents(newSecretVersion);
				TextIOUtils.doOutput("Version is " + newSecretVersion.toString());
				TextIOUtils.doOutput("Content is " + newSecretVersionContents);
			}
		}
		if (updatedSecretVersion != null) {
			if (updatedSecretVersion.getStages().contains(SecretVersion.Stages.Pending)) {
				if (TextIOUtils.getYN("New version is pending, do you want to make it current", true)) {
					Secret updatedSecret = vaultProcessor.updateSecretVersionPendingToCurrent(updatedSecretVersion);
					updatedSecretVersion = vaultProcessor.getSecretVersionCurrent(updatedSecretVersion.getSecretId());
					TextIOUtils.doOutput("new version details are " + updatedSecretVersion);
					Boolean updatesSecretStatus = vaultProcessor.waitForSecretToBecomeActive(updatedSecret);
					if (updatesSecretStatus == null) {
						TextIOUtils.doOutput("Secret is not found or update times out");
					} else if (updatesSecretStatus) {
						TextIOUtils.doOutput("Secret update timed out");
					} else {
						String updatedSecretVersionRetrievedContents = vaultProcessor
								.getSecretContents(updatedSecretVersion);
						TextIOUtils.doOutput("Secret updated, it's current version contents is  "
								+ updatedSecretVersionRetrievedContents);
					}

					TextIOUtils
							.doOutput("Secret contents is " + vaultProcessor.getSecretContents(updatedSecretVersion));
				} else {
					TextIOUtils.doOutput("OK, leaving new version as pending");
				}
			} else {
				TextIOUtils.doOutput("New version is already current, can't promote it from pending ");
			}
		}
		if (updatedSecretVersion != null) {
			if (updatedSecretVersion.getStages().contains(SecretVersion.Stages.Current)) {
				TextIOUtils.doOutput("Can't delete just created version as it's current");
			} else {
				if (TextIOUtils.getYN("Do you want to delete the non current secret version you just created ?",
						true)) {
					ZonedDateTime manualSecretDeletionDTG = TextIOUtils.getISOZonedDateTimeTimeZone(
							"Please enter the deletion time (should be  at least one day in the future!)",
							ZoneId.systemDefault(), LocalDateTime.now().plusDays(1).plusMinutes(10),
							LocalDateTime.now().plusWeeks(4));
					boolean deleteTestSecretVersionResp = vaultProcessor.scheduleSecretVersionDeletion(testSecret,
							updatedSecretVersion.getVersionNumber(), manualSecretDeletionDTG);
					TextIOUtils.doOutput("Delete request reponse was " + deleteTestSecretVersionResp);
				}
			}
		}
		if (testSecret != null) {
			if (TextIOUtils.getYN("Do you want to delete the manual contents secret you just created ?", true)) {
				ZonedDateTime manualSecretDeletionDTG = TextIOUtils.getISOZonedDateTimeTimeZone(
						"Please enter the deletion time (should be at least one day in the future!)",
						ZoneId.systemDefault(), LocalDateTime.now().plusDays(1).plusMinutes(10),
						LocalDateTime.now().plusWeeks(4));
				boolean deleteManualSecretResp = vaultProcessor.scheduleSecretDeletion(testSecret,
						manualSecretDeletionDTG);
				TextIOUtils.doOutput("Delete request reponse was " + deleteManualSecretResp);
			}
		}
	}

	/**
	 * @param vaultProcessor
	 * @param secret
	 * @return
	 * @throws IOException
	 */
	private static SecretVersion listAndChoseSecretVersion(VaultProcessor vaultProcessor, Secret secret)
			throws IOException {
		List<SecretVersion> secretVersions = vaultProcessor.listSecretVersions(secret);
		ChoiceDescriptionData<SecretVersion> secretVersionCdd = new ChoiceDescriptionData<>(secretVersions.stream()
				.map(secretVersion -> new ChoiceDescription<SecretVersion>(
						"Version " + secretVersion.getVersionNumber() + "(" + secretVersion.getStages() + ")",
						secretVersion))
				.toList());
		SecretVersion secretVersion = TextIOUtils.getParamChoice("Please chose the secret version", secretVersionCdd);
		return secretVersion;
	}
}
