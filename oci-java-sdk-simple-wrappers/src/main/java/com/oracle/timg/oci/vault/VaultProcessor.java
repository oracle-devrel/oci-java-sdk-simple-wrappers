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
package com.oracle.timg.oci.vault;

import java.io.IOException;
import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.Arrays;
import java.util.Base64;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.StreamSupport;

import org.apache.http.HttpStatus;

import com.oracle.bmc.identity.model.Compartment;
import com.oracle.bmc.keymanagement.KmsManagementClient;
import com.oracle.bmc.keymanagement.KmsVaultClient;
import com.oracle.bmc.keymanagement.model.Key;
import com.oracle.bmc.keymanagement.model.KeySummary;
import com.oracle.bmc.keymanagement.model.KeyVersion;
import com.oracle.bmc.keymanagement.model.KeyVersionSummary;
import com.oracle.bmc.keymanagement.model.Vault;
import com.oracle.bmc.keymanagement.model.VaultSummary;
import com.oracle.bmc.keymanagement.requests.GetKeyRequest;
import com.oracle.bmc.keymanagement.requests.GetKeyVersionRequest;
import com.oracle.bmc.keymanagement.requests.GetVaultRequest;
import com.oracle.bmc.keymanagement.requests.ListKeyVersionsRequest;
import com.oracle.bmc.keymanagement.requests.ListKeysRequest;
import com.oracle.bmc.keymanagement.requests.ListVaultsRequest;
import com.oracle.bmc.keymanagement.responses.GetKeyResponse;
import com.oracle.bmc.keymanagement.responses.GetKeyVersionResponse;
import com.oracle.bmc.keymanagement.responses.GetVaultResponse;
import com.oracle.bmc.secrets.SecretsClient;
import com.oracle.bmc.secrets.model.Base64SecretBundleContentDetails;
import com.oracle.bmc.secrets.requests.GetSecretBundleRequest;
import com.oracle.bmc.secrets.responses.GetSecretBundleResponse;
import com.oracle.bmc.vault.VaultsClient;
import com.oracle.bmc.vault.model.Base64SecretContentDetails;
import com.oracle.bmc.vault.model.BytesGenerationContext;
import com.oracle.bmc.vault.model.CreateSecretDetails;
import com.oracle.bmc.vault.model.PassphraseGenerationContext;
import com.oracle.bmc.vault.model.ScheduleSecretDeletionDetails;
import com.oracle.bmc.vault.model.ScheduleSecretVersionDeletionDetails;
import com.oracle.bmc.vault.model.Secret;
import com.oracle.bmc.vault.model.SecretGenerationContext;
import com.oracle.bmc.vault.model.SecretSummary;
import com.oracle.bmc.vault.model.SecretVersion;
import com.oracle.bmc.vault.model.SecretVersionSummary;
import com.oracle.bmc.vault.model.SshKeyGenerationContext;
import com.oracle.bmc.vault.model.UpdateSecretDetails;
import com.oracle.bmc.vault.requests.CancelSecretDeletionRequest;
import com.oracle.bmc.vault.requests.CancelSecretVersionDeletionRequest;
import com.oracle.bmc.vault.requests.CreateSecretRequest;
import com.oracle.bmc.vault.requests.GetSecretRequest;
import com.oracle.bmc.vault.requests.GetSecretVersionRequest;
import com.oracle.bmc.vault.requests.ListSecretVersionsRequest;
import com.oracle.bmc.vault.requests.ListSecretsRequest;
import com.oracle.bmc.vault.requests.ListSecretsRequest.SortBy;
import com.oracle.bmc.vault.requests.ScheduleSecretDeletionRequest;
import com.oracle.bmc.vault.requests.ScheduleSecretVersionDeletionRequest;
import com.oracle.bmc.vault.requests.UpdateSecretRequest;
import com.oracle.bmc.vault.responses.CancelSecretDeletionResponse;
import com.oracle.bmc.vault.responses.CancelSecretVersionDeletionResponse;
import com.oracle.bmc.vault.responses.CreateSecretResponse;
import com.oracle.bmc.vault.responses.GetSecretResponse;
import com.oracle.bmc.vault.responses.GetSecretVersionResponse;
import com.oracle.bmc.vault.responses.ScheduleSecretDeletionResponse;
import com.oracle.bmc.vault.responses.ScheduleSecretVersionDeletionResponse;
import com.oracle.bmc.vault.responses.UpdateSecretResponse;
import com.oracle.timg.oci.authentication.AuthenticationProcessor;

import lombok.Getter;
import lombok.NonNull;

public class VaultProcessor {
	private final AuthenticationProcessor authProcessor;
	@Getter
	private final KmsVaultClient kmsVaultClient;
	@Getter
	private final VaultsClient vaultClient;
	@Getter
	private final SecretsClient secretsClient;

	/**
	 * Creates a VCNProcessor which will use the supplied AuthenticationProcessor
	 * 
	 * @param authProcessor
	 * @throws IllegalArgumentException
	 * @throws IOException
	 */
	public VaultProcessor(AuthenticationProcessor authProcessor) throws IllegalArgumentException, IOException {
		this.authProcessor = authProcessor;
		kmsVaultClient = KmsVaultClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
		vaultClient = VaultsClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
		secretsClient = SecretsClient.builder().region(this.authProcessor.getRegionName())
				.build(this.authProcessor.getProvider());
	}

	/**
	 * allows you to change the region form the default provided by the
	 * AuthenticationProcessor
	 * 
	 * @param regionName
	 */
	public void setRegion(@NonNull String regionName) {
		kmsVaultClient.setRegion(regionName);
		vaultClient.setRegion(regionName);
	}

	/**
	 * 
	 * get the active vault summaries in the tenancy root in the active state only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @return
	 */
	public List<VaultSummary> listVaultSummaries() {
		return listVaultSummaries(authProcessor.getTenancyOCID(), null, VaultSummary.LifecycleState.Active);
	}

	/**
	 * 
	 * get the active vault summaries in the compartment with the specified ocid in
	 * the active state only
	 * 
	 * @param parentCompartment - must not be null
	 * @return
	 */
	public List<VaultSummary> listVaultSummaries(@NonNull Compartment parentCompartment) {
		return listVaultSummaries(parentCompartment.getId());
	}

	/**
	 * 
	 * get the active vault summaries in the compartment with the specified ocid in
	 * the active state only 720/4.8
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @return
	 */
	public List<VaultSummary> listVaultSummaries(@NonNull String parentCompartmentOcid) {
		return listVaultSummaries(parentCompartmentOcid, null, VaultSummary.LifecycleState.Active);
	}

	/**
	 * 
	 * get the active vault summaries in the compartment with the specified ocid in
	 * the active state and if it's not null matching the displayName only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @param displayName           the name to look for (or all if not specified)
	 * @return
	 */
	public List<VaultSummary> listVaultSummaries(@NonNull String parentCompartmentOcid, String displayName) {
		return listVaultSummaries(parentCompartmentOcid, displayName, VaultSummary.LifecycleState.Active);
	}

	/**
	 * 
	 * get the vault summaries in the compartment with the specified ocid in the
	 * specified state if null in all states) and with the displayName (if null all
	 * names)
	 * 
	 * @param parentCompartment - must not be null
	 * @param displayName       the name to look for (or all if not specified)
	 * @return
	 */
	public List<VaultSummary> listVaultSummaries(@NonNull Compartment parentCompartment, String displayName,
			VaultSummary.LifecycleState livecycleState) {
		return listVaultSummaries(parentCompartment.getId(), displayName, livecycleState);
	}

	/**
	 * 
	 * get the vault summaries in the compartment with the specified ocid in the
	 * specified state only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @param displayName           the name to look for (or all if not specified)
	 * @return
	 */
	public List<VaultSummary> listVaultSummaries(@NonNull String parentCompartmentOcid, String displayName,
			VaultSummary.LifecycleState livecycleState) {
		ListVaultsRequest.Builder requestBuilder = ListVaultsRequest.builder().compartmentId(parentCompartmentOcid)
				.sortBy(ListVaultsRequest.SortBy.Displayname).sortOrder(ListVaultsRequest.SortOrder.Asc);
		Iterable<VaultSummary> vaultsResponse = kmsVaultClient.getPaginators()
				.listVaultsRecordIterator(requestBuilder.build());
		return StreamSupport.stream(vaultsResponse.spliterator(), false).filter((vaultSummary) -> {
			return (livecycleState == null) || (vaultSummary.getLifecycleState() == livecycleState);
		}).filter((vaultsummary) -> {
			return ((displayName == null) || (vaultsummary.getDisplayName().equals(displayName)));
		}).toList();
	}

	/**
	 * 
	 * get the active vaults in the tenancy root in the active state only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @return
	 */
	public List<Vault> listVaults() {
		return listVaults(authProcessor.getTenancyOCID(), null, Vault.LifecycleState.Active);
	}

	/**
	 * 
	 * get the active vaults in the compartment with the specified ocid in the
	 * active state only
	 * 
	 * @param parentCompartment - must not be null
	 * @return
	 */
	public List<Vault> listVaults(@NonNull Compartment parentCompartment) {
		return listVaults(parentCompartment.getId());
	}

	/**
	 * 
	 * get the active vaults in the compartment with the specified ocid in the
	 * active state only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @return
	 */
	public List<Vault> listVaults(@NonNull String parentCompartmentOcid) {
		return listVaults(parentCompartmentOcid, null, Vault.LifecycleState.Active);
	}

	/**
	 * 
	 * get the active vault in the compartment with the specified ocid in the active
	 * state and if it's not null matching the displayName only
	 * 
	 * @param parentCompartment - must not be null
	 * @param displayName       the name to look for (or all if not specified)
	 * @return
	 */
	public List<Vault> listVaults(@NonNull Compartment parentCompartment, String displayName) {
		return listVaults(parentCompartment.getId(), displayName, Vault.LifecycleState.Active);
	}

	/**
	 * 
	 * get the active vaults in the compartment with the specified ocid in the
	 * active state and if it's not null matching the displayName only
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @param displayName           the name to look for (or all if not specified)
	 * @return
	 */
	public List<Vault> listVaults(@NonNull String parentCompartmentOcid, String displayName) {
		return listVaults(parentCompartmentOcid, displayName, Vault.LifecycleState.Active);
	}

	/**
	 * 
	 * get the vaults in the compartment with the specified ocid in the specified
	 * state if null in all states) and with the displayName (if null all names)
	 * 
	 * @param parentCompartment - must not be null
	 * @param displayName       the name to look for (or all if not specified)
	 * @return
	 */
	public List<Vault> listVaults(@NonNull Compartment parentCompartment, String displayName,
			Vault.LifecycleState livecycleState) {
		return listVaults(parentCompartment.getId(), displayName, livecycleState);
	}

	/**
	 * 
	 * get the vaults in the compartment with the specified ocid in the specified
	 * state and matching the displayName if it's non null
	 * 
	 * @param parentCompartmentOcid - must not be null
	 * @return
	 */
	public List<Vault> listVaults(@NonNull String parentCompartmentOcid, String displayName,
			Vault.LifecycleState lifecycleState) {
		VaultSummary.LifecycleState vaultSummaryLifecycleState = lifecycleState == null ? null
				: VaultSummary.LifecycleState.create(lifecycleState.getValue());
		List<VaultSummary> vaultSummaries = listVaultSummaries(parentCompartmentOcid, displayName,
				vaultSummaryLifecycleState);
		return vaultSummaries.stream().map(vs -> getVaultFromVaultSummary(vs)).toList();
	}

	/**
	 * gets the first matching vault with displayname in the compartment in active
	 * state, returns null if not found
	 * 
	 * @param compartment
	 * @param displayName
	 * @return
	 */
	public Vault getVaultByName(@NonNull Compartment compartment, @NonNull String displayName) {
		return getVaultByName(compartment, displayName, Vault.LifecycleState.Active);
	}

	/**
	 * gets the first matching vault with displayname in the compartment in active
	 * state, returns null if not found
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * @return
	 */
	public Vault getVaultByName(@NonNull String parentCompartmentOcid, @NonNull String displayName) {
		return getVaultByName(parentCompartmentOcid, displayName, Vault.LifecycleState.Active);
	}

	/**
	 * gets the first matching vault with displayname in the compartment in the
	 * specified state (null matches all states), returns null if not found
	 * 
	 * @param compartment
	 * @param displayName
	 * @return
	 */
	public Vault getVaultByName(@NonNull Compartment compartment, @NonNull String displayName,
			Vault.LifecycleState lifecycleState) {
		return getVaultByName(compartment.getId(), displayName, lifecycleState);
	}

	/**
	 * gets the first matching vault with displayname in the compartment in the
	 * specified state (null matches all states), returns null if not found
	 * 
	 * @param parentCompartmentOcid
	 * @param displayName
	 * @return
	 */
	public Vault getVaultByName(@NonNull String parentCompartmentOcid, @NonNull String displayName,
			Vault.LifecycleState lifecycleState) {
		List<Vault> vaults = listVaults(parentCompartmentOcid, displayName, lifecycleState);
		if (vaults.isEmpty()) {
			return null;
		}
		return vaults.getFirst();
	}

	public Vault getVaultFromVaultSummary(@NonNull VaultSummary vaultSummary) {
		return getVaultFromVaultSummary(vaultSummary.getId());
	}

	public Vault getVaultFromVaultSummary(@NonNull String vaultSummaryOcid) {
		return getVault(vaultSummaryOcid);
	}

	public Vault getVault(@NonNull String vaultOcid) {
		GetVaultRequest request = GetVaultRequest.builder().vaultId(vaultOcid).build();
		GetVaultResponse response = kmsVaultClient.getVault(request);
		return response.getVault();
	}

	private Map<String, KmsManagementClient> perVaultKmsManagementClient = new HashMap<>();

	private KmsManagementClient getKmsManagementClientForVault(@NonNull VaultSummary vaultSummary) {
		return getKmsManagementClientForVault(vaultSummary.getId(), vaultSummary.getManagementEndpoint());
	}

	private KmsManagementClient getKmsManagementClientForVault(@NonNull Vault vault) {
		return getKmsManagementClientForVault(vault.getId(), vault.getManagementEndpoint());
	}

	private KmsManagementClient getKmsManagementClientForVault(@NonNull String vaultOcid,
			@NonNull String vaultEndpoint) {
		if (perVaultKmsManagementClient.containsKey(vaultOcid)) {
			return perVaultKmsManagementClient.get(vaultOcid);
		} else {
			KmsManagementClient kmc = KmsManagementClient.builder().endpoint(vaultEndpoint)
					.build(this.authProcessor.getProvider());
			this.perVaultKmsManagementClient.put(vaultOcid, kmc);
			return kmc;
		}
	}

	/**
	 * return all master keys in the vault which are enabled and the same
	 * compartment as the vault
	 * 
	 * @param vault
	 * @return
	 */
	public List<KeySummary> listKeySummaries(@NonNull VaultSummary vaultSummary) {
		return listKeySummaries(vaultSummary, vaultSummary.getCompartmentId(), null, KeySummary.LifecycleState.Enabled);
	}

	/**
	 * return all master keys in the vault which are enabled and the same
	 * compartment as the vault and have the specified name, of all if displayName
	 * is null
	 * 
	 * @param vault
	 * @return
	 */
	public List<KeySummary> listKeySummaries(@NonNull VaultSummary vaultSummary, String displayName) {
		return listKeySummaries(vaultSummary, vaultSummary.getCompartmentId(), displayName,
				KeySummary.LifecycleState.Enabled);
	}

	public List<KeySummary> listKeySummaries(@NonNull VaultSummary vaultSummary, @NonNull String parentCompartmentOcid,
			String displayName, KeySummary.LifecycleState lifecycleState) {
		// this is vault specific, so we need one for each vault
		KmsManagementClient kmsManagementClient = getKmsManagementClientForVault(vaultSummary);
		return listKeySummaries(kmsManagementClient, parentCompartmentOcid, displayName, lifecycleState);
	}

	private List<KeySummary> listKeySummaries(@NonNull KmsManagementClient kmsManagementClient,
			@NonNull String parentCompartmentOcid, String displayName, KeySummary.LifecycleState lifecycleState) {

		// keys are in a compartment and a vault, the key management client is
		// associated to the vault.
		ListKeysRequest request = ListKeysRequest.builder().compartmentId(parentCompartmentOcid)
				.sortBy(ListKeysRequest.SortBy.Displayname).sortOrder(ListKeysRequest.SortOrder.Asc).build();
		// this is vault specific, so we need one for each vault
		Iterable<KeySummary> response = kmsManagementClient.getPaginators().listKeysRecordIterator(request);
		return StreamSupport.stream(response.spliterator(), false).filter((keySummary) -> {
			return ((lifecycleState == null) || (keySummary.getLifecycleState() == lifecycleState));
		}).filter((vaultsummary) -> {
			return ((displayName == null) || (vaultsummary.getDisplayName().equals(displayName)));
		}).toList();
	}

	/**
	 * return all master keys in the vault which are enabled and the same
	 * compartment as the vault
	 * 
	 * @param vault
	 * @return
	 */
	public List<KeySummary> listKeySummaries(@NonNull Vault vault) {
		return listKeySummaries(vault, vault.getCompartmentId(), null, KeySummary.LifecycleState.Enabled);
	}

	/**
	 * return all master keys in the vault which are enabled and the same
	 * compartment as the vault and have the specified name, of all if displayName
	 * is null
	 * 
	 * @param vault
	 * @return
	 */
	public List<KeySummary> listKeySummaries(@NonNull Vault vault, String displayName) {
		return listKeySummaries(vault, vault.getCompartmentId(), displayName, KeySummary.LifecycleState.Enabled);
	}

	public List<KeySummary> listKeySummaries(@NonNull Vault vault, @NonNull String parentCompartmentOcid,
			String displayName, KeySummary.LifecycleState lifecycleState) {
		KmsManagementClient kmsManagementClient = getKmsManagementClientForVault(vault);
		return listKeySummaries(kmsManagementClient, parentCompartmentOcid, displayName, lifecycleState);
	}

	public Key getKey(@NonNull VaultSummary vaultSummary, @NonNull KeySummary keySummary) {
		return getKey(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), keySummary.getId());
	}

	public Key getKey(@NonNull VaultSummary vaultSummary, @NonNull String keyOcid) {
		return getKey(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), keyOcid);
	}

	public Key getKeyByName(@NonNull VaultSummary vaultSummary, @NonNull String keyName) {
		KmsManagementClient kmsManagementClient = getKmsManagementClientForVault(vaultSummary);
		return getKeyByName(kmsManagementClient, vaultSummary.getCompartmentId(), keyName);
	}

	public Key getKey(@NonNull Vault vault, @NonNull KeySummary keySummary) {
		return getKey(vault.getId(), vault.getManagementEndpoint(), keySummary.getId());
	}

	public Key getKey(@NonNull Vault vault, @NonNull String keyOcid) {
		return getKey(vault.getId(), vault.getManagementEndpoint(), keyOcid);
	}

	public Key getKeyByName(@NonNull Vault vault, @NonNull String keyName) {
		KmsManagementClient kmsManagementClient = getKmsManagementClientForVault(vault);
		return getKeyByName(kmsManagementClient, vault.getCompartmentId(), keyName);
	}

	public Key getKeyByName(@NonNull KmsManagementClient kmsManagementClient, @NonNull String parentCompartmentOcid,
			@NonNull String keyName) {
		return getKeyByName(kmsManagementClient, parentCompartmentOcid, keyName, KeySummary.LifecycleState.Enabled);
	}

	public Key getKeyByName(@NonNull KmsManagementClient kmsManagementClient, @NonNull String parentCompartmentOcid,
			@NonNull String keyName, KeySummary.LifecycleState lifecycleState) {
		List<KeySummary> summaries = listKeySummaries(kmsManagementClient, parentCompartmentOcid, keyName,
				lifecycleState);
		if (summaries.isEmpty()) {
			return null;
		}
		KeySummary first = summaries.getFirst();
		return getKey(first.getVaultId(), kmsManagementClient.getEndpoint(), first.getId());
	}

	public Key getKey(@NonNull String vaultOcid, @NonNull String vaultEndpoint, @NonNull String keyOcid) {
		GetKeyRequest request = GetKeyRequest.builder().keyId(keyOcid).build();
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultOcid, vaultEndpoint);
		GetKeyResponse response = kmvManagementClient.getKey(request);
		return response.getKey();
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull VaultSummary vaultSummary,
			@NonNull KeySummary keySummary) {
		return listKeyVersionSummaries(vaultSummary, keySummary.getId(), KeyVersionSummary.LifecycleState.Enabled);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull VaultSummary vaultSummary, @NonNull Key key) {
		return listKeyVersionSummaries(vaultSummary, key.getId(), KeyVersionSummary.LifecycleState.Enabled);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull VaultSummary vaultSummary,
			@NonNull String keyOcid) {
		return listKeyVersionSummaries(vaultSummary, keyOcid, KeyVersionSummary.LifecycleState.Enabled);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull Vault vault, @NonNull KeySummary keySummary) {
		return listKeyVersionSummaries(vault, keySummary, KeyVersionSummary.LifecycleState.Enabled);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull Vault vault, @NonNull Key key) {
		return listKeyVersionSummaries(vault, key.getId(), KeyVersionSummary.LifecycleState.Enabled);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull Vault vault, @NonNull String keyOcid) {
		return listKeyVersionSummaries(vault, keyOcid, KeyVersionSummary.LifecycleState.Enabled);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull VaultSummary vaultSummary,
			@NonNull KeySummary keySummary, @NonNull KeyVersionSummary.LifecycleState lifecycleState) {
		return listKeyVersionSummaries(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), keySummary.getId(),
				lifecycleState);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull VaultSummary vaultSummary, @NonNull String keyOcid,
			@NonNull KeyVersionSummary.LifecycleState lifecycleState) {
		return listKeyVersionSummaries(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), keyOcid,
				lifecycleState);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull Vault vault, @NonNull KeySummary keySummary,
			@NonNull KeyVersionSummary.LifecycleState lifecycleState) {
		return listKeyVersionSummaries(vault.getId(), vault.getManagementEndpoint(), keySummary.getId(),
				lifecycleState);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull Vault vault, @NonNull String keyOcid,
			@NonNull KeyVersionSummary.LifecycleState lifecycleState) {
		return listKeyVersionSummaries(vault.getId(), vault.getManagementEndpoint(), keyOcid, lifecycleState);
	}

	public List<KeyVersionSummary> listKeyVersionSummaries(@NonNull String vaultOcid, @NonNull String vaultEndpoint,
			@NonNull String keyId, KeyVersionSummary.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultOcid, vaultEndpoint);
		return listKeyVersionSummaries(kmvManagementClient, keyId, lifecycleState);
	}

	private List<KeyVersionSummary> listKeyVersionSummaries(@NonNull KmsManagementClient kmsManagementClient,
			@NonNull String keyId, KeyVersionSummary.LifecycleState lifecycleState) {
		ListKeyVersionsRequest request = ListKeyVersionsRequest.builder().keyId(keyId)
				.sortBy(ListKeyVersionsRequest.SortBy.Timecreated).sortOrder(ListKeyVersionsRequest.SortOrder.Desc)
				.build();
		// this is vault specific, so we need one for each vault
		Iterable<KeyVersionSummary> response = kmsManagementClient.getPaginators()
				.listKeyVersionsRecordIterator(request);
		return StreamSupport.stream(response.spliterator(), false).filter((keyVersionSummary) -> {
			return ((lifecycleState == null) || (keyVersionSummary.getLifecycleState() == lifecycleState));
		}).toList();
	}

	public List<KeyVersion> listKeyVersions(@NonNull VaultSummary vaultSummary, @NonNull KeySummary keySummary) {
		return listKeyVersions(vaultSummary, keySummary.getId(), KeyVersion.LifecycleState.Enabled);
	}

	public List<KeyVersion> listKeyVersions(@NonNull VaultSummary vaultSummary, @NonNull Key key) {
		return listKeyVersions(vaultSummary, key.getId(), KeyVersion.LifecycleState.Enabled);
	}

	public List<KeyVersion> listKeyVersions(@NonNull VaultSummary vaultSummary, @NonNull String keyOcid) {
		return listKeyVersions(vaultSummary, keyOcid, KeyVersion.LifecycleState.Enabled);
	}

	public List<KeyVersion> listKeyVersions(@NonNull Vault vault, @NonNull KeySummary keySummary) {
		return listKeyVersions(vault, keySummary, KeyVersion.LifecycleState.Enabled);
	}

	public List<KeyVersion> listKeyVersions(@NonNull Vault vault, @NonNull Key key) {
		return listKeyVersions(vault, key.getId(), KeyVersion.LifecycleState.Enabled);
	}

	public List<KeyVersion> listKeyVersion(@NonNull Vault vault, @NonNull String keyOcid) {
		return listKeyVersions(vault, keyOcid, KeyVersion.LifecycleState.Enabled);
	}

	public List<KeyVersion> listKeyVersions(@NonNull VaultSummary vaultSummary, @NonNull KeySummary keySummary,
			KeyVersion.LifecycleState lifecycleState) {
		return listKeyVersions(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), keySummary.getId(),
				lifecycleState);
	}

	public List<KeyVersion> listKeyVersions(@NonNull VaultSummary vaultSummary, @NonNull String keyOcid,
			KeyVersion.LifecycleState lifecycleState) {
		return listKeyVersions(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), keyOcid, lifecycleState);
	}

	public List<KeyVersion> listKeyVersions(@NonNull Vault vault, @NonNull KeySummary keySummary,
			KeyVersion.LifecycleState lifecycleState) {
		return listKeyVersions(vault.getId(), vault.getManagementEndpoint(), keySummary.getId(), lifecycleState);
	}

	public List<KeyVersion> listKeyVersions(@NonNull Vault vault, @NonNull Key key,
			KeyVersion.LifecycleState lifecycleState) {
		return listKeyVersions(vault.getId(), vault.getManagementEndpoint(), key.getId(), lifecycleState);
	}

	public List<KeyVersion> listKeyVersions(@NonNull Vault vault, @NonNull String keyOcid,
			KeyVersion.LifecycleState lifecycleState) {
		return listKeyVersions(vault.getId(), vault.getManagementEndpoint(), keyOcid, lifecycleState);
	}

	public List<KeyVersion> listKeyVersions(@NonNull String vaultOcid, @NonNull String vaultEndpoint, String keyId,
			KeyVersion.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultOcid, vaultEndpoint);
		return listKeyVersions(kmvManagementClient, keyId, lifecycleState);
	}

	private List<KeyVersion> listKeyVersions(@NonNull KmsManagementClient kmsManagementClient, @NonNull String keyId,
			KeyVersion.LifecycleState lifecycleState) {
		KeyVersionSummary.LifecycleState kvsLifecycleState = lifecycleState == null ? null
				: KeyVersionSummary.LifecycleState.create(lifecycleState.getValue());
		List<KeyVersionSummary> keyVersionSummaries = listKeyVersionSummaries(kmsManagementClient, keyId,
				kvsLifecycleState);
		return keyVersionSummaries.stream()
				.map(keyVersionSummary -> getKeyVersion(kmsManagementClient, keyId, keyVersionSummary.getId()))
				.toList();
	}

	/**
	 * gets the first keyVersion of the key in the lifecycle state or if that's null
	 * the first version in the returned list (which is sorted by creation time in
	 * descending order, do this basically means the most recent version)
	 * 
	 * @param vault
	 * @param key
	 * @param lifecycleState
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull Vault vault, @NonNull Key key, KeyVersion.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vault.getId(),
				vault.getManagementEndpoint());
		return getKeyVersion(kmvManagementClient, key.getId(), lifecycleState);
	}

	/**
	 * gets the first keyVersion of the key in the lifecycle state or if that's null
	 * the first version in the returned list (which is sorted by creation time in
	 * descending order, do this basically means the most recent version)
	 * 
	 * @param vaultSummary
	 * @param key
	 * @param lifecycleState
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull VaultSummary vaultSummary, @NonNull Key key,
			KeyVersion.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultSummary.getId(),
				vaultSummary.getManagementEndpoint());
		return getKeyVersion(kmvManagementClient, key.getId(), lifecycleState);
	}

	/**
	 * gets the first keyVersion of the key in the lifecycle state or if that's null
	 * the first version in the returned list (which is sorted by creation time in
	 * descending order, do this basically means the most recent version)
	 * 
	 * @param vault
	 * @param keyOcid
	 * @param lifecycleState
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull Vault vault, @NonNull String keyOcid,
			KeyVersion.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vault.getId(),
				vault.getManagementEndpoint());
		return getKeyVersion(kmvManagementClient, keyOcid, lifecycleState);
	}

	/**
	 * gets the first keyVersion of the key in the lifecycle state or if that's null
	 * the first version in the returned list (which is sorted by creation time in
	 * descending order, do this basically means the most recent version)
	 * 
	 * @param vaultSummary
	 * @param keyOcid
	 * @param lifecycleState
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull VaultSummary vaultSummary, @NonNull String keyOcid,
			KeyVersion.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultSummary.getId(),
				vaultSummary.getManagementEndpoint());
		return getKeyVersion(kmvManagementClient, keyOcid, lifecycleState);
	}

	/**
	 * gets the first keyVersion of the key in the lifecycle state or if that's null
	 * the first version in the returned list (which is sorted by creation time in
	 * descending order, do this basically means the most recent version)
	 * 
	 * @param vaultOcid
	 * @param vaultEndpoint
	 * @param keyOcid
	 * @param lifecycleState
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull String vaultOcid, @NonNull String vaultEndpoint, @NonNull String keyOcid,
			KeyVersion.LifecycleState lifecycleState) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultOcid, vaultEndpoint);
		return getKeyVersion(kmvManagementClient, keyOcid, lifecycleState);
	}

	/**
	 * gets the first keyVersion of the key in the lifecycle state or if that's null
	 * the first version in the returned list (which is sorted by creation time in
	 * descending order, do this basically means the most recent version)
	 * 
	 * @param kmvManagementClient
	 * @param keyOcid
	 * @param lifecycleState
	 * @return
	 */
	private KeyVersion getKeyVersion(@NonNull KmsManagementClient kmvManagementClient, @NonNull String keyOcid,
			KeyVersion.LifecycleState lifecycleState) {
		KeyVersionSummary.LifecycleState kvsLifecycleState = lifecycleState == null ? null
				: KeyVersionSummary.LifecycleState.create(lifecycleState.getValue());
		List<KeyVersionSummary> keyVersionSummaries = listKeyVersionSummaries(kmvManagementClient, keyOcid,
				kvsLifecycleState);
		if (keyVersionSummaries.size() == 0) {
			return null;
		} else {
			return getKeyVersion(kmvManagementClient, keyOcid, keyVersionSummaries.getFirst().getId());
		}
	}

	/**
	 * get the key version from it's OCID in the specified key / vault
	 * 
	 * @param vaultSummary
	 * @param key
	 * @param keyVersionOcid
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull VaultSummary vaultSummary, @NonNull Key key,
			@NonNull String keyVersionOcid) {
		return getKeyVersion(vaultSummary.getId(), vaultSummary.getManagementEndpoint(), key.getId(), keyVersionOcid);
	}

	/**
	 * get the key version from it's OCID in the specified key / vault
	 * 
	 * @param vault
	 * @param key
	 * @param keyVersionOcid
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull Vault vault, @NonNull Key key, @NonNull String keyVersionOcid) {
		return getKeyVersion(vault.getId(), vault.getManagementEndpoint(), key.getId(), keyVersionOcid);
	}

	/**
	 * get the key version from it's OCID in the specified key / vault (as per the
	 * vault id / endpoint)
	 * 
	 * @param vaultOcid
	 * @param vaultEndpoint
	 * @param key
	 * @param keyVersionOcid
	 * @return
	 */
	public KeyVersion getKeyVersion(@NonNull String vaultOcid, @NonNull String vaultEndpoint, @NonNull String keyOcid,
			@NonNull String keyVersionOcid) {
		KmsManagementClient kmvManagementClient = getKmsManagementClientForVault(vaultOcid, vaultEndpoint);
		return getKeyVersion(kmvManagementClient, keyOcid, keyVersionOcid);
	}

	/**
	 * get the key version from it's OCID in the specified key / vault ar per the
	 * provided management client
	 * 
	 * @param kmvManagementClient
	 * @param key
	 * @param keyVersionOcid
	 * @return
	 */
	private KeyVersion getKeyVersion(@NonNull KmsManagementClient kmvManagementClient, @NonNull String keyOcid,
			@NonNull String keyVersionOcid) {
		GetKeyVersionRequest request = GetKeyVersionRequest.builder().keyId(keyOcid).keyVersionId(keyVersionOcid)
				.build();
		GetKeyVersionResponse response = kmvManagementClient.getKeyVersion(request);
		return response.getKeyVersion();
	}

	/**
	 * Get a list of the active secret summaries in the specified compartment, if
	 * any of the vault / name are specified then the returned list if filtered
	 * against those. The result is ordered by the secret name
	 * 
	 * @param compartmentOcid
	 * @param vaultOcid
	 * @param name
	 * @return
	 */
	public List<SecretSummary> listSecretSummaries(@NonNull String compartmentOcid, String vaultOcid, String name) {
		return listSecretSummaries(compartmentOcid, vaultOcid, name, SecretSummary.LifecycleState.Active);
	}

	/**
	 * Get a list of the secret summaries in the specified compartment, if any of
	 * the vault / name / lifecycle state are specified then the returned list if
	 * filtered against those. The result is ordered by the secret name
	 * 
	 * @param compartmentOcid
	 * @param vaultOcid
	 * @param name
	 * @param lifecycleState
	 * @return
	 */
	public List<SecretSummary> listSecretSummaries(@NonNull String compartmentOcid, String vaultOcid, String name,
			SecretSummary.LifecycleState lifecycleState) {
		ListSecretsRequest.Builder requestBuilder = ListSecretsRequest.builder().compartmentId(compartmentOcid)
				.sortBy(SortBy.Name).sortOrder(ListSecretsRequest.SortOrder.Asc);
		if (vaultOcid != null) {
			requestBuilder.vaultId(vaultOcid);
		}
		if (name != null) {
			requestBuilder.name(name);
		}
		if (lifecycleState != null) {
			requestBuilder.lifecycleState(lifecycleState);
		}
		Iterable<SecretSummary> response = vaultClient.getPaginators()
				.listSecretsRecordIterator(requestBuilder.build());
		return StreamSupport.stream(response.spliterator(), false).toList();
	}

	/**
	 * Get a list of the active secret summaries in the specified vault against
	 * that. The result is ordered by the secret name. The secrets must be in the
	 * same compartment as the vault
	 * 
	 * @param vaultSummary
	 * @param name
	 * @return
	 */
	public List<Secret> listSecrets(@NonNull VaultSummary vaultSummary) {
		return listSecrets(vaultSummary, null);
	}

	/**
	 * Get a list of the active secret summaries in the specified vault against
	 * that. The result is ordered by the secret name. The secrets must be in the
	 * same compartment as the vault
	 * 
	 * @param vault
	 * @param name
	 * @return
	 */
	public List<Secret> listSecrets(@NonNull Vault vault) {
		return listSecrets(vault, null);
	}

	/**
	 * Get a list of the active secret summaries in the specified vault, if name is
	 * non null then the returned list is filtered against that. The result is
	 * ordered by the secret name. The secrets must be in the same compartment as
	 * the vault
	 * 
	 * @param vaultSummary
	 * @param name
	 * @return
	 */
	public List<Secret> listSecrets(@NonNull VaultSummary vaultSummary, String name) {
		return listSecrets(vaultSummary.getCompartmentId(), vaultSummary.getId(), name);
	}

	/**
	 * Get a list of the active secret summaries in the specified vault, if name is
	 * non null then the returned list is filtered against that. The result is
	 * ordered by the secret name. The secrets must be in the same compartment as
	 * the vault
	 * 
	 * @param vault
	 * @param name
	 * @return
	 */
	public List<Secret> listSecrets(@NonNull Vault vault, String name) {
		return listSecrets(vault.getCompartmentId(), vault.getId(), name);
	}

	/**
	 * Get a list of the active secret summaries in the specified compartment, if
	 * any of the vault / name are specified then the returned list if filtered
	 * against those. The result is ordered by the secret name
	 * 
	 * @param compartmentOcid
	 * @param vaultOcid
	 * @param name
	 * @return
	 */
	public List<Secret> listSecrets(@NonNull String compartmentOcid, String vaultOcid, String name) {
		return listSecrets(compartmentOcid, vaultOcid, name, Secret.LifecycleState.Active);
	}

	/**
	 * Get a list of the secrets in the specified compartment, if any of the vault /
	 * name / lifecycle state are specified then the returned list if filtered
	 * against those. The result is ordered by the secret name
	 * 
	 * @param compartmentOcid
	 * @param vaultOcid
	 * @param name
	 * @param lifecycleState
	 * @return
	 */
	public List<Secret> listSecrets(@NonNull String compartmentOcid, String vaultOcid, String name,
			Secret.LifecycleState lifecycleState) {
		SecretSummary.LifecycleState secretSummaryLifecycleState = lifecycleState == null ? null
				: SecretSummary.LifecycleState.create(lifecycleState.getValue());
		List<SecretSummary> secretSummaries = listSecretSummaries(compartmentOcid, vaultOcid, name,
				secretSummaryLifecycleState);
		return secretSummaries.stream().map(secretSummary -> getSecret(secretSummary)).toList();
	}

	/**
	 * gets the first secret in the provided vault with a matching name. Note that
	 * the secret must be in the same compartment as the vault
	 * 
	 * @param vaultSummary
	 * @param name
	 * @return
	 */
	public Secret getSecretByName(@NonNull VaultSummary vaultSummary, @NonNull String name) {
		return getSecretByName(vaultSummary.getCompartmentId(), vaultSummary.getId(), name);
	}

	/**
	 * gets the first secret in the provided vault with a matching name. Note that
	 * the secret must be in the same compartment as the vault
	 * 
	 * @param vault
	 * @param name
	 * @return
	 */
	public Secret getSecretByName(@NonNull Vault vault, @NonNull String name) {
		return getSecretByName(vault.getCompartmentId(), vault.getId(), name);
	}

	/**
	 * gets the **first** (as defined by name order ascending) active secret in the
	 * compartment with the given name, if vaultOcid is null allows for any vault
	 * 
	 * @param compartmentOcid
	 * @param vaultOcid
	 * @param name
	 * @return
	 */
	public Secret getSecretByName(@NonNull String compartmentOcid, String vaultOcid, @NonNull String name) {
		List<SecretSummary> secretSummaries = listSecretSummaries(compartmentOcid, vaultOcid, name,
				SecretSummary.LifecycleState.Active);
		if (secretSummaries.isEmpty()) {
			return null;
		}
		return getSecret(secretSummaries.getFirst());
	}

	/**
	 * gets the secret from the summary
	 * 
	 * @param secretSummary
	 * @return
	 */
	public Secret getSecret(@NonNull SecretSummary secretSummary) {
		return getSecret(secretSummary.getId());
	}

	/**
	 * gets the secret by it's OCID
	 * 
	 * @param secretOcid
	 * @return
	 */
	public Secret getSecret(@NonNull String secretOcid) {
		GetSecretRequest request = GetSecretRequest.builder().secretId(secretOcid).build();
		GetSecretResponse response = vaultClient.getSecret(request);
		return response.getSecret();
	}

	/**
	 * create a secret with an initial version containing bytes if
	 * generationTemplate is null this will use the Bytes512 template
	 * 
	 * @param compartmentOcid
	 * @param name
	 * @param encryptionKeyOcid
	 * @param vaultOcid
	 * @param length
	 * @param secretDescription
	 * @param generationTemplate
	 * @return
	 */
	public Secret createSecretGeneratedBytes(@NonNull String compartmentOcid, @NonNull String name,
			@NonNull String encryptionKeyOcid, @NonNull String vaultOcid, String secretDescription,
			BytesGenerationContext.GenerationTemplate generationTemplate) {

		SecretGenerationContext generationContext = BytesGenerationContext.builder().generationTemplate(
				generationTemplate == null ? BytesGenerationContext.GenerationTemplate.Bytes512 : generationTemplate)
				.build();
		return createSecretGenerated(compartmentOcid, name, encryptionKeyOcid, vaultOcid, generationContext,
				secretDescription);
	}

	/**
	 * create a secret with an initial version containing a ssh key, if the
	 * generationTemplate is null use the Rsa3072 template
	 * 
	 * @param compartmentOcid
	 * @param name
	 * @param encryptionKeyOcid
	 * @param vaultOcid
	 * @param length
	 * @param secretDescription
	 * @param generationTemplate
	 * @return
	 */
	public Secret createSecretGeneratedSSHKey(@NonNull String compartmentOcid, @NonNull String name,
			@NonNull String encryptionKeyOcid, @NonNull String vaultOcid, String secretDescription,
			SshKeyGenerationContext.GenerationTemplate generationTemplate) {
		SecretGenerationContext generationContext = SshKeyGenerationContext.builder().generationTemplate(
				generationTemplate == null ? SshKeyGenerationContext.GenerationTemplate.Rsa3072 : generationTemplate)
				.build();
		return createSecretGenerated(compartmentOcid, name, encryptionKeyOcid, vaultOcid, generationContext,
				secretDescription);
	}

	/**
	 * create a secret with an initial version containing a random password using
	 * the passprase of the specified length. if generationTemplate is null will use
	 * the SecretsDefaultPassword template
	 * 
	 * @param compartmentOcid
	 * @param name
	 * @param encryptionKeyOcid
	 * @param vaultOcid
	 * @param length
	 * @param secretDescription
	 * @return
	 */
	public Secret createSecretGeneratedPassphrase(@NonNull String compartmentOcid, @NonNull String name,
			@NonNull String encryptionKeyOcid, @NonNull String vaultOcid, @NonNull Integer length,
			String secretDescription, PassphraseGenerationContext.GenerationTemplate generationTemplate) {
		SecretGenerationContext generationContext = PassphraseGenerationContext.builder()
				.generationTemplate(generationTemplate == null
						? PassphraseGenerationContext.GenerationTemplate.SecretsDefaultPassword
						: generationTemplate)
				.passphraseLength(length).build();
		return createSecretGenerated(compartmentOcid, name, encryptionKeyOcid, vaultOcid, generationContext,
				secretDescription);
	}

	private Secret createSecretGenerated(@NonNull String compartmentOcid, @NonNull String name,
			@NonNull String encryptionKeyOcid, @NonNull String vaultOcid,
			@NonNull SecretGenerationContext generationContext, String secretDescription) {
		CreateSecretDetails.Builder createSecretDetailsBuilder = CreateSecretDetails.builder()
				.compartmentId(compartmentOcid).secretName(name).vaultId(vaultOcid).keyId(encryptionKeyOcid)
				.enableAutoGeneration(true).secretGenerationContext(generationContext);
		if (secretDescription != null) {
			createSecretDetailsBuilder.description(secretDescription);
		}
		CreateSecretDetails createSecretDetails = createSecretDetailsBuilder.build();
		CreateSecretRequest request = CreateSecretRequest.builder().createSecretDetails(createSecretDetails).build();
		CreateSecretResponse response = vaultClient.createSecret(request);
		return response.getSecret();
	}

	/**
	 * 
	 * create a secret with an initial version containing the contents supplied by
	 * the caller.
	 * 
	 * @param compartmentOcid
	 * @param name
	 * @param encryptionKeyOcid
	 * @param vaultOcid
	 * @param secretContents
	 * @param secretContentsVersionName if null will not be set
	 * @param secretDescription         if null will not be set
	 * @return
	 */
	public Secret createSecretManual(@NonNull String compartmentOcid, @NonNull String name,
			@NonNull String encryptionKeyOcid, @NonNull String vaultOcid, @NonNull String secretContents,
			String secretContentsVersionName, String secretDescription) {
		CreateSecretDetails.Builder createSecretDetailsBuilder = CreateSecretDetails.builder()
				.compartmentId(compartmentOcid).secretName(name).vaultId(vaultOcid).keyId(encryptionKeyOcid)
				.enableAutoGeneration(false);
		if (secretDescription != null) {
			createSecretDetailsBuilder.description(secretDescription);
		}
		String base64SecretContents = Base64.getEncoder().encodeToString(secretContents.getBytes());
		Base64SecretContentDetails.Builder base64SecretContentDetailsBuilder = Base64SecretContentDetails.builder()
				.content(base64SecretContents);
		if (secretContentsVersionName != null) {
			base64SecretContentDetailsBuilder.name(secretContentsVersionName);
		}
		Base64SecretContentDetails base64SecretContentDetails = base64SecretContentDetailsBuilder.build();
		createSecretDetailsBuilder.secretContent(base64SecretContentDetails);
		CreateSecretDetails createSecretDetails = createSecretDetailsBuilder.build();
		CreateSecretRequest request = CreateSecretRequest.builder().createSecretDetails(createSecretDetails).build();
		CreateSecretResponse response = vaultClient.createSecret(request);
		return response.getSecret();
	}

	/**
	 * waits for the secret to become active, return null if there is no secret or
	 * an interruption, return true if it becomes active in the time window, or
	 * false if not. checkes every 10 seconds for upto 120 seconds
	 * 
	 * @param secret
	 * @return
	 */
	public Boolean waitForSecretToBecomeActive(@NonNull Secret secret) {
		return waitForSecretToBecomeActive(secret, 10, 120);
	}

	/**
	 * waits for the secret to become active, return null if there is no secret or
	 * an interruption, return true if it becomes active in the time window, or
	 * false if not. checkes every 10 seconds for upto 120 seconds
	 * 
	 * @param secret
	 * @return
	 */
	public Boolean waitForSecretToBecomeActive(@NonNull Secret secret, int secondsBetweenChecks, int secondsToWait) {
		Secret secretToTest = secret;
		int remainingSeconds = secondsToWait;
		while ((remainingSeconds > 0) && (!secretToTest.getLifecycleState().equals(Secret.LifecycleState.Active))) {
			remainingSeconds -= secondsBetweenChecks;
			try {
				Thread.sleep(Duration.ofSeconds(secondsBetweenChecks));
			} catch (InterruptedException e) {
				return null;
			}
			secretToTest = getSecret(secret.getId());
		}
		return secretToTest.getLifecycleState().equals(Secret.LifecycleState.Active);
	}

	/**
	 * waits for the secret to become active, return null if there is no secret or
	 * an interruption, return true if it becomes active in the time window, or
	 * false if not
	 * 
	 * @param secret
	 * @param secondsBetweenChecks
	 * @param secondsToWait
	 * @return
	 */
	public Boolean waitForSecretToBecomeActive(@NonNull String secretOcid, int secondsBetweenChecks,
			int secondsToWait) {
		Secret secretToTest = getSecret(secretOcid);
		// if it's not there then give up
		if (secretToTest == null) {
			return null;
		}
		return waitForSecretToBecomeActive(secretToTest, secondsBetweenChecks, secondsToWait);
	}

	/**
	 * waits for the secret to become active, return null if there is no secret or
	 * an interruption, return true if it becomes active in the time window, or
	 * false if not
	 * 
	 * @param secretOcid
	 * @param secondsBetweenChecks
	 * @param secondsToWait
	 * @return
	 */
	public Boolean waitForSecretToBecomeActive(@NonNull String secretOcid) {
		return waitForSecretToBecomeActive(secretOcid, 10, 120);
	}

	public boolean scheduleSecretDeletion(@NonNull SecretSummary secretSummary, @NonNull ZonedDateTime deletionDTG) {
		return scheduleSecretDeletion(secretSummary.getId(), Date.from(deletionDTG.toInstant()));
	}

	public boolean scheduleSecretDeletion(@NonNull Secret secret, @NonNull ZonedDateTime deletionDTG) {
		return scheduleSecretDeletion(secret.getId(), Date.from(deletionDTG.toInstant()));
	}

	public boolean scheduleSecretDeletion(@NonNull String secretOcid, @NonNull ZonedDateTime deletionDTG) {
		return scheduleSecretDeletion(secretOcid, Date.from(deletionDTG.toInstant()));
	}

	public boolean scheduleSecretDeletion(@NonNull SecretSummary secretSummary, @NonNull Date deletionDTG) {
		return scheduleSecretDeletion(secretSummary.getId(), deletionDTG);
	}

	public boolean scheduleSecretDeletion(@NonNull Secret secret, @NonNull Date deletionDTG) {
		return scheduleSecretDeletion(secret.getId(), deletionDTG);
	}

	public boolean scheduleSecretDeletion(@NonNull String secretOcid, @NonNull Date deletionDTG) {
		ScheduleSecretDeletionDetails details = ScheduleSecretDeletionDetails.builder().timeOfDeletion(deletionDTG)
				.build();
		ScheduleSecretDeletionRequest request = ScheduleSecretDeletionRequest.builder().secretId(secretOcid)
				.scheduleSecretDeletionDetails(details).build();
		ScheduleSecretDeletionResponse response = vaultClient.scheduleSecretDeletion(request);
		return response.get__httpStatusCode__() == HttpStatus.SC_OK;
	}

	public boolean cancelSecretDeletion(@NonNull SecretSummary secretSummary) {
		return cancelSecretDeletion(secretSummary.getId());
	}

	public boolean cancelSecretDeletion(@NonNull Secret secret) {
		return cancelSecretDeletion(secret.getId());
	}

	public boolean cancelSecretDeletion(@NonNull String secretOcid) {
		CancelSecretDeletionRequest request = CancelSecretDeletionRequest.builder().secretId(secretOcid).build();
		CancelSecretDeletionResponse response = vaultClient.cancelSecretDeletion(request);
		return response.get__httpStatusCode__() == HttpStatus.SC_OK;
	}

	/**
	 * lists all pending SecretVersionSummary for the provided secret
	 * 
	 * @param secret
	 * @return
	 */
	public List<SecretVersionSummary> listSecretVersionSummariesPending(@NonNull Secret secret) {
		return listSecretVersionSummaries(secret, SecretVersionSummary.Stages.Pending);
	}

	/**
	 * lists all pending SecretVersion for the provided secret
	 * 
	 * @param secret
	 * @return
	 */
	public List<SecretVersion> listSecretVersionsPending(@NonNull Secret secret) {
		return listSecretVersions(secret, SecretVersion.Stages.Pending);
	}

	/**
	 * lists all deleted SecretVersionSummary for the provided secret
	 * 
	 * @param secret
	 * @return
	 */
	public List<SecretVersionSummary> listSecretVersionSummariesDeleted(@NonNull Secret secret) {
		return listSecretVersionSummaries(secret, SecretVersionSummary.Stages.Deprecated);
	}

	/**
	 * lists all deleted SecretVersionSummary for the provided secret
	 * 
	 * @param secret
	 * @return
	 */
	public List<SecretVersion> listSecretVersionsDeleted(@NonNull Secret secret) {
		return listSecretVersions(secret, SecretVersion.Stages.Deprecated);
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * current or null if there are no current versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersion getSecretVersionCurrent(@NonNull SecretSummary secretSummary) {
		return getSecretVersionCurrent(secretSummary.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * current or null if there are no current versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersion getSecretVersionCurrent(@NonNull Secret secret) {
		return getSecretVersionCurrent(secret.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * current or null if there are no current versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersion getSecretVersionCurrent(@NonNull String secretOcid) {
		SecretVersionSummary secretVersionSummary = getSecretVersionSummaryCurrent(secretOcid);
		if (secretVersionSummary == null) {
			return null;
		}
		return getSecretVersion(secretVersionSummary);
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * current or null if there are no current versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersionSummary getSecretVersionSummaryCurrent(@NonNull SecretSummary secretSummary) {
		return getSecretVersionSummaryCurrent(secretSummary.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * current or null if there are no current versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersionSummary getSecretVersionSummaryCurrent(@NonNull Secret secret) {
		return getSecretVersionSummaryCurrent(secret.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * current or null if there are no current versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersionSummary getSecretVersionSummaryCurrent(@NonNull String secretOcid) {
		List<SecretVersionSummary> secretVersionSummaries = listSecretVersionSummaries(secretOcid,
				SecretVersionSummary.Stages.Current);
		if (secretVersionSummaries.isEmpty()) {
			return null;
		}
		return secretVersionSummaries.getFirst();
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * latest or null if there are no versions
	 * 
	 * @param secretSummary
	 * @return
	 */
	public SecretVersionSummary getSecretVersionSummaryLatest(@NonNull SecretSummary secretSummary) {
		return getSecretVersionSummaryLatest(secretSummary.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * latest or null if there are no versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersionSummary getSecretVersionSummaryLatest(@NonNull Secret secret) {
		return getSecretVersionSummaryLatest(secret.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * latest or null if there are no versions
	 * 
	 * @param secretOcid
	 * @return
	 */
	public SecretVersionSummary getSecretVersionSummaryLatest(@NonNull String secretOcid) {
		List<SecretVersionSummary> secretVersionSummaries = listSecretVersionSummaries(secretOcid,
				SecretVersionSummary.Stages.Latest);
		if (secretVersionSummaries.isEmpty()) {
			return null;
		}
		return secretVersionSummaries.getFirst();
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * latest or null if there are no versions
	 * 
	 * @param secretSummary
	 * @return
	 */
	public SecretVersion getSecretVersionLatest(@NonNull SecretSummary secretSummary) {
		return getSecretVersionLatest(secretSummary.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * latest or null if there are no versions
	 * 
	 * @param secret
	 * @return
	 */
	public SecretVersion getSecretVersionLatest(@NonNull Secret secret) {
		return getSecretVersionLatest(secret.getId());
	}

	/**
	 * Gets the **first** (most recent) secret version summary which is in the state
	 * latest or null if there are no versions
	 * 
	 * @param secretOcid
	 * @return
	 */
	public SecretVersion getSecretVersionLatest(@NonNull String secretOcid) {
		List<SecretVersionSummary> secretVersionSummaries = listSecretVersionSummaries(secretOcid,
				SecretVersionSummary.Stages.Latest);
		if (secretVersionSummaries.isEmpty()) {
			return null;
		}
		return getSecretVersion(secretVersionSummaries.getFirst());
	}

	/**
	 * returns a list of SecretVersionSummary for the secret. if lifecycleStage is
	 * not null limits the list to only versions where the version has a stage that
	 * matches the lifecycleStage. E.g. if the version has stages CURRENT and LATEST
	 * then both of those will have to be set, but just one or a different stage
	 * such as PENDING will not
	 * 
	 * @param secretOcid
	 * @param lifecycleStage
	 * @return
	 */
	public List<SecretVersionSummary> listSecretVersionSummaries(@NonNull Secret secret,
			SecretVersionSummary.Stages... lifecycleStages) {
		return listSecretVersionSummaries(secret.getId(), lifecycleStages);
	}

	/**
	 * returns a list of SecretVersionSummary for the secret. if lifecycleStage is
	 * not null limits the list to only versions where the version has a stage that
	 * matches the lifecycleStage. E.g. if the version has stages CURRENT and LATEST
	 * then both of those will have to be set, but just one or a different stage
	 * such as PENDING will not
	 * 
	 * @param secretOcid
	 * @param lifecycleStage
	 * @return
	 */
	public List<SecretVersionSummary> listSecretVersionSummaries(@NonNull SecretSummary secretSummary,
			SecretVersionSummary.Stages... lifecycleStages) {
		return listSecretVersionSummaries(secretSummary.getId(), lifecycleStages);
	}

	/**
	 * returns a list of SecretVersion for the secret. if lifecycleStage is not null
	 * limits the list to only versions where the version has a stage that matches
	 * all of the lifecycleStage. E.g. if the version has stages CURRENT and LATEST
	 * then both of those will have to be set, but just one or a different stage
	 * such as PENDING will not
	 * 
	 * @param secretOcid
	 * @param lifecycleStage
	 * @return
	 */
	public List<SecretVersion> listSecretVersions(@NonNull Secret secret, SecretVersion.Stages... lifecycleStages) {
		return listSecretVersions(secret.getId(), lifecycleStages);
	}

	/**
	 * returns a list of SecretVersion for the secret. if lifecycleStage is not null
	 * limits the list to only versions where the version has a stage that matches
	 * all of the lifecycleStage. E.g. if the version has stages CURRENT and LATEST
	 * then both of those will have to be set, but just one or a different stage
	 * such as PENDING will not
	 * 
	 * @param secretOcid
	 * @param lifecycleStage
	 * @return
	 */
	public List<SecretVersion> listSecretVersions(@NonNull String secretOcid, SecretVersion.Stages... lifecycleStages) {
		SecretVersionSummary.Stages secretVersionLifecycleStages[];
		if (lifecycleStages == null) {
			secretVersionLifecycleStages = null;
		} else {
			secretVersionLifecycleStages = new SecretVersionSummary.Stages[lifecycleStages.length];
			for (int i = 0; i < lifecycleStages.length; i++) {
				secretVersionLifecycleStages[i] = SecretVersionSummary.Stages.create(lifecycleStages[i].getValue());
			}
		}
		List<SecretVersionSummary> secretVersionSummaries = listSecretVersionSummaries(secretOcid,
				secretVersionLifecycleStages);
		return secretVersionSummaries.stream()
				.map(secretVersionSummary -> getSecretVersion(secretVersionSummary.getSecretId(),
						secretVersionSummary.getVersionNumber()))
				.toList();
	}

	/**
	 * returns a list of SecretVersionSummary for the secret. if lifecycleStage is
	 * not null limits the list to only versions where the version has a stage that
	 * matches all of the lifecycleStage. E.g. if the lifecycleStages has stages
	 * CURRENT and LATEST then both of those will have to be set (any othewrs may or
	 * may not be present), but just one or a different stage such as PENDING will
	 * not, if lifecycleStages has only one value, say DELETED then versions with
	 * that will be returned even if they have other stage values as well.
	 * 
	 * @param secretOcid
	 * @param lifecycleStage
	 * @return
	 */
	public List<SecretVersionSummary> listSecretVersionSummaries(@NonNull String secretOcid,
			SecretVersionSummary.Stages... lifecycleStages) {
		ListSecretVersionsRequest request = ListSecretVersionsRequest.builder().secretId(secretOcid)
				.sortBy(ListSecretVersionsRequest.SortBy.VersionNumber)
				.sortOrder(ListSecretVersionsRequest.SortOrder.Desc).build();
		List<SecretVersionSummary.Stages> stages = ((lifecycleStages == null) || (lifecycleStages.length == 0)) ? null
				: Arrays.asList(lifecycleStages);
		Iterable<SecretVersionSummary> secretVersionSummaries = vaultClient.getPaginators()
				.listSecretVersionsRecordIterator(request);
		return StreamSupport.stream(secretVersionSummaries.spliterator(), false).filter((secretVersionSummary) -> {
			return stages == null ? true : secretVersionSummary.getStages().containsAll(stages);
		}).toList();
	}

	/**
	 * gets the secret version
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @return
	 */
	public SecretVersion getSecretVersion(@NonNull SecretVersionSummary secretVersionSummary) {
		return getSecretVersion(secretVersionSummary.getSecretId(), secretVersionSummary.getVersionNumber());
	}

	/**
	 * gets the secret version
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @return
	 */
	public SecretVersion getSecretVersion(@NonNull Secret secret, @NonNull Long secretVersionNumber) {
		return getSecretVersion(secret.getId(), secretVersionNumber);
	}

	/**
	 * gets the secret version
	 * 
	 * @param secretSummary
	 * @param secretVersionNumber
	 * @return
	 */
	public SecretVersion getSecretVersion(@NonNull SecretSummary secretSummary, @NonNull Long secretVersionNumber) {
		return getSecretVersion(secretSummary.getId(), secretVersionNumber);
	}

	/**
	 * gets the secret version
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @return
	 */
	public SecretVersion getSecretVersion(@NonNull String secretOcid, @NonNull Long secretVersionNumber) {
		GetSecretVersionRequest request = GetSecretVersionRequest.builder().secretId(secretOcid)
				.secretVersionNumber(secretVersionNumber).build();
		GetSecretVersionResponse response = vaultClient.getSecretVersion(request);
		return response.getSecretVersion();
	}

	/**
	 * gets the contents of the CURRENT version of the secret
	 * 
	 * @param secret
	 * @return
	 */
	public String getSecretContents(@NonNull Secret secret) {
		return getSecretContentsReal(secret.getId(), null, null, null);
	}

	/**
	 * gets the contents of the CURRENT version of the secret
	 * 
	 * @param secretSummary
	 * @return
	 */
	public String getSecretContents(@NonNull SecretSummary secretSummary) {
		return getSecretContentsReal(secretSummary.getId(), null, null, null);
	}

	/**
	 * gets the contents of the CURRENT version of the secret
	 * 
	 * @param secretOcid
	 * @return
	 */
	public String getSecretContents(@NonNull String secretOcid) {
		return getSecretContentsReal(secretOcid, null, null, null);
	}

	/**
	 * gets the contents of the specified version of the secret
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull String secretOcid, @NonNull Long secretVersionNumber) {
		return getSecretContentsReal(secretOcid, secretVersionNumber, null, null);
	}

	/**
	 * gets the contents of the specified version of the secret
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull SecretSummary secret, @NonNull Long secretVersionNumber) {
		return getSecretContentsReal(secret.getId(), secretVersionNumber, null, null);
	}

	/**
	 * gets the contents of the specified version of the secret
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull Secret secret, @NonNull Long secretVersionNumber) {
		return getSecretContentsReal(secret.getId(), secretVersionNumber, null, null);
	}

	/**
	 * gets the contents of the specified version of the secret
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull String secretOcid, @NonNull String secretVersionName) {
		return getSecretContentsReal(secretOcid, null, secretVersionName, null);
	}

	/**
	 * gets the contents of the specified version of the secret
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull SecretSummary secret, @NonNull String secretVersionName) {
		return getSecretContentsReal(secret.getId(), null, secretVersionName, null);
	}

	/**
	 * gets the contents of the specified version of the secret
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull Secret secret, @NonNull String secretVersionName) {
		return getSecretContentsReal(secret.getId(), null, secretVersionName, null);
	}

	/**
	 * gets the contents of the version of the secret in the specified stage
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @return
	 */
	public String getSecretContents(@NonNull String secretOcid, @NonNull GetSecretBundleRequest.Stage stage) {
		return getSecretContentsReal(secretOcid, null, null, stage);
	}

	/**
	 * gets the contents of the version of the secret
	 * 
	 * @param secretVersion
	 * @return
	 */
	public String getSecretContents(@NonNull SecretVersion secretVersion) {
		return getSecretContentsReal(secretVersion.getSecretId(), secretVersion.getVersionNumber(), null, null);
	}

	public String getSecretContents(@NonNull String secretOcid, Long secretVersionNumber, String secretVersionName,
			SecretVersion.Stages stage) {

		GetSecretBundleRequest.Stage requestStage = stage == null ? null
				: GetSecretBundleRequest.Stage.create(stage.getValue());
		return getSecretContentsReal(secretOcid, secretVersionNumber, secretVersionName, requestStage);
	}

	/**
	 * Gets the secret contents. If all of secretVersionNumber, secretVersionName
	 * and stage are null the CURRENT version is returned. If one is set then the
	 * results will filter based on that. Only one of them should be set
	 * (unpredictable behaviour otherwise)
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @param secretVersionName
	 * @param stage
	 * @return null if there isn't a match (e.g. secretVersionNumber is set but
	 *         there is no version with that number)
	 */
	private String getSecretContentsReal(@NonNull String secretOcid, Long secretVersionNumber, String secretVersionName,
			GetSecretBundleRequest.Stage stage) {
		GetSecretBundleRequest.Builder getSecretBundleRequestBuilder = GetSecretBundleRequest.builder()
				.secretId(secretOcid);
		if (secretVersionNumber != null) {
			getSecretBundleRequestBuilder.versionNumber(secretVersionNumber);
		}
		if (secretVersionName != null) {
			getSecretBundleRequestBuilder.secretVersionName(secretVersionName);
		}
		if (stage != null) {
			getSecretBundleRequestBuilder.stage(stage);
		}

		GetSecretBundleRequest getSecretBundleRequest = getSecretBundleRequestBuilder.build();
		GetSecretBundleResponse response = secretsClient.getSecretBundle(getSecretBundleRequest);
		if (response.getSecretBundle() == null) {
			return null;
		}
		Base64SecretBundleContentDetails contentDetails = (Base64SecretBundleContentDetails) response.getSecretBundle()
				.getSecretBundleContent();

		String base64Content = contentDetails.getContent();
		byte[] decodedBytes = Base64.getDecoder().decode(base64Content);
		return new String(decodedBytes);
	}

	/**
	 * create a new version of the generated secret using the template specified
	 * when the origional was created and set the new version to be the current
	 * version, the description can be null if desired
	 * 
	 * @param secretOcid
	 * @param secretDescription
	 * @return
	 */
	public Secret createSecretVersionGeneratedCurrent(@NonNull String secretOcid, String secretDescription) {
		Secret secret = getSecret(secretOcid);
		return createSecretVersionGeneratedCurrent(secret, secretDescription);
	}

	/**
	 * create a new version of the generated secret using the template specified
	 * when the origional was created and set the new version to be the current
	 * version, the description can be null if desired
	 * 
	 * @param secret
	 * @param secretDescription
	 * @return
	 */
	public Secret createSecretVersionGeneratedCurrent(@NonNull Secret secret, String secretDescription) {
		return createSecretVersionGenerated(secret, Base64SecretContentDetails.Stage.Current, secretDescription);
	}

	/**
	 * create a new version of the genrated secret, not tat at the moment there
	 * seems to be no way to set the stage gor generated secrets, this private to
	 * let me later change the code without breaking callers.
	 * 
	 * @param secret
	 * @param stage
	 * @param secretDescription
	 * @return
	 */
	private Secret createSecretVersionGenerated(@NonNull Secret secret, @NonNull Base64SecretContentDetails.Stage stage,
			String secretDescription) {
		Base64SecretContentDetails base64SecretContentDetails = Base64SecretContentDetails.builder()
				.stage(Base64SecretContentDetails.Stage.Current).build();
		UpdateSecretDetails.Builder updateSecretDetailsBuilder = UpdateSecretDetails.builder()
				.secretContent(base64SecretContentDetails).enableAutoGeneration(true)
				.secretGenerationContext(secret.getSecretGenerationContext());
		if (secretDescription != null) {
			updateSecretDetailsBuilder.description(secretDescription);
		}
		UpdateSecretDetails updateSecretDetails = updateSecretDetailsBuilder.build();
		UpdateSecretRequest request = UpdateSecretRequest.builder().secretId(secret.getId())
				.updateSecretDetails(updateSecretDetails).build();
		UpdateSecretResponse response = vaultClient.updateSecret(request);
		return response.getSecret();
	}

	/**
	 * Create a new version of the secret with the provided contents and make it the
	 * current version, the name and description can be null which means they won't
	 * be set in the new version
	 * 
	 * @param secretOcid
	 * @param secretContents
	 * @param secretContentsVersionName
	 * @param secretDescription
	 * @return
	 */
	public Secret createSecretVersionManualAsCurrent(@NonNull String secretOcid, @NonNull String secretContents,
			String secretContentsVersionName, String secretDescription) {
		return createSecretVersionManual(secretOcid, secretContents, Base64SecretContentDetails.Stage.Current,
				secretContentsVersionName, secretDescription);
	}

	/**
	 * 
	 * Create a new version of the secret with the provided contents and make it
	 * pending, the name and description can be null which means they won't be set
	 * in the new version
	 * 
	 * @param secretOcid
	 * @param secretContents
	 * @param secretContentsVersionName
	 * @param secretDescription
	 * @return
	 */
	public Secret createSecretVersionManualAsPending(@NonNull String secretOcid, @NonNull String secretContents,
			String secretContentsVersionName, String secretDescription) {
		return createSecretVersionManual(secretOcid, secretContents, Base64SecretContentDetails.Stage.Pending,
				secretContentsVersionName, secretDescription);
	}

	/**
	 * 
	 * Create a new version of the secret with the provided contents and set it to
	 * the provided stage, the name and description can be null which means they
	 * won't be set in the new version
	 * 
	 * @param secretOcid
	 * @param secretContents
	 * @param stage
	 * @param secretContentsVersionName
	 * @param secretDescription
	 * @return
	 */
	public Secret createSecretVersionManual(@NonNull String secretOcid, @NonNull String secretContents,
			Base64SecretContentDetails.Stage stage, String secretContentsVersionName, String secretDescription) {
		UpdateSecretDetails.Builder updateSecretDetailsBuilder = UpdateSecretDetails.builder()
				.enableAutoGeneration(false);
		if (secretDescription != null) {
			updateSecretDetailsBuilder.description(secretDescription);
		}
		String base64SecretContents = Base64.getEncoder().encodeToString(secretContents.getBytes());
		Base64SecretContentDetails.Builder base64SecretContentDetailsBuilder = Base64SecretContentDetails.builder()
				.content(base64SecretContents);
		if (secretContentsVersionName != null) {
			base64SecretContentDetailsBuilder.name(secretContentsVersionName);
		}
		if (stage != null) {
			base64SecretContentDetailsBuilder.stage(stage);
		}
		Base64SecretContentDetails base64SecretContentDetails = base64SecretContentDetailsBuilder.build();
		updateSecretDetailsBuilder.secretContent(base64SecretContentDetails);
		UpdateSecretDetails updateSecretDetails = updateSecretDetailsBuilder.build();
		UpdateSecretRequest request = UpdateSecretRequest.builder().secretId(secretOcid)
				.updateSecretDetails(updateSecretDetails).build();
		UpdateSecretResponse response = vaultClient.updateSecret(request);
		return response.getSecret();
	}

	/**
	 * update the secret version to current
	 * 
	 * @param secretVersion
	 * @return
	 */
	public Secret updateSecretVersionPendingToCurrent(@NonNull SecretVersion secretVersion) {
		return updateSecretVersionPendingToCurrent(secretVersion.getSecretId(), secretVersion.getVersionNumber());
	}

	/**
	 * update the secret version to current
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @return
	 */
	public Secret updateSecretVersionPendingToCurrent(@NonNull String secretOcid, @NonNull Long secretVersionNumber) {
		UpdateSecretDetails updateSecretDetails = UpdateSecretDetails.builder()
				.currentVersionNumber(secretVersionNumber).build();
		UpdateSecretRequest request = UpdateSecretRequest.builder().secretId(secretOcid)
				.updateSecretDetails(updateSecretDetails).build();
		UpdateSecretResponse response = vaultClient.updateSecret(request);
		return response.getSecret();
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secretSummary
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull SecretSummary secretSummary,
			@NonNull Long secretVersionNumber, @NonNull LocalDateTime deletionDTG) {
		return scheduleSecretVersionDeletion(secretSummary.getId(), secretVersionNumber,
				ZonedDateTime.of(deletionDTG, ZoneId.systemDefault()));
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull Secret secret, @NonNull Long secretVersionNumber,
			@NonNull LocalDateTime deletionDTG) {
		return scheduleSecretVersionDeletion(secret.getId(), secretVersionNumber,
				ZonedDateTime.of(deletionDTG, ZoneId.systemDefault()));
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull String secretOcid, @NonNull Long secretVersionNumber,
			@NonNull LocalDateTime deletionDTG) {
		return scheduleSecretVersionDeletion(secretOcid, secretVersionNumber,
				ZonedDateTime.of(deletionDTG, ZoneId.systemDefault()));
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secretSummary
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull SecretSummary secretSummary,
			@NonNull Long secretVersionNumber, @NonNull ZonedDateTime deletionDTG) {
		return scheduleSecretVersionDeletion(secretSummary.getId(), secretVersionNumber,
				Date.from(deletionDTG.toInstant()));
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull Secret secret, @NonNull Long secretVersionNumber,
			@NonNull ZonedDateTime deletionDTG) {
		return scheduleSecretVersionDeletion(secret.getId(), secretVersionNumber, Date.from(deletionDTG.toInstant()));
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secretOcid
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull String secretOcid, @NonNull Long secretVersionNumber,
			@NonNull ZonedDateTime deletionDTG) {
		return scheduleSecretVersionDeletion(secretOcid, secretVersionNumber, Date.from(deletionDTG.toInstant()));
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secretSummary
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull SecretSummary secretSummary,
			@NonNull Long secretVersionNumber, @NonNull Date deletionDTG) {
		return scheduleSecretVersionDeletion(secretSummary.getId(), secretVersionNumber, deletionDTG);
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secret
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull Secret secret, @NonNull Long secretVersionNumber,
			@NonNull Date deletionDTG) {
		return scheduleSecretVersionDeletion(secret.getId(), secretVersionNumber, deletionDTG);
	}

	/**
	 * schedule the deletion of the secret version, this will fail if it's the
	 * current version
	 * 
	 * @param secretVersionOcid
	 * @param secretVersionNumber
	 * @param deletionDTG
	 * @return
	 */
	public boolean scheduleSecretVersionDeletion(@NonNull String secretVersionOcid, @NonNull Long secretVersionNumber,
			@NonNull Date deletionDTG) {
		ScheduleSecretVersionDeletionDetails details = ScheduleSecretVersionDeletionDetails.builder()
				.timeOfDeletion(deletionDTG).build();
		ScheduleSecretVersionDeletionRequest request = ScheduleSecretVersionDeletionRequest.builder()
				.secretId(secretVersionOcid).secretVersionNumber(secretVersionNumber)
				.scheduleSecretVersionDeletionDetails(details).build();
		ScheduleSecretVersionDeletionResponse response = vaultClient.scheduleSecretVersionDeletion(request);
		return response.get__httpStatusCode__() == HttpStatus.SC_OK;
	}

	/**
	 * cancel the scheduled deletion of the secret version
	 * 
	 * @param secretVersionSummary
	 * @param secretVersionNumber
	 * @return
	 */
	public boolean cancelSecretVersionDeletion(@NonNull SecretVersionSummary secretVersionSummary,
			@NonNull Long secretVersionNumber) {
		return cancelSecretVersionDeletion(secretVersionSummary.getSecretId(), secretVersionNumber);
	}

	/**
	 * cancel the scheduled deletion of the secret version
	 * 
	 * @param secretVersion
	 * @param secretVersionNumber
	 * @return
	 */
	public boolean cancelSecretVersionDeletion(@NonNull SecretVersion secretVersion,
			@NonNull Long secretVersionNumber) {
		return cancelSecretVersionDeletion(secretVersion.getSecretId(), secretVersionNumber);
	}

	/**
	 * cancel the scheduled deletion of the secret version
	 * 
	 * @param secretVersionOcid
	 * @param secretVersionNumber
	 * @return
	 */
	public boolean cancelSecretVersionDeletion(@NonNull String secretVersionOcid, @NonNull Long secretVersionNumber) {
		CancelSecretVersionDeletionRequest request = CancelSecretVersionDeletionRequest.builder()
				.secretId(secretVersionOcid).secretVersionNumber(secretVersionNumber).build();
		CancelSecretVersionDeletionResponse response = vaultClient.cancelSecretVersionDeletion(request);
		return response.get__httpStatusCode__() == HttpStatus.SC_OK;
	}

}
